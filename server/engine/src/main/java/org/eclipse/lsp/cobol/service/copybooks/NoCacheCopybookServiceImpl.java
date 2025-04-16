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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.common.CleanerPreprocessor;
import org.eclipse.lsp.cobol.common.ResultWithErrors;
import org.eclipse.lsp.cobol.common.copybook.CopybookId;
import org.eclipse.lsp.cobol.common.copybook.CopybookModel;
import org.eclipse.lsp.cobol.common.copybook.CopybookName;
import org.eclipse.lsp.cobol.common.copybook.CopybookProcessingMode;
import org.eclipse.lsp.cobol.common.file.FileSystemService;
import org.eclipse.lsp.cobol.lsp.jrpc.CobolLanguageClient;

/**
 * This service processes copybook requests and returns content by its name. This service relies on
 * the client for the copybook content resolution and doesn't maintain any cache.
 */
@Slf4j
@Singleton
public class NoCacheCopybookServiceImpl extends CopybookServiceImpl {
  @Inject
  public NoCacheCopybookServiceImpl(
      Provider<CobolLanguageClient> clientProvider,
      FileSystemService files,
      CopybookCache copybookCache) {
    super(clientProvider, files, copybookCache);
  }

  private static CopybookModel getDefaultCopybook(CopybookName copybookName, String programUri) {
    return new CopybookModel(copybookName.toCopybookId(programUri), copybookName, null, null);
  }

  /**
   * Retrieve and return a CopybookModel by its name and preprocessed errors for the Retrieved
   * copybook wrapped inside {@link ResultWithErrors}. Copybook are not cached.
   *
   * <p>Resolving works in synchronous way. Resolutions with different copybook names will not block
   * each other.
   *
   * @param copybookId - the id of the copybook to be retrieved
   * @param copybookName - the name of the copybook to be retrieved
   * @param programUri - the currently processing program document
   * @param documentUri - the currently processing document that contains the copy statement
   * @param preprocessor - Cleanup preprocessor that will be used for new copybooks or null
   * @return a CopybookModel wrapped inside {@link ResultWithErrors} which contains copybook name,
   *     its URI and the content
   */
  @Override
  public ResultWithErrors<CopybookModel> resolve(
      @NonNull CopybookId copybookId,
      @NonNull CopybookName copybookName,
      @NonNull String programUri,
      @NonNull String documentUri,
      CleanerPreprocessor preprocessor) {

    try {
      CompletableFuture<String> futureUri =
          this.clientProvider
              .get()
              .resolveCopybookUri(
                  programUri,
                  copybookName.getDisplayName(),
                  Optional.ofNullable(copybookName.getDialectType()).orElse("COBOL"))
              .exceptionally(
                  ex -> {
                    LOG.warn(
                        "Failed to resolve copybook URI: {}", copybookName.getDisplayName(), ex);
                    return null;
                  });

      String resolvedUri = futureUri.join();

      if (resolvedUri == null) {
        Optional<CopybookModel> predefineCopybook = tryResolvePredefinedCopybook(copybookName);
        return ResultWithErrors.of(
            predefineCopybook.orElse(getDefaultCopybook(copybookName, programUri)));
      }
      CompletableFuture<String> fileContentFuture =
          futureUri
              .thenCompose(
                  uri ->
                      uri != null
                          ? clientProvider.get().getFileContent(uri)
                          : CompletableFuture.completedFuture(null))
              .exceptionally(
                  ex -> {
                    LOG.warn(
                        "Failed to fetch content for copybook: {}",
                        copybookName.getDisplayName(),
                        ex);
                    return null;
                  });

      String fileContent = fileContentFuture.join();

      if (fileContent == null) {
        return ResultWithErrors.of(getDefaultCopybook(copybookName, programUri));
      }

      CopybookModel dirtyCopybook =
          new CopybookModel(
              copybookName.toCopybookId(programUri), copybookName, resolvedUri, fileContent);
      ResultWithErrors<CopybookModel> copybookModelResultWithErrors =
          cleanupCopybook(dirtyCopybook, preprocessor);
      copybookUsage
          .computeIfAbsent(programUri, k -> new HashSet<>())
          .add(copybookModelResultWithErrors.getResult());
      preprocessCopybookErrors.put(
          dirtyCopybook.getUri(), copybookModelResultWithErrors.getErrors());
      return copybookModelResultWithErrors;

    } catch (CancellationException | CompletionException e) {
      LOG.warn("Copybook resolution cancelled: {}", copybookName.getDisplayName(), e);
      return ResultWithErrors.of(getDefaultCopybook(copybookName, programUri));
    }
  }

  @Override
  public void invalidateCache(boolean onlyNonImplicit) {}

  @Override
  public void store(CopybookModel copybookModel) {}

  @Override
  public void store(CopybookModel copybookModel, CleanerPreprocessor preprocessor) {}

  @Override
  public void sendCopybookDownloadRequest(
      String documentUri, Collection<String> copybookUris, CopybookProcessingMode processingMode) {}
}
