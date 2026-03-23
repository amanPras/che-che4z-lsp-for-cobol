import { Block, CCFGraph, Edge, CCFNode } from "./types";
import { StructureTracker } from "./structureTracker";
import { Program } from "../../model";

export class CFGGenerator {
    constructor(private structureTracker: StructureTracker) { }
    public generate(blocks: Block[]): CCFGraph {
        let edges: Edge[] = [];
        const labelToBlockIndex = this.buildSymbolTable(blocks);

        // draws standard sequential lines for fall-throughs/sequential (if nxtBlock >= 0).
        // also, sequential lines/edges for conditional falseBlock metadata
        // alos, draw edges for follow-along --> GO TO instructions
        // look ahead for the exit para/sec for a valid exit point and draw edges .. follow along and hard jump
        edges = this.generateBaseEdges(blocks, labelToBlockIndex);

        // retur-back 
        // finds perform(retur-back) blocks and identifies their start and end boundaries
        // clones all internal edges within those boundaries, add unique scope identifier
        // if exit edge points to a block greater than the endIndex => EXIT command jump out of perform scope
        //.     then, mark eaddges as perform-return, helps in tracking, ignore the edges when drawing
        // all remaing edges are scopeed boundaryScoped (perform edges)
        edges = this.expandPerformMacros(edges, blocks, labelToBlockIndex);

        // for alter
        // modify the eges target as per alter meta-data
        //if under condition, maintain the normal flow
        // this is diff than the current approach, which just points to the ultimate target
        edges = this.expandAlterMacros(edges, blocks, labelToBlockIndex.table);

        // breadth 1st search to strip unreachable edges from the program start
        // do not touch the peform scoped edges
        edges = this.pruneUnreachableGlobalEdges(edges);

        // depth first search
        // pass activeCaller ID and a scopeHash down the recursion stack. 
        //     ... Helps to not mix the path when called from diff para.
        // make sure subroutine scopes retrurs
        // if GOBACK or crash => sequential fall-through for caller.
        const finalEdges = this.cleanGraphEdges(edges, blocks);

        for (const block of blocks) block.incoming_flow = []; 
        for (const edge of finalEdges) blocks[edge.to].incoming_flow.push({ from: edge.from, type: edge.type });

        // revert the blocks => blocks in same namesppcaesa are merged
        return this.condenseToCCFGraph(blocks, finalEdges);
    }

    private generateBaseEdges(blocks: Block[], labelToBlockIndex: { table: Map<string, number>; idexToProgram: Map<number, string>; }) {
        let edges: Edge[] = [];
        for (let i = 0; i < blocks.length; i++) {
            const block = blocks[i];
            const isTerminal = block.isTerminal === true;
            let isHardJump = false;

            const falseBlock = (block.meta_data as any)?.falseBlock;
            if (falseBlock !== undefined && falseBlock < blocks.length) {
                edges.push({ from: i, to: falseBlock, type: "sequential" });
            }

            if (block.outgoing_flow?.type === "follow-along") {
                const targetNameRaw = block.outgoing_flow.name;
                const targetName = Array.isArray(targetNameRaw) ? targetNameRaw[0] : targetNameRaw;
                const targetIndex = labelToBlockIndex.table.get(targetName);
                if (targetIndex !== undefined) {
                    edges.push({ from: i, to: targetIndex, type: "follow-along" });
                    isHardJump = true; 
                }
            } else if (block.outgoing_flow?.type === "exit-section") {
                let targetIndex = blocks.length;
                for(let j = i + 1; j < blocks.length; j++) {
                    const node = blocks[j].instructions[0]?.instr.getInitialNode();
                    if (node?.type === "section") { targetIndex = j; break; }
                }
                if (targetIndex < blocks.length) edges.push({ from: i, to: targetIndex, type: "follow-along" });
                isHardJump = true;
            } else if (block.outgoing_flow?.type === "exit-paragraph") {
                let targetIndex = blocks.length;
                for(let j = i + 1; j < blocks.length; j++) {
                    const node = blocks[j].instructions[0]?.instr.getInitialNode();
                    if (node?.type === "paragraph" || node?.type === "section") { targetIndex = j; break; }
                }
                if (targetIndex < blocks.length) edges.push({ from: i, to: targetIndex, type: "follow-along" });
                isHardJump = true;
            }

            // FIX: Any nxtBlock >= 0 is a valid fall-through. -2 for the true cond 
            if (block.nxtBlock >= 0 && !isTerminal && !isHardJump) {
                edges.push({ from: i, to: block.nxtBlock, type: "sequential" });
            }
        }
        return edges;
    }

