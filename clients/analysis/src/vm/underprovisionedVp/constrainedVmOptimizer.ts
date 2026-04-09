import { VirtualMachine, VirtualMachineState } from "../vm";

export class ConstrainedVmOptimiser {
  private visitedStates = new Set<string>();
  private processedVmState = new Map<number, Set<number>>();

  public canContinue(vm: VirtualMachine, targetNestedLevel: number): boolean {
    return vm.getContext().getNestedLevel() >= targetNestedLevel;
  }

  public processedVm(state: VirtualMachineState, ic: number): void {
    state.vnCellStates.clear();
    const hash = state.getHash();
    
    let icSet = this.processedVmState.get(hash);
    if (!icSet) {
      icSet = new Set<number>();
      this.processedVmState.set(hash, icSet);
    }
    icSet.add(ic);
  }

  public isProcessedVm(vm: VirtualMachine): boolean {
    const state = vm.getContext().generateVmState();
    state.vnCellStates.clear();
    
    const icSet = this.processedVmState.get(state.getHash());
    return icSet !== undefined && icSet.has(vm.ic());
  }

  public shouldFork(vm: VirtualMachine): boolean {
    if (this.isProcessedVm(vm)) {
      return false;
    }

    const stateId = vm.getId();
    if (this.visitedStates.has(stateId)) {
      return false; 
    }
    
    this.visitedStates.add(stateId);
    return true;
  }
}