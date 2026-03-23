import { Alter, Goto, Perform } from "../../model";
import { AlterInstruction, CobolInstruction, ProgramUnit } from "../instructions";
import { Block } from "./types";
import { StructureTracker } from "./structureTracker";

const COND_ENTRIES = ["if", "atEnd", "onException", "inlineperform"]; // these are not available yet, "invalidKey", "onSizeError", "onOverflow"];
const COND_ELSES = ["else"]; // not aviable ->  "notOnOverflow",  "notAtEnd", "notInvalidKey", "notOnSizeError", "notOnException"];
const COND_EXITS = ["endif", "atEndExit", "endinlineperform"]; // not available ->  "invalidKeyExit", "onSizeErrorExit", "onExceptionExit", "onOverflowExit"];

/**
 * Fundamental blocks for a code flow
 * Propeteries -
 * 1. single entry
 * 2. single exit
 * 3. branch - last entry
 * 4. target - 1st instr
 */
export class BlockGenerator {
  constructor(private instructions: CobolInstruction[], private structureTracker: StructureTracker) {}
  private isHardStopNode(nodeType: string | undefined): boolean {
      if (!nodeType) return false;
      return nodeType === "stop" || nodeType === "goto" || 
             nodeType === "exitsection" || nodeType === "exitparagraph" || 
             nodeType === "exit-program" || nodeType === "goback";
  }

  private createEmptyBlock(type: "normal" | "section"): Block {
    return {
      instructions: [], isTerminal: false, nxtBlock: -1, isUnderCondition: false, type,
      meta_data: { alter_date: { alterMap: new Map<string, string>(), isBranched: false }, falseBlock: undefined } as any,
      incoming_flow: [],
    };
  }

