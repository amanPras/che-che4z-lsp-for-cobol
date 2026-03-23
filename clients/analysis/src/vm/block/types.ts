import { CobolInstruction } from "../instructions";


export type EdgeType = "sequential" | "follow-along" | "return-back" | "perform-return";

export interface Edge {
  from: number;
  to: number;
  type: EdgeType;
  scope?: {
    callerId?: number;
    endBlock?: number;
    // ALTER State Data
    mutatesAlter?: Record<number, number>;
    requiresAlter?: { alteredBlock: number; targetBlock: number; }; // Condition to take this edge
  };
}

export interface ControlFlowGraph {
  nodes: Block[];
  edges: Edge[];
}

export type Block = {
  instructions: { instr: CobolInstruction; isUnderCondition: boolean; }[];
  nxtBlock: number;
  isTerminal: boolean;
  isUnderCondition: boolean;
  type: "normal" | "section";
  meta_data: {
    alter_date: {
      alterMap: Map<string, string>;
      isBranched?: boolean;
    };
  };
  outgoing_flow?: {
    name: string | string[]; //TODO: check array scenario, added to pass the types check
    thru?: string;
    type: "follow-along" | "return-back" | "exit-section" | "exit-paragraph";
    line?: number;
  };
  incoming_flow: { from: number; type: EdgeType; }[]; //populate this at the very end
};

export interface CCFNode {
  id: number;
  name: string;
  type: 'section' | 'paragraph' | 'implicit';
  originalBlocks: number[];
}

export interface CCFGraph {
  nodes: CCFNode[];
  edges: Edge[];
}