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
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp.cobol.common.file.FileSystemService;
import org.eclipse.lsp.cobol.common.io.CachedIOService;
import org.eclipse.lsp.cobol.common.io.ResolveFileContent;

/** Interface to resolve content of a URI at the server side */
public class DiskBasedFileContent implements ResolveFileContent, CachedIOService<String, String> {
  private final FileSystemService fileSystemService;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  @Inject
  public DiskBasedFileContent(FileSystemService fileSystemService) {
    this.fileSystemService = fileSystemService;
  }

  /**
   * @param uri
   * @return
   */
  @Override
  public CompletableFuture<String> getFileContent(String uri) {
    return CompletableFuture.completedFuture(
        cache.computeIfAbsent(
            uri,
            id -> {
              Path file = fileSystemService.getPathFromURI(uri);
              return fileSystemService.fileExists(file)
                  ? fileSystemService.getContentByPath(Objects.requireNonNull(file))
                  : null;
            }));
  }

  /** */
  @Override
  public void invalidateAll() {
    cache.clear();
  }

  /**
   * @param id
   */
  @Override
  public void invalidate(String id) {
    cache.remove(id);
  }

  /**
   * @param key
   * @param value
   */
  @Override
  public void store(String key, String value) {
    cache.put(key, value);
  }
}
