/*
 * Copyright (c) 2023 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom, Inc. - initial API and implementation
 *
 */
package org.eclipse.lsp.cobol.lsp.handlers.text;

import static java.lang.String.format;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.lsp.DisposableLSPStateService;
import org.eclipse.lsp.cobol.lsp.SourceUnitGraph;
import org.eclipse.lsp.cobol.lsp.analysis.AsyncAnalysisService;
import org.eclipse.lsp.cobol.service.DocumentModelService;
import org.eclipse.lsp.cobol.service.WatcherService;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;

/** LSP DidClose Handler */
@Slf4j
public class DidCloseHandler {
  private final DisposableLSPStateService disposableLSPStateService;
  private final AsyncAnalysisService asyncAnalysisService;
  private final DocumentModelService documentModelService;
  private final WatcherService watcherService;
  private final SourceUnitGraph sourceUnitGraph;

  @Inject
  public DidCloseHandler(
      DisposableLSPStateService disposableLSPStateService,
      AsyncAnalysisService asyncAnalysisService,
      DocumentModelService documentModelService,
      WatcherService watcherService,
      SourceUnitGraph sourceUnitGraph) {
    this.disposableLSPStateService = disposableLSPStateService;
    this.asyncAnalysisService = asyncAnalysisService;
    this.documentModelService = documentModelService;
    this.watcherService = watcherService;
    this.sourceUnitGraph = sourceUnitGraph;
  }

  /**
   * Handle LSP didClose request.
   *
   * @param params DidCloseTextDocumentParams.
   */
  public void didClose(DidCloseTextDocumentParams params) throws InterruptedException {
    if (disposableLSPStateService.isServerShutdown()) {
      return;
    }
    String uri = params.getTextDocument().getUri();
    LOG.info(format("Document closing invoked on URI %s", uri));
    if (!sourceUnitGraph.isFileOpened(uri)) {
      LOG.info(format("Ignoring document closing invoked on URI %s", uri));
      return;
    }
    watcherService.removeRuntimeWatchers(uri);
    documentModelService.closeDocument(uri);
    asyncAnalysisService.cancelAnalysis(uri);
  }
}