  public generateBlocksOptimized(): Block[] {
    const blocks: Block[] = [];
    let currentBlock = this.createEmptyBlock("normal");
    let currentInstructionUnderCondition = false;
    
    // The Syntax Context Stacks
    const ifStack: { ifBlockIndex: number, trueBranchEnds: number[], hasElse: boolean, isLoop: boolean }[] = []; 
    const evaluateStack: { whenConditionBlocks: number[], bodyEndBlocks: number[], hasWhenOther: boolean }[] = []; 

    const pushBlock = (block: Block, isUnderCondition: boolean) => {
      block.isUnderCondition = isUnderCondition;
      if (block.instructions.length === 0) return;
      if (blocks.length > 0) {
        const prevBlock = blocks[blocks.length - 1];
        const lastInst = prevBlock.instructions[prevBlock.instructions.length - 1];
        const nodeType = lastInst?.instr.getInitialNode()?.type;
        
        if (!this.isHardStopNode(nodeType) && prevBlock.nxtBlock === -1) {
          prevBlock.nxtBlock = blocks.length;
        }
      }
      blocks.push(block);
    };

    for (let i = 0; i < this.instructions.length; i++) {
      const unit = this.instructions[i];
      const initialNode = unit.getInitialNode();
      
      if (!initialNode) continue;

      if (unit instanceof ProgramUnit) {
        this.structureTracker.trackInstructions(unit);
        if (currentBlock.instructions.length > 0) {
          pushBlock(currentBlock, currentInstructionUnderCondition);
          currentBlock = this.createEmptyBlock(initialNode.type === "section" ? "section" : "normal");
        } else if (initialNode.type === "section") {
          currentBlock.type = "section";
        }
      }

      if (COND_ENTRIES.includes(initialNode.type)) {
        if (currentBlock.instructions.length > 0) {
          pushBlock(currentBlock, currentInstructionUnderCondition);
          currentBlock = this.createEmptyBlock("normal");
        }
        const ifBlockIndex = blocks.length; 
        
        // flag inlineperform as a looping branch!
        const isLoop = initialNode.type === "inlineperform";
        
        ifStack.push({ ifBlockIndex, trueBranchEnds: [], hasElse: false, isLoop });
        currentInstructionUnderCondition = true;
      }

      if (COND_ELSES.includes(initialNode.type)) {
        if (currentBlock.instructions.length > 0) {
          pushBlock(currentBlock, currentInstructionUnderCondition);
          currentBlock = this.createEmptyBlock("normal");
        }
        const ifCtx = ifStack[ifStack.length - 1];
        if (ifCtx) {
          ifCtx.hasElse = true;
          const lastTrueBlockIndex = blocks.length - 1;
          
          if (lastTrueBlockIndex >= 0) {
              const lastTrueBlock = blocks[lastTrueBlockIndex];
              const lastInstType = lastTrueBlock.instructions[lastTrueBlock.instructions.length - 1]?.instr.getInitialNode()?.type;
              
              if (!this.isHardStopNode(lastInstType) && !lastTrueBlock.isTerminal) {
                  ifCtx.trueBranchEnds.push(lastTrueBlockIndex);
              }
              lastTrueBlock.nxtBlock = -2; 
          }
          (blocks[ifCtx.ifBlockIndex].meta_data as any).falseBlock = blocks.length;
        }
      }

      if (COND_EXITS.includes(initialNode.type)) {
        if (currentBlock.instructions.length > 0) {
          pushBlock(currentBlock, currentInstructionUnderCondition);
          currentBlock = this.createEmptyBlock("normal");
        }
        const ifCtx = ifStack.pop();
        if (ifCtx) {
          const endIfIndex = blocks.length; 
          if (!ifCtx.hasElse) {
            const lastTrueBlockIndex = blocks.length - 1;
            if (lastTrueBlockIndex >= 0 && lastTrueBlockIndex >= ifCtx.ifBlockIndex) {
               const lastTrueBlock = blocks[lastTrueBlockIndex];
               const lastInstType = lastTrueBlock.instructions[lastTrueBlock.instructions.length - 1]?.instr.getInitialNode()?.type;
               
               if (!this.isHardStopNode(lastInstType) && !lastTrueBlock.isTerminal) {
                   // Loop back up if it's an inline perform! Otherwise fall to the end.
                   lastTrueBlock.nxtBlock = ifCtx.isLoop ? ifCtx.ifBlockIndex : endIfIndex;
               } else {
                // When processing ELSE nodes or WHEN bodies,  
                // tag the previous True-branch block with nxtBlock = -2
                   if (lastTrueBlock.nxtBlock === -1) lastTrueBlock.nxtBlock = -2;
               }
            }
            (blocks[ifCtx.ifBlockIndex].meta_data as any).falseBlock = endIfIndex;
          }
          
          for (const tbIndex of ifCtx.trueBranchEnds) {
            if (blocks[tbIndex]) {
                 blocks[tbIndex].nxtBlock = ifCtx.isLoop ? ifCtx.ifBlockIndex : endIfIndex;
            }
          }
        }
        currentInstructionUnderCondition = ifStack.length > 0 || evaluateStack.length > 0;
      }

      if (initialNode.type === "evaluate") {
        if (currentBlock.instructions.length > 0) {
          pushBlock(currentBlock, currentInstructionUnderCondition);
          currentBlock = this.createEmptyBlock("normal");
        }
        evaluateStack.push({ whenConditionBlocks: [], bodyEndBlocks: [], hasWhenOther: false });
      }

      if (initialNode.type === "when" || initialNode.type === "whenother") {
        if (currentBlock.instructions.length > 0) {
          pushBlock(currentBlock, currentInstructionUnderCondition);
          currentBlock = this.createEmptyBlock("normal");
        }
        const evalCtx = evaluateStack[evaluateStack.length - 1];
        if (evalCtx) {
          if (evalCtx.whenConditionBlocks.length > 0) {
            const lastBodyBlockIndex = blocks.length - 1;
            if (lastBodyBlockIndex >= 0) {
                const lastBodyBlock = blocks[lastBodyBlockIndex];
                const lastInstType = lastBodyBlock.instructions[lastBodyBlock.instructions.length - 1]?.instr.getInitialNode()?.type;
                if (!this.isHardStopNode(lastInstType) && !lastBodyBlock.isTerminal) {
                    evalCtx.bodyEndBlocks.push(lastBodyBlockIndex);
                }
                lastBodyBlock.nxtBlock = -2; 
            }
            if (!evalCtx.hasWhenOther) {
                const lastWhenIndex = evalCtx.whenConditionBlocks[evalCtx.whenConditionBlocks.length - 1];
                (blocks[lastWhenIndex].meta_data as any).falseBlock = blocks.length;
            }
          }
          if (initialNode.type === "whenother") evalCtx.hasWhenOther = true;
          else evalCtx.whenConditionBlocks.push(blocks.length);
        }
        currentInstructionUnderCondition = true;
      }

      if (initialNode.type === "endevaluate") {
        if (currentBlock.instructions.length > 0) {
          pushBlock(currentBlock, currentInstructionUnderCondition);
          currentBlock = this.createEmptyBlock("normal");
        }
        currentInstructionUnderCondition = false;
        const evalCtx = evaluateStack.pop();
        if (evalCtx) {
          const endEvalIndex = blocks.length; 
          if (evalCtx.whenConditionBlocks.length > 0 && !evalCtx.hasWhenOther) {
            const lastWhenIndex = evalCtx.whenConditionBlocks[evalCtx.whenConditionBlocks.length - 1];
            (blocks[lastWhenIndex].meta_data as any).falseBlock = endEvalIndex;
          }
          for (const bodyEndIndex of evalCtx.bodyEndBlocks) {
            if (blocks[bodyEndIndex]) blocks[bodyEndIndex].nxtBlock = endEvalIndex;
          }
        }
        currentInstructionUnderCondition = ifStack.length > 0 || evaluateStack.length > 0;
      }

      currentBlock.instructions.push({ instr: unit, isUnderCondition: currentInstructionUnderCondition });

      if (unit instanceof AlterInstruction && unit.from !== undefined && unit.to !== undefined) {
        const alternode = unit.getInitialNode() as Alter;
        currentBlock.meta_data.alter_date.alterMap.set(
          alternode.from.inSection ? `${alternode.from.inSection}.${alternode.from.name}` : alternode.from.name,
          alternode.to.inSection ? `${alternode.to.inSection}.${alternode.to.name}` : alternode.to.name,
        );
        currentBlock.meta_data.alter_date.isBranched = currentInstructionUnderCondition;
      }

      let shouldCloseBlock = false;

      if (initialNode.type === "goto") {
        const gotoNode = initialNode as Goto;
        const targetName = Array.isArray(gotoNode.targetName) ? gotoNode.targetName[0] : gotoNode.targetName;
        currentBlock.outgoing_flow = { name: targetName, type: "follow-along" };
        shouldCloseBlock = true;
      } else if (initialNode.type === "perform") {
        const performNode = initialNode as Perform;
        if (performNode.targetName) {
            const tName = Array.isArray(performNode.targetName) ? performNode.targetName[0] : performNode.targetName;
            const target = performNode.targetSectionName ? `${performNode.targetSectionName}.${tName}` : tName;
            
            let thru = undefined;
            if (performNode.thruName) {
                const thruNameRaw = Array.isArray(performNode.thruName) ? performNode.thruName[0] : performNode.thruName;
                thru = performNode.thruSectionName ? `${performNode.thruSectionName}.${thruNameRaw}` : thruNameRaw;
            }
            currentBlock.outgoing_flow = { name: target, type: "return-back", thru };
            shouldCloseBlock = true;
        }
      } else if (initialNode.type === "exitsection") {
        currentBlock.outgoing_flow = { name: "NEXT_SECTION", type: "exit-section" };
        shouldCloseBlock = true;
      } else if (initialNode.type === "exitparagraph") {
        currentBlock.outgoing_flow = { name: "NEXT_PARAGRAPH", type: "exit-paragraph" };
        shouldCloseBlock = true;
      } else if (initialNode.type === "stop" || initialNode.type === "goback") { 
        shouldCloseBlock = true;
        currentBlock.isTerminal = true;
      } else if (initialNode.type === "exit" || COND_EXITS.includes(initialNode.type)) { 
        shouldCloseBlock = true; 
      }

      if (shouldCloseBlock) {
        pushBlock(currentBlock, currentInstructionUnderCondition);
        currentBlock = this.createEmptyBlock("normal");
      }
    }

    if (currentBlock.instructions.length > 0) pushBlock(currentBlock, currentInstructionUnderCondition);
    return blocks;
  }

  public buildLineToBlockMap(blocks: Block[]): Map<number, number> {
    const lineMap = new Map<number, number>();

    for (let i = 0; i < blocks.length; i++) {
      for (const inst of blocks[i].instructions) {
        const node = inst.instr.getInitialNode() as any;
        if (!node || !node.location) continue;
        const line = node.location.start?.line ?? node.location.line;
        if (line !== undefined && !lineMap.has(line)) {
          lineMap.set(line, i);
        }
      }
    }
    return lineMap;
  }
}