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
package org.eclipse.lsp.cobol.service.copybooks;

import java.util.*;
import lombok.NonNull;
import org.eclipse.lsp.cobol.common.CleanerPreprocessor;
import org.eclipse.lsp.cobol.common.ResultWithErrors;
import org.eclipse.lsp.cobol.common.copybook.*;
import org.eclipse.lsp.cobol.common.error.SyntaxError;

/**
 * This service processes predefined copybook requests and returns content by its name. The service
 * caches copybook.
 */
public class PredefinedCopybookService implements CopybookService {
  private final Map<CopybookName, CopybookModel> preDefinedCopybookCache = new HashMap<>();
  private final Map<String, List<SyntaxError>> predefinedCopybookErrors = new HashMap<>();

  private static CopybookModel getDefaultCopybook(CopybookName copybookName, String programUri) {
    return new CopybookModel(copybookName.toCopybookId(programUri), copybookName, null, null);
  }

  /**
   * @param onlyNonImplicit
   */
  @Override
  public void invalidateCache(boolean onlyNonImplicit) {
    predefinedCopybookErrors.clear();
    preDefinedCopybookCache.clear();
  }

  /**
   * @param copybookId
   */
  @Override
  public void invalidateCache(CopybookId copybookId) {}

  /**
   * @param copybookId - the id of the copybook to be retrieved
   * @param copybookName - the name of the copybook to be retrieved
   * @param programDocumentUri - the currently processing program document
   * @param documentUri - the currently processing document that contains the copy statement
   * @param preprocessor - Cleanup preprocessor that will be used for new copybooks or null
   * @return
   */
  @Override
  public ResultWithErrors<CopybookModel> resolve(
      @NonNull CopybookId copybookId,
      @NonNull CopybookName copybookName,
      @NonNull String programDocumentUri,
      @NonNull String documentUri,
      CleanerPreprocessor preprocessor) {
    return getPredefinedCopybooks(copybookName, programDocumentUri);
  }

  /**
   * @param copybookModel the copybook model
   */
  @Override
  public void store(CopybookModel copybookModel) {
    preDefinedCopybookCache.put(copybookModel.getCopybookName(), copybookModel);
  }

  /**
   * @param copybookModel the copybook model
   * @param preprocessor - Cleanup preprocessor that will be used for new copybooks or null
   */
  @Override
  public void store(CopybookModel copybookModel, CleanerPreprocessor preprocessor) {
    if (preprocessor != null) {
      ResultWithErrors<CopybookModel> processedCopybook =
          CopybookUtility.cleanupCopybook(copybookModel, preprocessor);
      copybookModel = processedCopybook.getResult();
      predefinedCopybookErrors.put(copybookModel.getUri(), processedCopybook.getErrors());
    }
    store(copybookModel);
  }

  /**
   * @param documentUri current document uri.
   * @param copybookUris collection of copybook uris.
   * @param processingMode copybook processing mode.
   */
  @Override
  public void sendCopybookDownloadRequest(
      String documentUri, Collection<String> copybookUris, CopybookProcessingMode processingMode) {}

  /**
   * @param documentUri current document uri.
   * @return
   */
  @Override
  public Set<CopybookModel> getCopybookUsage(String documentUri) {
    return Collections.emptySet();
  }

  private ResultWithErrors<CopybookModel> getPredefinedCopybooks(
      @NonNull CopybookName copybookName, @NonNull String programUri) {
    return new ResultWithErrors<>(
        preDefinedCopybookCache.getOrDefault(
            copybookName, getDefaultCopybook(copybookName, programUri)),
        predefinedCopybookErrors.getOrDefault(programUri, Collections.emptyList()));
  }
}
