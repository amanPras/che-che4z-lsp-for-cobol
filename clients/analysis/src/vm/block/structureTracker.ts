import { Location, Paragraph, Program, Section } from "../../model";
import { CobolInstruction } from "../instructions";

export interface ParagraphDetail {
  name: string;
  location: Location;
}

export class StructureTracker {
  public static readonly DEFAULT_PROGRAM = "[[DEFAULT_PROGRAM]]";
  public static readonly DEFAULT_SECTION = "[[DEFAULT_SECTION]]";
  private currentProgram = StructureTracker.DEFAULT_PROGRAM;
  private currentSection = StructureTracker.DEFAULT_SECTION;

  // Data Structure: Map<ProgramName, Map<SectionName, ParagraphDetail[]>>
  private hierarchyMap: Map<string, Map<string, ParagraphDetail[]>>;

  constructor() {
    this.hierarchyMap = new Map();
  }

  /**
   * Helper to ensure the nested maps exist for a given program and section.
   */
  private ensurePaths(programName: string, sectionName: string): void {
    if (!this.hierarchyMap.has(programName)) {
      this.hierarchyMap.set(programName, new Map());
    }
    const sectionMap = this.hierarchyMap.get(programName)!;
    if (!sectionMap.has(sectionName)) {
      sectionMap.set(sectionName, []);
    }
  }

  public trackInstructions(instruction: CobolInstruction): void {
    if (instruction.getInitialNode()) {
      const node = instruction.getInitialNode()!;
      if (node.type === "program") {
        this.currentProgram = (node as Program).name;
        this.currentSection = StructureTracker.DEFAULT_SECTION;
        this.ensurePaths(this.currentProgram, this.currentSection);
      } else if (node.type === "section") {
        this.currentSection = (node as Section).name;
        this.ensurePaths(this.currentProgram, this.currentSection);
      } else if (node.type === "paragraph") {
        this.ensurePaths(this.currentProgram, this.currentSection);

        const sectionMap = this.hierarchyMap.get(this.currentProgram)!;
        sectionMap.get(this.currentSection)!.push({
          name: (node as Paragraph).name,
          location: node.location,
        });
      }
    }
  }

  /**
   * Returns a list of all Program names discovered.
   */
  public getAllProgramNames(): string[] {
    return Array.from(this.hierarchyMap.keys());
  }

  /**
   * Returns a list of all Section names within a specific Program.
   */
  public getSectionsInProgram(programName: string): string[] {
    const sectionMap = this.hierarchyMap.get(programName);
    return sectionMap ? Array.from(sectionMap.keys()) : [];
  }

  /**
   * Retrieves all paragraphs belonging to a specific section within a specific program.
   */
  public getParagraphs(
    programName: string,
    sectionName: string,
  ): ParagraphDetail[] {
    const sectionMap = this.hierarchyMap.get(programName);
    if (!sectionMap) return [];

    return sectionMap.get(sectionName) || [];
  }
}
