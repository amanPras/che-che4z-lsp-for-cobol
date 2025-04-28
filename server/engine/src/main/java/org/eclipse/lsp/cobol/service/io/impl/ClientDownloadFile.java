/*
 * Copyright (c) 2025 Broadcom.
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
package org.eclipse.lsp.cobol.service.io.impl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp.cobol.common.copybook.CopyBookDTO;
import org.eclipse.lsp.cobol.common.io.FileDownload;
import org.eclipse.lsp.cobol.lsp.jrpc.CobolLanguageClient;

/** Request client to download files */
public class ClientDownloadFile implements FileDownload {
  private final Provider<CobolLanguageClient> clientProvider;

  @Inject
  public ClientDownloadFile(Provider<CobolLanguageClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  /**
   * Request client to download files
   *
   * @param cobolFileUri
   * @param copybooks
   * @param quietMode
   * @return
   */
  @Override
  public CompletableFuture<Void> downloadCopybooks(
      String cobolFileUri, List<CopyBookDTO> copybooks, boolean quietMode) {
    return clientProvider.get().downloadCopybooks(cobolFileUri, copybooks, quietMode);
  }
}
