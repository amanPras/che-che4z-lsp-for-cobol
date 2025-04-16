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
package org.eclipse.lsp.cobol.common.io;

/** Basic for contact for any cached service */
public interface CachedIOService<K, V> {
  /** clears cache */
  void invalidateAll();

  /**
   * Invalidate a specific element from cache
   *
   * @param id
   */
  void invalidate(K id);

  /**
   * Store the key value pair in cache
   *
   * @param key
   * @param value
   */
  void store(K key, V value);
}
