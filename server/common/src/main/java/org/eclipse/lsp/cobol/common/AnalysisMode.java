/*
 * Copyright (c) 2026 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom - initial API and implementation
 *
 */

package org.eclipse.lsp.cobol.common;

import java.util.Objects;

public final class AnalysisMode {
  public Mode mode;

  public AnalysisMode() {
    this.mode = Mode.ADVANCED;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof AnalysisMode && Objects.equals(this.mode, ((AnalysisMode) obj).mode);
  }

  @Override
  public int hashCode() {
    return mode == null ? 0 : mode.hashCode();
  }

  public enum Mode {
    ADVANCED,
    BASIC
  }
}
