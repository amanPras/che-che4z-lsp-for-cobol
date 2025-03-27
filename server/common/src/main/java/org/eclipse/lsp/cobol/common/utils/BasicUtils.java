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
package org.eclipse.lsp.cobol.common.utils;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class BasicUtils {
  /**
   * Searches a list for the first occurrence of an object of a specified type.
   *
   * @param <T> The type of object to search for.
   * @param list The list to search through. Can contain objects of any type.
   * @param type The Class object representing the type to search for.
   * @return The first object in the list that is an instance of the specified type, or null if no
   *     such object is found.
   * @throws NullPointerException if either the list or type parameter is null.
   *     <p>Example usage: List<Object> mixedList = Arrays.asList("String", 1, 2.0, new
   *     ArrayList<>()); ArrayList<?> result = getFirstInstanceOfType(mixedList, ArrayList.class);
   */
  public  <T> T getFirstInstanceOfType(List<?> list, Class<T> type) {
    for (Object obj : list) {
      if (type.isInstance(obj)) {
        return type.cast(obj);
      }
    }
    return null;
  }
}