    private expandPerformMacros(baseEdges: Edge[], blocks: Block[], labelToBlockIndex: { table: Map<string, number>; idexToProgram: Map<number, string>; }): Edge[] {
        const finalEdges: Edge[] = [...baseEdges];

        for (let i = 0; i < blocks.length; i++) {
            const callerBlock = blocks[i];

            if (callerBlock.outgoing_flow?.type === "return-back") {
                const targetNameRaw = callerBlock.outgoing_flow.name;
                const targetName = Array.isArray(targetNameRaw) ? targetNameRaw[0] : targetNameRaw;
                const thruName = callerBlock.outgoing_flow.thru;

                if (!targetName) continue;

                const startIndex = labelToBlockIndex.table.get(targetName);
                let endIndex = startIndex;

                if (thruName) {
                    const resolvedThruIndex = this.getLastIndexForTarget(thruName, labelToBlockIndex.table, blocks, labelToBlockIndex.idexToProgram.get(i)!);
                    if (resolvedThruIndex !== undefined) endIndex = resolvedThruIndex;
                } else {
                    const resolvedEndIndex = this.getLastIndexForTarget(targetName, labelToBlockIndex.table, blocks, labelToBlockIndex.idexToProgram.get(i)!);
                    if (resolvedEndIndex !== undefined) endIndex = resolvedEndIndex;
                }

                if (startIndex !== undefined && endIndex !== undefined && startIndex <= endIndex) {
                    const boundaryScope = { callerId: i, endBlock: endIndex };

                    finalEdges.push({ from: i, to: startIndex, type: "return-back", scope: boundaryScope });

                    for (let j = startIndex; j <= endIndex; j++) {
                        const internalBaseEdges = baseEdges.filter((e) => e.from === j);
                        for (const baseEdge of internalBaseEdges) {
                            if (j === endIndex && baseEdge.type === "sequential") continue;

                            // Local exits that push control OUT of the PERFORM scope trigger a return!
                            const sourceBlock = blocks[baseEdge.from];
                            const isLocalExit = sourceBlock.outgoing_flow?.type === "exit-section" || sourceBlock.outgoing_flow?.type === "exit-paragraph";

                            if (isLocalExit && baseEdge.to > endIndex) {
                                if (callerBlock.nxtBlock !== -1) {
                                    const edgeExists = finalEdges.some(e => e.from === baseEdge.from && e.to === callerBlock.nxtBlock && e.type === 'perform-return' && e.scope?.callerId === boundaryScope.callerId);
                                    if (!edgeExists) {
                                        finalEdges.push({ from: baseEdge.from, to: callerBlock.nxtBlock, type: 'perform-return', scope: boundaryScope });
                                    }
                                }
                                continue; // Prevent the global jump
                            }

                            finalEdges.push({ from: baseEdge.from, to: baseEdge.to, type: baseEdge.type, scope: boundaryScope });
                        }
                    }

                    for (let j = startIndex; j <= endIndex; j++) {
                        const targetBlock = blocks[j];
                        const isLastBlock = (j === endIndex);
                        
                        // check for < 0 instead of === -1... now we have a -2 for end of if/when etc
                        const isHardJump = targetBlock.nxtBlock < 0 && 
                                           (targetBlock.outgoing_flow?.type === 'follow-along' || 
                                            targetBlock.outgoing_flow?.type === 'exit-section' || 
                                            targetBlock.outgoing_flow?.type === 'exit-paragraph');
                        
                        const isExplicitExit = targetBlock.nxtBlock < 0 && !isHardJump && !targetBlock.isTerminal;

                        if (isLastBlock || isExplicitExit) {
                            if (callerBlock.nxtBlock >= 0 && !targetBlock.isTerminal && !isHardJump) {
                                const edgeExists = finalEdges.some(e => e.from === j && e.to === callerBlock.nxtBlock && e.type === 'perform-return' && e.scope?.callerId === boundaryScope.callerId);
                                if (!edgeExists) {
                                    finalEdges.push({ from: j, to: callerBlock.nxtBlock, type: 'perform-return', scope: boundaryScope });
                                }
                            }
                        }
                    }
                }
            }
        }
        return finalEdges;
    }

