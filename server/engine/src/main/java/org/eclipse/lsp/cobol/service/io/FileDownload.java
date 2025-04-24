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
package org.eclipse.lsp.cobol.service.io;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp.cobol.common.copybook.CopyBookDTO;

/** Contract to download file */
public interface FileDownload {
  /**
   * Requests client to download copybook
   *
   * @param cobolFileUri
   * @param copybooks
   * @param quietMode
   * @return void
   */
  CompletableFuture<Void> downloadCopybooks(
      String cobolFileUri, List<CopyBookDTO> copybooks, boolean quietMode);
}
