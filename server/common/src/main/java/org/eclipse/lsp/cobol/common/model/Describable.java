/*
 * Copyright (c) 2021 Broadcom.
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
package org.eclipse.lsp.cobol.common.model;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.eclipse.lsp4j.MarkupContent;

/** The interface represents structure that can show formatted line to the user. */
public interface Describable {
  /**
   * Get user friendly node description.
   *
   * @param prefix prefix for the display string
   * @return the {@link MarkupContent} with description.
   */
  List<MarkupContent> getFormattedDisplayString(String prefix);

  /**
   * Get text from the source code for a specific node.
   *
   * @param contentExtractor {@link Function} which takes uri and returns a {@link Function} which
   *     takes the {@link Locality} and gives List of {@link MarkupContent}
   * @return the {@link MarkupContent} with description.
   */
  default List<MarkupContent> getSourceDisplayString(
      Function<String, Function<Locality, List<MarkupContent>>> contentExtractor, String prefix) {
    return Collections.emptyList();
  }
}