    private pruneUnreachableGlobalEdges(edges: Edge[], startNode = 0): Edge[] {
        const globallyReachable = new Set<number>();
        const queue = [startNode];
        globallyReachable.add(startNode);

        const globalAdj = new Map<number, number[]>();
        for (const edge of edges) {
            const isPerformEdges = edge.scope?.callerId !== undefined;
            if (!isPerformEdges) {
                if (!globalAdj.has(edge.from)) globalAdj.set(edge.from, []);
                globalAdj.get(edge.from)!.push(edge.to);
            }
        }

        let head = 0;
        while (head < queue.length) {
            const current = queue[head++];
            const neighbors = globalAdj.get(current) || [];
            for (const nxt of neighbors) {
                if (!globallyReachable.has(nxt)) {
                    globallyReachable.add(nxt);
                    queue.push(nxt);
                }
            }
        }

        return edges.filter((edge) => {
            if (edge.scope?.callerId !== undefined) return true;
            return globallyReachable.has(edge.from);
        });
    }

    private expandAlterMacros(edges: Edge[], blocks: Block[], labelMap: Map<string, number>): Edge[] {
        const finalEdges = [...edges];
        for (let i = 0; i < blocks.length; i++) {
            const block = blocks[i];
            const alterMap = block.meta_data?.alter_date?.alterMap;

            if (alterMap && alterMap.size > 0) {
                const resolvedMutations: Record<number, number> = {};

                for (const [from, to] of alterMap.entries()) {
                    const alteredBlockIndex = labelMap.get(from);
                    const newTargetIndex = labelMap.get(to);

                    if (alteredBlockIndex !== undefined && newTargetIndex !== undefined) {
                        resolvedMutations[alteredBlockIndex] = newTargetIndex;

                        const edgeExists = finalEdges.some(
                            (e) => e.from === alteredBlockIndex && e.to === newTargetIndex && e.scope?.requiresAlter?.alteredBlock === alteredBlockIndex
                        );

                        if (!edgeExists) {
                            finalEdges.push({
                                from: alteredBlockIndex, to: newTargetIndex, type: "follow-along", 
                                scope: { requiresAlter: { alteredBlock: alteredBlockIndex, targetBlock: newTargetIndex } },
                            });
                        }
                    }
                }

                if (Object.keys(resolvedMutations).length > 0) {
                    const isConditional = block.meta_data?.alter_date?.isBranched || false;
                    const outgoingEdges = finalEdges.filter((e) => e.from === i);

                    for (const outEdge of outgoingEdges) {
                        if (isConditional) finalEdges.push({ from: outEdge.from, to: outEdge.to, type: outEdge.type, scope: outEdge.scope ? { ...outEdge.scope } : undefined });
                        if (!outEdge.scope) outEdge.scope = {};
                        outEdge.scope.mutatesAlter = resolvedMutations;
                    }
                }
            }
        }
        return finalEdges;
    }

    public condenseToCCFGraph(blocks: Block[], finalEdges: Edge[]): CCFGraph {
        const oldToNewMap = new Map<number, number>();
        const CCFNodes: CCFNode[] = [];
        let currentMacroIndex = -1;

        for (let i = 0; i < blocks.length; i++) {
            const block = blocks[i];
            const firstInstNode = block.instructions[0]?.instr.getInitialNode();

            const isLeader = firstInstNode && (firstInstNode.type === 'paragraph' || firstInstNode.type === 'section' || firstInstNode.type === 'program');

            if (isLeader || currentMacroIndex === -1) {
                currentMacroIndex++;
                CCFNodes.push({
                    id: currentMacroIndex, name: isLeader ? (firstInstNode as Program).name : `IMPLICIT_START`,
                    type: isLeader ? (firstInstNode.type as "paragraph" | "section") : 'implicit', originalBlocks: []
                });
            }

            oldToNewMap.set(i, currentMacroIndex);
            CCFNodes[currentMacroIndex].originalBlocks.push(i);
        }

        const macroEdges: Edge[] = [];
        const seenMacroEdges = new Set<string>();

        for (const edge of finalEdges) {
            const newFrom = oldToNewMap.get(edge.from)!;
            const newTo = oldToNewMap.get(edge.to)!;

            if (newFrom === newTo && edge.type === 'sequential') continue;

            let newScope = undefined;
            if (edge.scope) {
                newScope = { ...edge.scope };
                if (newScope.callerId !== undefined) newScope.callerId = oldToNewMap.get(newScope.callerId)!;
                if (newScope.endBlock !== undefined) newScope.endBlock = oldToNewMap.get(newScope.endBlock)!;
                if (newScope.requiresAlter) {
                    newScope.requiresAlter = { alteredBlock: oldToNewMap.get(newScope.requiresAlter.alteredBlock)!, targetBlock: oldToNewMap.get(newScope.requiresAlter.targetBlock)! };
                }
                if (newScope.mutatesAlter) {
                    const translatedMutates: Record<number, number> = {};
                    for (const [oldKey, oldVal] of Object.entries(newScope.mutatesAlter)) {
                        translatedMutates[oldToNewMap.get(Number(oldKey))!] = oldToNewMap.get(oldVal)!;
                    }
                    newScope.mutatesAlter = translatedMutates;
                }
            }

            const scopeHash = newScope ? JSON.stringify(newScope) : 'global';
            const edgeKey = `${newFrom}->${newTo}-${edge.type}-${scopeHash}`;

            if (!seenMacroEdges.has(edgeKey)) {
                seenMacroEdges.add(edgeKey);
                macroEdges.push({ from: newFrom, to: newTo, type: edge.type, scope: newScope });
            }
        }

        return { nodes: CCFNodes, edges: macroEdges };
    }

