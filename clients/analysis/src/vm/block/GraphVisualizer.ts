import { CCFGraph } from "./types";


export class GraphVisualizer {
  /**
   * Converts a ControlFlowGraph into a Graphviz DOT format string.
   */
  public static toDotFormat(cfg: CCFGraph): string {
    let dot = 'digraph COBOL_MACRO_CFG {\n';

    // General graph styling
    dot += '    node [shape=box, style="rounded,filled", fontname="Helvetica", fontsize=12];\n';
    dot += '    edge [fontname="Helvetica", fontsize=10];\n\n';

    dot += '    /* --- Macro Nodes (Paragraphs & Sections) --- */\n';
    cfg.nodes.forEach(node => {
      // Determine styling based on the macro type
      let fillColor = "#ffffff";
      let shape = "box";

      if (node.type === 'section') {
        fillColor = "#e1bee7"; // Light purple for Sections
        shape = "folder"; // Folder shape to imply grouping
      } else if (node.type === 'paragraph') {
        fillColor = "#bbdefb"; // Light blue for Paragraphs
      } else {
        fillColor = "#f5f5f5"; // Grey for Implicit/Start blocks
      }

      // Clean up the label to show the name clearly
      const label = `<<B>${node.name}</B><BR/><FONT POINT-SIZE="9">ID: M${node.id} | Blocks: [${node.originalBlocks.join(',')}]</FONT>>`;

      dot += `    M${node.id} [label=${label}, fillcolor="${fillColor}", shape="${shape}"];\n`;
    });

    dot += '\n    /* --- Edges --- */\n';
    cfg.edges.forEach(edge => {
      let color = "gray30";
      let style = "solid";
      let edgeLabel = edge.type;
      let weight = 1;

      // 1. Style based on control flow type
      if (edge.type === 'perform-return') return; // Hide exit doors visually
      if (edge.type === 'return-back') {
        color = "#d32f2f"; // Red
        style = "dashed"; // Jumps into and returns from PERFORMs
      } else if (edge.type === 'follow-along') {
        color = "#1976d2"; // Blue
        weight = 2; // GO TO branches
      } else if (edge.type === 'sequential') {
        color = "#212121"; // Black
        weight = 3; // Standard fall-throughs naturally want to drop straight down
      }

      // 2. Append Scope Information
      if (edge.scope) {
        const scopeLabels: string[] = [];
        style = style === "dashed" ? "dashed,bold" : "bold"; // Thicken scoped lanes


        // A. PERFORM Boundaries
        if (edge.scope.callerId !== undefined && edge.scope.endBlock !== undefined) {
          scopeLabels.push(`Stack: M${edge.scope.callerId} ➔ M${edge.scope.endBlock}`);
        }

        // B. ALTER Mutations (The State Payload)
        if (edge.scope.mutatesAlter) {
          const mutations = Object.entries(edge.scope.mutatesAlter)
            .map(([from, to]) => `M${from}➔M${to}`)
            .join(', ');
          scopeLabels.push(`Mutates: {${mutations}}`);
          color = "#e65100"; // Orange to highlight state changes
        }

        // C. ALTER Requirements (The Conditional Jump)
        if (edge.scope.requiresAlter) {
          const req = edge.scope.requiresAlter;
          scopeLabels.push(`Requires: M${req.alteredBlock}➔M${req.targetBlock}`);
          style = "dotted,bold"; // Dotted line for conditional/altered paths
          color = "#e65100"; // Orange
        }

        if (scopeLabels.length > 0) {
          edgeLabel += `\\n[${scopeLabels.join(' | ')}]`;
        }
      }

      dot += `    M${edge.from} -> M${edge.to} [label="${edgeLabel}", color="${color}", style="${style}", weight=${weight}];\n`;
    });

    dot += '}\n';
    return dot;
  }
}
