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
package org.eclipse.lsp.cobol.lsp.handlers.workspace;

import com.google.inject.Inject;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.common.UserInterruptException;
import org.eclipse.lsp.cobol.lsp.DisposableLSPStateService;
import org.eclipse.lsp.cobol.lsp.SourceUnitGraph;
import org.eclipse.lsp.cobol.lsp.analysis.AsyncAnalysisService;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;

/** Handles LSP DidChangeWatchedFiles events for COBOL language server */
@Slf4j
public class DidChangeWatchedFilesHandler {
  private static final String FILE_SCHEME = "file";
  private static final String GIT_SCHEME_PREFIX = "git:";
  private static final String VSCODE_SCHEME_PREFIX = "vscode:";
  public static final String VSCODE_USERDATA_SCHEME_PREFIX = "vscode-userdata:";

  private final DisposableLSPStateService lspStateService;
  private final SourceUnitGraph sourceUnitGraph;
  private final AsyncAnalysisService asyncAnalysisService;

  @Inject
  public DidChangeWatchedFilesHandler(
      DisposableLSPStateService lspStateService,
      SourceUnitGraph sourceUnitGraph,
      AsyncAnalysisService asyncAnalysisService) {
    this.lspStateService = lspStateService;
    this.sourceUnitGraph = sourceUnitGraph;
    this.asyncAnalysisService = asyncAnalysisService;
  }

  /**
   * Processes file system change notifications
   *
   * @param params LSP notification parameters containing file changes
   */
  public void didChangeWatchedFiles(@NonNull DidChangeWatchedFilesParams params) {
    if (lspStateService.isServerShutdown()) return;

    Set<FileEvent> relevantChanges = filterRelevantChanges(params.getChanges());
    if (relevantChanges.isEmpty()) return;

    logFileChanges(relevantChanges);
    processFileEvents(relevantChanges);
  }

  private Set<FileEvent> filterRelevantChanges(List<FileEvent> changes) {
    return changes.stream()
        .filter(this::isRelevantFileChange)
        .collect(Collectors.toCollection(HashSet::new));
  }

  private boolean isRelevantFileChange(FileEvent change) {
    return !isGitMetadataFile(change.getUri())
        && !change.getUri().startsWith(GIT_SCHEME_PREFIX)
        && !isVscodeSchemaFile(change.getUri());
  }

  private boolean isGitMetadataFile(String uri) {
    return uri.startsWith("file:") && uri.contains("/.git/");
  }

  private boolean isVscodeSchemaFile(String uri) {
    return uri.startsWith(VSCODE_SCHEME_PREFIX) || uri.startsWith(VSCODE_USERDATA_SCHEME_PREFIX);
  }

  private void logFileChanges(Set<FileEvent> changes) {
    LOG.info(
        "[File change event]: {}",
        changes.stream().map(FileEvent::getUri).collect(Collectors.joining(", ")));
  }

  private void processFileEvents(Set<FileEvent> changes) {
    changes.forEach(
        event -> {
          URI uri = URI.create(event.getUri());
          if (FILE_SCHEME.equals(uri.getScheme())) {
            handleLocalFileSystemChange(event, uri);
          } else {
            triggerAnalysisForUri(event.getUri());
          }
        });
  }

  private void handleLocalFileSystemChange(FileEvent event, URI uri) {
    LOG.debug("[File change event] Processing: {}", uri);

    Path path = getEffectivePath(event, uri);
    String fileUri = path.toUri().toString();

    if (isOpenInEditor(fileUri)) {
      LOG.debug("Ignoring change for open file: {}", fileUri);
      return;
    }

    if (Files.isDirectory(path)) {
      handleDirectoryChange(path);
    } else {
      triggerAnalysisForUri(fileUri);
    }
  }

  private Path getEffectivePath(FileEvent event, URI uri) {
    Path path = Paths.get(uri);
    return event.getType() == FileChangeType.Deleted ? path.getParent() : path;
  }

  private boolean isOpenInEditor(String uri) {
    return sourceUnitGraph.isFileOpened(uri);
  }

  private void handleDirectoryChange(Path directory) {
    LOG.debug("Processing directory change: {}", directory);
    analyzeAllOpenedDocuments();
  }

  private void triggerAnalysisForUri(String uri) {
    List<String> associatedUris = sourceUnitGraph.getAllAssociatedFilesForACopybook(uri);

    if (associatedUris.isEmpty()) {
      analyzeAllOpenedDocuments();
      return;
    }

    updateFileContentAndTriggerAnalysis(uri, associatedUris);
  }

  private void updateFileContentAndTriggerAnalysis(String uri, List<String> associatedUris) {
    sourceUnitGraph.updateContent(uri);
    String fileContent = sourceUnitGraph.getContent(uri);

    if (!sourceUnitGraph.isFileOpened(uri)) {
      LOG.debug("Triggering analysis for: {}", String.join(", ", associatedUris));
      asyncAnalysisService.reanalyseCopybooksAssociatedPrograms(
          associatedUris, uri, fileContent, SourceUnitGraph.EventSource.FILE_SYSTEM);
    }
  }

  private void analyzeAllOpenedDocuments() {
    try {
      asyncAnalysisService.reanalyseOpenedPrograms(SourceUnitGraph.EventSource.FILE_SYSTEM);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UserInterruptException("Analysis interrupted", e);
    }
  }
}