    private getLastIndexForTarget(targetName: string, labelMap: Map<string, number>, blocks: Block[], currentProgram: string): number | undefined {
        const index = labelMap.get(targetName);
        if (index === undefined) return undefined;

        const targetBlock = blocks[index];
        const node = targetBlock.instructions[0]?.instr.getInitialNode();

        let startIndexToScan = index;

        if (node?.type === "section") {
            const sectionName = (node as Program).name;
            const paras = this.structureTracker.getParagraphs(currentProgram, sectionName);

            if (paras && paras.length > 0) {
                const lastParaName = paras[paras.length - 1].name;
                const fullyQualifiedName = `${sectionName}.${lastParaName}`;
                startIndexToScan = labelMap.get(fullyQualifiedName) || labelMap.get(lastParaName) || index;
            }
        }

        for (let i = startIndexToScan + 1; i < blocks.length; i++) {
            const nextBlockNode = blocks[i].instructions[0]?.instr.getInitialNode();
            if (nextBlockNode && (nextBlockNode.type === 'paragraph' || nextBlockNode.type === 'section' || nextBlockNode.type === 'program')) {
                return i - 1; 
            }
        }

        return blocks.length - 1; 
    }

    private buildSymbolTable(blocks: Block[]): { table: Map<string, number>; idexToProgram: Map<number, string>; } {
        const table = new Map<string, number>();
        const idexToProgram = new Map<number, string>();
        let currentSection: string | undefined;
        let currentProgram: string | undefined;

        for (let i = 0; i < blocks.length; i++) {
            const firstInst = blocks[i].instructions[0]?.instr;
            const node = firstInst.getInitialNode();

            if (node) {
                if (node.type === "program" || node.type === "section") {
                    if (node.type === "program") currentProgram = (node as Program).name;
                    if (node.type === "section") currentSection = (node as Program).name;
                    table.set((node as Program).name, i);
                } else if (node.type === "paragraph") {
                    const paraName = (node as Program).name;
                    const qualifiedName = currentSection ? `${currentSection}.${paraName}` : paraName;
                    table.set(qualifiedName, i);
                    if (!table.has(paraName)) table.set(paraName, i); 
                }
                if (currentProgram) idexToProgram.set(i, currentProgram);
            }
        }
        return { table, idexToProgram };
    }

