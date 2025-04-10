/*
 * Copyright (c) 2020 Broadcom.
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

import static java.util.stream.Collectors.toList;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.common.CleanerPreprocessor;
import org.eclipse.lsp.cobol.common.ResultWithErrors;
import org.eclipse.lsp.cobol.common.copybook.*;
import org.eclipse.lsp.cobol.common.error.SyntaxError;
import org.eclipse.lsp.cobol.common.mapping.ExtendedText;
import org.eclipse.lsp.cobol.common.mapping.OriginalLocation;
import org.eclipse.lsp.cobol.core.semantics.CopybooksRepository;
import org.eclipse.lsp.cobol.lsp.jrpc.CobolLanguageClient;

/**
 * This service processes copybook requests and returns content by its name. The service also caches
 * copybook to reduce filesystem load.
 */
@Slf4j
@Singleton
@SuppressWarnings("UnstableApiUsage")
public class CopybookServiceImpl implements CopybookService {

  private static final String COBOL = "COBOL";
  private final Map<String, List<SyntaxError>> preprocessCopybookErrors = new ConcurrentHashMap<>();
  private final Map<String, Set<CopybookModel>> copybookUsage = new ConcurrentHashMap<>();
  private final Provider<CobolLanguageClient> clientProvider;
  private final Map<String, Set<CopybookName>> copybooksForDownloading =
      new ConcurrentHashMap<>(8, 0.9f, 1);

  private final Map<CopybookName, CopybookModel> preDefinedCopybookCache = new HashMap<>();

  @Inject
  public CopybookServiceImpl(Provider<CobolLanguageClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  /**
   * Retrieve and return a CopybookModel by its name and preprocessed errors for the Retrieved
   * copybook wrapped inside {@link ResultWithErrors}. Copybook may be cached to limit interactions
   * with the file system.
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
          clientProvider
              .get()
              .resolveCopybookUri(
                  programUri,
                  copybookName.getDisplayName(),
                  Optional.ofNullable(copybookName.getDialectType()).orElse(COBOL))
              .exceptionally(
                  ex -> {
                    LOG.warn(
                        "Failed to resolve copybook URI: {}", copybookName.getDisplayName(), ex);
                    return null;
                  });

      String resolvedUri = futureUri.join();

      if (resolvedUri == null) {
        return getPredefinedCopybooks(copybookName, programUri);
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

  private static CopybookModel getDefaultCopybook(CopybookName copybookName, String programUri) {
    return new CopybookModel(copybookName.toCopybookId(programUri), copybookName, null, null);
  }

  private ResultWithErrors<CopybookModel> getPredefinedCopybooks(
      @NonNull CopybookName copybookName, @NonNull String programUri) {
    return ResultWithErrors.of(
        preDefinedCopybookCache.getOrDefault(
            copybookName, getDefaultCopybook(copybookName, programUri)));
  }

  /**
   * Store the copybookModel in cache. Copybook depends on a document from where it is imported.
   *
   * @param model the copybook model
   */
  public void storePredefinedCopybooks(CopybookModel model) {
    preDefinedCopybookCache.put(model.getCopybookName(), model);
  }

  private ResultWithErrors<CopybookModel> cleanupCopybook(
      CopybookModel dirtyCopybook, CleanerPreprocessor preprocessor) {
    ResultWithErrors<ExtendedText> textTransformationsResultWithErrors =
        preprocessor.cleanUpCode(dirtyCopybook.getUri(), dirtyCopybook.getContent());
    String cleanText =
        CharMatcher.whitespace()
            .trimTrailingFrom(textTransformationsResultWithErrors.getResult().toString());
    CopybookModel copybookModel =
        new CopybookModel(
            dirtyCopybook.getCopybookId(),
            dirtyCopybook.getCopybookName(),
            dirtyCopybook.getUri(),
            cleanText);
    return new ResultWithErrors<>(
        copybookModel,
        adjustErrorLocation(dirtyCopybook, textTransformationsResultWithErrors.getErrors()));
  }

  private List<SyntaxError> adjustErrorLocation(
      CopybookModel dirtyCopybook, List<SyntaxError> originalErrors) {
    return originalErrors.stream()
        .map(
            error ->
                error.toBuilder().location(getErrorOriginalLocation(dirtyCopybook, error)).build())
        .collect(toList());
  }

  private OriginalLocation getErrorOriginalLocation(
      CopybookModel dirtyCopybook, SyntaxError error) {
    return new OriginalLocation(
        Optional.ofNullable(error.getLocation()).map(OriginalLocation::getLocation).orElse(null),
        CopybooksRepository.toId(
            dirtyCopybook.getCopybookName().getQualifiedName(),
            dirtyCopybook.getCopybookName().getDialectType(),
            dirtyCopybook.getUri()));
  }

  /**
   * Get the list of copybook used by a document
   *
   * @param documentUri current document uri.
   * @return Set of all the {@link CopybookModel} used by the passed document
   */
  public Set<CopybookModel> getCopybookUsage(String documentUri) {
    return Collections.unmodifiableSet(copybookUsage.getOrDefault(documentUri, ImmutableSet.of()));
  }
}
