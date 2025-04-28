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
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.common.copybook.CopybookId;
import org.eclipse.lsp.cobol.common.copybook.CopybookName;
import org.eclipse.lsp.cobol.common.io.CachedIOService;
import org.eclipse.lsp.cobol.common.io.ResolveCopybookUri;
import org.eclipse.lsp.cobol.lsp.jrpc.CobolLanguageClient;

/**
 * Resolves a copybook uri for a COBOL document and caches the result to reduce client server
 * communication
 */
@Singleton
@Slf4j
public class CacheResolveCopybookUri
    implements ResolveCopybookUri, CachedIOService<CopybookId, String> {
  private final Provider<CobolLanguageClient> clientProvider;
  private final Map<CopybookId, String> cache = new ConcurrentHashMap<>();

  @Inject
  public CacheResolveCopybookUri(Provider<CobolLanguageClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  /**
   * Resolves a copybook uri for a COBOL document and caches the result to reduce client server
   * communication
   *
   * @param cobolFileUri
   * @param copybookName
   * @param dialectType
   * @return
   */
  @Override
  public String resolveCopybookUri(
      String cobolFileUri, CopybookName copybookName, String dialectType) {
    return cache.computeIfAbsent(
        copybookName.toCopybookId(cobolFileUri),
        (id) -> {
          try {
            CompletableFuture<String> futureUri =
                this.clientProvider
                    .get()
                    .resolveCopybook(cobolFileUri, copybookName.getDisplayName(), dialectType)
                    .exceptionally(
                        ex -> {
                          LOG.warn(
                              "Failed to resolve copybook URI: {} for program {}",
                              copybookName,
                              cobolFileUri,
                              ex);
                          return null;
                        });

            return futureUri.join();
          } catch (CancellationException | CompletionException e) {
            LOG.warn("Copybook resolution cancelled: {}", copybookName, e);
            return null;
          }
        });
  }

  /**
   * Invalidates cache for a specific {@link CopybookId}, or clear entire cache if passed id is null
   *
   * @param copybookId
   */
  @Override
  public void invalidate(CopybookId copybookId) {
    if (copybookId == null) {
      cache.clear();
      return;
    }
    cache.remove(copybookId);
  }

  /** */
  @Override
  public void invalidateAll() {
    cache.clear();
  }

  /**
   * @param key
   * @param value
   */
  @Override
  public void store(CopybookId key, String value) {
    cache.put(key, value);
  }
}
