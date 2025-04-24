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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp.cobol.common.copybook.CopyBookDTO;
import org.eclipse.lsp.cobol.service.io.FileDownload;

/** Dummy implementation of {@link FileDownload} which does nothing */
public class DummyFileDownloadService implements FileDownload {
  /**
   * @param cobolFileUri
   * @param copybooks
   * @param quietMode
   * @return
   */
  @Override
  public CompletableFuture<Void> downloadCopybooks(
      String cobolFileUri, List<CopyBookDTO> copybooks, boolean quietMode) {
    return CompletableFuture.completedFuture(null);
  }
}
