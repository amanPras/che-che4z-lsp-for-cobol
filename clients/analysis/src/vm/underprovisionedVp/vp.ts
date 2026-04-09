import { VirtualProcessorListener } from "../listener";
import { ProgramListing } from "../listing";
import { VirtualMachine, VmContext } from "../vm";
import { cfastNodeInfo, Channel } from "../logger";
import {
  AlterInstruction,
  CicsInstruction,
  ConditionExit,
  GotoInstruction,
  JumpInstruction,
  PerformInstruction,
  ProgramUnit,
  RestoreProgramUnit,
  SqlInstruction,
} from "../instructions";
import { ConstrainedVmOptimiser } from "./constrainedVmOptimizer";

export class VirtualProcessor {
  private forkedvms: VirtualMachine[] = [];
  private mainVm: VirtualMachine | undefined;
  private optimizer = new ConstrainedVmOptimiser();

  public constructor(
    private programListing: ProgramListing,
    private listener: VirtualProcessorListener,
    private logger?: Channel,
  ) {}

  public run(): void {
    this.mainVm = new VirtualMachine(new VmContext(this.programListing, this.listener));
    this.forkedvms.push(this.mainVm);

    while (this.forkedvms.length > 0) {
      const currentVm = this.forkedvms.shift();
      if (currentVm) {
        this.stepIntoProgramUnits(currentVm, currentVm.getContext().getNestedLevel());
      }
    }
  }

  private stepIntoProgramUnits(vm: VirtualMachine, nestedLevel: number): void {
    let canContinue = true;
    while (canContinue) {
      const vmState = vm.generateState();
      const vmIc = vm.ic();
      
      canContinue = this.step(vm, nestedLevel);
      if (canContinue) {
        this.optimizer.processedVm(vmState, vmIc);
      }
    }
  }

  private step(vm: VirtualMachine, nestedLevel: number): boolean {
    this.logger?.debug(
      `${vm.getInfo()}: ${cfastNodeInfo(vm.currentInstruction()?.getInitialNode())}, CPU: ${vm.getCurrentProgramUnit()?.id}`,
    );

    vm.updateProgramUnit();
    const prevPU = vm.getCurrentProgramUnit();

    if (!this.optimizer.canContinue(vm, nestedLevel)) {
      return false;
    }

    const currentInstruction = vm.currentInstruction();
    
    // optimize
    if (currentInstruction?.isProcessed() && this.optimizer.isProcessedVm(vm)) {
      return false;
    }

    switch (true) {
      case currentInstruction instanceof JumpInstruction:
        vm.step();
        return true;

      case currentInstruction instanceof ConditionExit && currentInstruction.isProcessed():
        vm.passThroughStep(); 
        return true;

      case currentInstruction instanceof SqlInstruction: 
      case currentInstruction instanceof CicsInstruction: 
          const nxtVms = vm.passThroughStep();
          this.handleForks(nxtVms);
          return true;

      case currentInstruction instanceof PerformInstruction: {
        const nxtVms = vm.passThroughStep();
        const result = this.handleForks(nxtVms);
        if (result.length > 0) {
          result.filter(ele => !ele.isForked).forEach(nvm => this.listener.moveControl(vm.getCurrentProgramUnit(), nvm.forkVm.getCurrentProgramUnit()))
        }
        const initialNode = currentInstruction.getInitialNode() as any;
        if (initialNode?.performUntilType === "UNTIL_EXIT") {
          return false; 
        }
        return true;
      }

      case currentInstruction instanceof AlterInstruction: 
      case currentInstruction instanceof GotoInstruction: {
        const nxtVms = vm.passThroughStep();
        this.handleForks(nxtVms);
        return false;
      }

      default: {
        const newVms = vm.step();
        if (!newVms) return false;

        const isBranchingInstruction =
          currentInstruction instanceof PerformInstruction ||
          currentInstruction instanceof GotoInstruction ||
          currentInstruction instanceof ProgramUnit ||
          currentInstruction instanceof RestoreProgramUnit;

        if (vm.getCurrentProgramUnit() !== prevPU && !isBranchingInstruction) {
          return false;
        }

        this.handleForks(newVms);
        return true;
      }
    }
  }

  private handleForks(newVms: VirtualMachine[] | undefined | null): {forkVm: VirtualMachine, isForked: boolean}[] {
    if (!newVms || newVms.length === 0) return [];
    
    let result: {forkVm: VirtualMachine, isForked: boolean}[] = [];
    for (const newVm of newVms) {
      if (this.optimizer.shouldFork(newVm)) {
        result.push({forkVm: newVm, isForked: true})
        this.forkedvms.push(newVm);
      } else {
        result.push({forkVm: newVm, isForked: false})
      }
    }
    return result;
  }
}