    private cleanGraphEdges(edges: Edge[], blocks: Block[], startNode = 0): Edge[] {
        const adj = new Map<number, Edge[]>();
        for (const edge of edges) {
            let list = adj.get(edge.from);
            if (!list) {
                list = [];
                adj.set(edge.from, list);
            }
            list.push(edge);
        }

        const reachableEdges = new Set<Edge>();
        const visitedEdgeKeys = new Set<string>();

        function traverse(node: number, activeAlters: Record<number, number>, activeCaller?: number): boolean {
            let foundExit = false;
            const outgoingEdges = adj.get(node) || [];

            const currentBlockIsAltered = activeAlters[node] !== undefined;
            const activeTarget = activeAlters[node];
            const validEdges: Edge[] = [];

            for (const edge of outgoingEdges) {
                if (currentBlockIsAltered) {
                    const isMatchingAlterEdge = edge.scope?.requiresAlter?.alteredBlock === node &&
                        edge.scope?.requiresAlter?.targetBlock === activeTarget;
                    if (!isMatchingAlterEdge) continue; 
                } else {
                    if (edge.scope?.requiresAlter) continue;
                }
                validEdges.push(edge);
            }

            const performEdges = validEdges.filter(e => e.type === 'return-back' && e.scope?.callerId === node);
            const sequentialEdges = validEdges.filter(e => e.type === 'sequential');
            const otherEdges = validEdges.filter(e => !performEdges.includes(e) && !sequentialEdges.includes(e));

            let performReturnedSuccessfully = true;
            if (performEdges.length > 0) {
                for (const pEdge of performEdges) {
                    const nextAlters = { ...activeAlters };
                    if (pEdge.scope?.mutatesAlter) Object.assign(nextAlters, pEdge.scope.mutatesAlter);

                    const stateHash = JSON.stringify(nextAlters);
                    const scopeHash = pEdge.scope ? JSON.stringify(pEdge.scope) : 'global';
                    const edgeKey = `${pEdge.from}->${pEdge.to}-${pEdge.type}-${stateHash}-${scopeHash}`;

                    if (!visitedEdgeKeys.has(edgeKey)) {
                        visitedEdgeKeys.add(edgeKey);
                        reachableEdges.add(pEdge);

                        const performReturned = traverse(pEdge.to, nextAlters, pEdge.scope!.callerId);
                        if (!performReturned) performReturnedSuccessfully = false;
                    } else {
                        performReturnedSuccessfully = false;
                    }
                }
            }

            const hasHardJump = otherEdges.some(e => e.type === 'follow-along');
            const isTerminal = blocks[node].isTerminal === true;

            const canFallThrough = !hasHardJump && !isTerminal ; // shiw edges that crash as wll => bring parity && (performEdges.length === 0 || performReturnedSuccessfully);
            const falseBlockIndex = (blocks[node].meta_data as any)?.falseBlock;

            for (const seqEdge of sequentialEdges) {
                const edgeCaller = seqEdge.scope?.callerId;
                if (edgeCaller !== activeCaller) continue;

                const isBypassEdge = seqEdge.to === falseBlockIndex;

                if (isBypassEdge || canFallThrough) {
                    const nextAlters = { ...activeAlters };
                    if (seqEdge.scope?.mutatesAlter) Object.assign(nextAlters, seqEdge.scope.mutatesAlter);

                    const stateHash = JSON.stringify(nextAlters);
                    const scopeHash = seqEdge.scope ? JSON.stringify(seqEdge.scope) : 'global';
                    const edgeKey = `${seqEdge.from}->${seqEdge.to}-${seqEdge.type}-${stateHash}-${scopeHash}`;

                    if (!visitedEdgeKeys.has(edgeKey)) {
                        visitedEdgeKeys.add(edgeKey);
                        reachableEdges.add(seqEdge);
                        if (traverse(seqEdge.to, nextAlters, activeCaller)) foundExit = true;
                    }
                }
            }

            for (const otherEdge of otherEdges) {
                const edgeCaller = otherEdge.scope?.callerId;
                if (otherEdge.type === "perform-return") {
                    if (edgeCaller !== activeCaller) continue; 
                } else if (otherEdge.type === "follow-along") {
                    if (edgeCaller !== activeCaller && edgeCaller !== undefined) continue;
                }

                const nextAlters = { ...activeAlters };
                if (otherEdge.scope?.mutatesAlter) Object.assign(nextAlters, otherEdge.scope.mutatesAlter);

                const stateHash = JSON.stringify(nextAlters);
                const scopeHash = otherEdge.scope ? JSON.stringify(otherEdge.scope) : 'global';
                const edgeKey = `${otherEdge.from}->${otherEdge.to}-${otherEdge.type}-${stateHash}-${scopeHash}`;

                if (!visitedEdgeKeys.has(edgeKey)) {
                    visitedEdgeKeys.add(edgeKey);
                    reachableEdges.add(otherEdge);

                    if (otherEdge.type === "perform-return") foundExit = true;
                    else if (traverse(otherEdge.to, nextAlters, activeCaller)) foundExit = true;
                }
            }
            return foundExit;
        }

        traverse(startNode, {});
        return Array.from(reachableEdges);
    }
}