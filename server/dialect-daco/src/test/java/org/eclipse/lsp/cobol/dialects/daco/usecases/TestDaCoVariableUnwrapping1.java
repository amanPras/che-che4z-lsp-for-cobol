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
package org.eclipse.lsp.cobol.dialects.daco.usecases;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.lsp.cobol.dialects.daco.DaCoDialect;
import org.eclipse.lsp.cobol.dialects.daco.utils.DialectConfigs;
import org.eclipse.lsp.cobol.dialects.idms.IdmsDialect;
import org.eclipse.lsp.cobol.test.CobolText;
import org.eclipse.lsp.cobol.test.engine.UseCaseEngine;
import org.junit.jupiter.api.Test;

/** Test for DaCo variable unwrapping */
class TestDaCoVariableUnwrapping1 {
  private static final String TEXT =
      "       IDENTIFICATION DIVISION.\n"
          + "       PROGRAM-ID.    TEST1.\n"
          + "       ENVIRONMENT  DIVISION.\n"
          + "       IDMS-CONTROL SECTION. PROTOCOL. IDMS-RECORDS MANUAL.\n"
          + "       DATA   DIVISION.\n"
          + "       WORKING-STORAGE SECTION.\n"
          + "       01 {$*ROOT}.\n"
          + "              05 COPY MAID {~CPB1!DaCo} KMK.\n"
          + "       01  COPY IDMS {~CPB2-XTT!IDMS}.\n"
          + "       01  {$*MAIN} PIC X(8).";
  private static final String COPYBOOK1 =
      "       01  {$*L1-X}.\n"
          + "           03 {$*L3-B}              PIC S9(9)   VALUE ZERO  COMP.\n";
  private static final String COPYBOOK2 = "       01 {$*KMKKLS-XTT}  PIC X(3)    VALUE SPACE.";

  @Test
  void test() {
    UseCaseEngine.runTest(
        TEXT,
        ImmutableList.of(
            new CobolText("CPB1", DaCoDialect.NAME, COPYBOOK1),
            new CobolText("CPB2-XTT", IdmsDialect.NAME, COPYBOOK2)),
        ImmutableMap.of(),
        ImmutableList.of(),
        DialectConfigs.getDaCoAnalysisConfig());
  }
}
