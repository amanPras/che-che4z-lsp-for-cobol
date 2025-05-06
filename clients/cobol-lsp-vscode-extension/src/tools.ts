import * as vscode from "vscode";
import { ControlFlowAnalysisService } from "./services/ControlFlowService";
export class Tool implements vscode.LanguageModelTool<{ uri: string }> {
  private analysisService: ControlFlowAnalysisService;
  constructor(analysisService: ControlFlowAnalysisService) {
    this.analysisService = analysisService;
  }
  invoke(
    options: vscode.LanguageModelToolInvocationOptions<{ uri: string }>,
    _token: vscode.CancellationToken,
  ): vscode.ProviderResult<vscode.LanguageModelToolResult> {
    return this.getGraph(options.input.uri);
  }
  // prepareInvocation?(options: vscode.LanguageModelToolInvocationPrepareOptions<string>, token: vscode.CancellationToken): vscode.ProviderResult<vscode.PreparedToolInvocation> {
  //     throw new Error('Method not implemented.');
  // }

  private async getGraph(uri: string) {
    const analysisResult = await this.analysisService.getAnalysis(uri);
    return new vscode.LanguageModelToolResult([
      new vscode.LanguageModelTextPart(JSON.stringify(analysisResult.graphs)),
    ]);
  }
}
