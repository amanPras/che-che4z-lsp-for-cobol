/*
 * Copyright (c) 2022 Broadcom.
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

package org.eclipse.lsp.cobol.common.error;

import lombok.Getter;

/**
 * This enum represents the source where error is generated during parsing and analysis.
 */
public enum ErrorSource {
  PARSING("(parsing)", ErrorLevel.FATAL),
  PREPROCESSING("(preprocessing)", ErrorLevel.FATAL),
  DIALECT("(dialect)"),
  EXTENDED_DOCUMENT("(extended document)", ErrorLevel.FATAL),
  COPYBOOK("(copybook)"),
  WORKSPACE_SETTINGS("(workspace setting)");

  private static final String COBOL_LANG_SUPPORT_LABEL = "COBOL Language Support";

  private String label;
  @Getter private ErrorLevel level;

  ErrorSource(String label) {
    this.label = label;
    this.level = ErrorLevel.ERROR;
  }

  ErrorSource(String label, ErrorLevel level) {
    this.label = label;
    this.level = level;
  }

  public ErrorSource updateLevel(ErrorLevel level) {
    this.level = level;
    return this;
  }

  public String getText() {
    return COBOL_LANG_SUPPORT_LABEL + " " + label;
  }
}
