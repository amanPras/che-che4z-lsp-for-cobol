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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.common.CleanerPreprocessor;
import org.eclipse.lsp.cobol.common.ResultWithErrors;
import org.eclipse.lsp.cobol.common.copybook.*;
import org.eclipse.lsp.cobol.common.error.SyntaxError;
import org.eclipse.lsp.cobol.common.utils.ThreadInterruptionUtil;
import org.eclipse.lsp.cobol.service.io.FileDownload;
import org.eclipse.lsp.cobol.service.io.ResolveCopybookUri;
import org.eclipse.lsp.cobol.service.io.ResolveFileContent;

/**
 * This service processes copybook requests and returns content by its name. The service also caches
 * copybook to reduce filesystem load.
 */
@Slf4j
@Singleton
@SuppressWarnings("UnstableApiUsage")
public class CopybookServiceImpl implements CopybookService {

  protected final Map<String, List<SyntaxError>> preprocessCopybookErrors =
      new ConcurrentHashMap<>();
  protected final Map<String, Set<CopybookModel>> copybookUsage = new ConcurrentHashMap<>();
  private final ResolveCopybookUri resolveCopybookUri;
  private final ResolveFileContent resolveFileContent;
  private final FileDownload fileDownloadService;
  private static final String COBOL = "COBOL";

  @Getter
  @Named("predefinedCopybook")
  private final CopybookService predefinedCopybookService;

  private final Map<String, Set<CopybookName>> copybooksForDownloading =
      new ConcurrentHashMap<>(8, 0.9f, 1);

  @Inject
  public CopybookServiceImpl(
      ResolveCopybookUri resolveCopybookUri,
      ResolveFileContent resolveFileContent,
      FileDownload fileDownloadService,
      CopybookService predefinedCopybookService) {
    this.resolveCopybookUri = resolveCopybookUri;
    this.resolveFileContent = resolveFileContent;
    this.fileDownloadService = fileDownloadService;
    this.predefinedCopybookService = predefinedCopybookService;
  }

  @Override
  public void invalidateCache(boolean onlyNonImplicit) {
    LOG.debug("Copybooks for downloading: {}", copybooksForDownloading);
    LOG.debug("Cache invalidated");
    resolveCopybookUri.invalidateCache(null); // TODO Added
    copybookUsage.clear();
    copybooksForDownloading.clear();
  }

  /**
   * Removes cache for the passed {@link CopybookId}
   *
   * @param copybookId is a copybook identifier
   */
  public void invalidateCache(CopybookId copybookId) {
    resolveCopybookUri.invalidateCache(copybookId);
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
   * @param programDocumentUri - the currently processing program document
   * @param documentUri - the currently processing document that contains the copy statement
   * @param preprocessor - Cleanup preprocessor that will be used for new copybooks or null
   * @return a CopybookModel wrapped inside {@link ResultWithErrors} which contains copybook name,
   *     its URI and the content
   */
  public ResultWithErrors<CopybookModel> resolve(
      @NonNull CopybookId copybookId,
      @NonNull CopybookName copybookName,
      @NonNull String programDocumentUri,
      @NonNull String documentUri,
      CleanerPreprocessor preprocessor) {
    try {
      ThreadInterruptionUtil.checkThreadInterrupted();

      String copybookUri =
          resolveCopybookUri.resolveCopybookUri(
              programDocumentUri,
              copybookName,
              Optional.ofNullable(copybookName.getDialectType()).orElse(COBOL));
      if (copybookUri == null) {
        ResultWithErrors<CopybookModel> predefinedCopybook =
            predefinedCopybookService.resolve(
                copybookName.toCopybookId(programDocumentUri),
                copybookName,
                programDocumentUri,
                programDocumentUri,
                preprocessor);
        if (predefinedCopybook.getResult().getContent() == null) {
          return ResultWithErrors.of(registerForDownloading(copybookName, programDocumentUri));
        }
        return ResultWithErrors.of(predefinedCopybook.getResult());
      }
      String fileContent = resolveFileContent.getFileContent(copybookUri).join();

      if (fileContent == null) {
        return ResultWithErrors.of(
            CopybookUtility.getDefaultCopybook(copybookName, programDocumentUri));
      }

      CopybookModel dirtyCopybook =
          new CopybookModel(
              copybookName.toCopybookId(programDocumentUri),
              copybookName,
              copybookUri,
              fileContent);
      ResultWithErrors<CopybookModel> copybookModelResultWithErrors =
          CopybookUtility.cleanupCopybook(dirtyCopybook, preprocessor);
      copybookUsage
          .computeIfAbsent(programDocumentUri, k -> new HashSet<>())
          .add(copybookModelResultWithErrors.getResult());
      preprocessCopybookErrors.put(
          dirtyCopybook.getUri(), copybookModelResultWithErrors.getErrors());
      return copybookModelResultWithErrors;
    } catch (UncheckedExecutionException | ExecutionError e) {
      LOG.error("Can't resolve copybook '{}'.", copybookName, e);
      return new ResultWithErrors<>(
          CopybookUtility.getDefaultCopybook(copybookName, programDocumentUri),
          Collections.emptyList());
    }
  }

  @Override
  public void store(CopybookModel copybookModel) {}

  @Override
  public void store(CopybookModel copybookModel, CleanerPreprocessor preprocessor) {
    //    if (preprocessor != null) {
    //      ResultWithErrors<CopybookModel> processedCopybook =
    //          CopybookUtility.cleanupCopybook(copybookModel, preprocessor);
    //      copybookModel = processedCopybook.getResult();
    //      preprocessCopybookErrors.put(copybookModel.getUri(), processedCopybook.getErrors());
    //    }
    //    store(copybookModel);
  }

  private CopybookModel registerForDownloading(CopybookName copybookName, String programUri) {
    String cobolFileName = CopybookUtility.getNameFromURI(programUri);
    LOG.debug("Registering copybook {} of {} for further downloading", copybookName, cobolFileName);
    Optional.ofNullable(cobolFileName)
        .map(
            name ->
                copybooksForDownloading.computeIfAbsent(name, s -> ConcurrentHashMap.newKeySet()))
        .ifPresent(it -> it.add(copybookName));
    return new CopybookModel(copybookName.toCopybookId(programUri), copybookName, null, null);
  }

  @Override
  public void sendCopybookDownloadRequest(
      String documentUri, Collection<String> copybookUris, CopybookProcessingMode processingMode) {
    LOG.debug("Copybooks expecting downloading: {}", copybooksForDownloading);
    Set<String> uris = new HashSet<>(copybookUris);
    uris.add(documentUri);

    if (processingMode.download) {
      List<CopyBookDTO> copybooksToDownload =
          uris.stream()
              .map(CopybookUtility::getNameFromURI)
              .map(copybooksForDownloading::remove)
              .filter(Objects::nonNull)
              .flatMap(Set::stream)
              .map(CopyBookDTO::new)
              .collect(toList());
      LOG.debug("Copybooks to download: {}", copybooksToDownload);
      if (!copybooksToDownload.isEmpty()) {
        fileDownloadService.downloadCopybooks(
            documentUri, copybooksToDownload, !processingMode.userInteraction);
      }
    }
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

  @VisibleForTesting
  Map<String, Set<CopybookName>> getCopybooksForDownloading() {
    return ImmutableMap.copyOf(copybooksForDownloading);
  }
}
