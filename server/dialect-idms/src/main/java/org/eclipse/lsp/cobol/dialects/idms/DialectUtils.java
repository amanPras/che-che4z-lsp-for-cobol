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
package org.eclipse.lsp.cobol.dialects.idms;

import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/** Dialect utils class */
@UtilityClass
class DialectUtils {
  /**
   * Construct the range from ANTLR context
   *
   * @param ctx the ANTLR context
   * @return the range
   */
  public Range constructRange(ParserRuleContext ctx) {
    return new Range(
        new Position(ctx.getStart().getLine() - 1, ctx.getStart().getCharPositionInLine()),
        new Position(
            ctx.getStop().getLine() - 1,
            ctx.getStop().getCharPositionInLine()
                + ctx.getStop().getStopIndex()
                - ctx.getStop().getStartIndex()
                + 1));
  }

  /**
   * Construct the range from ANTLR token
   *
   * @param token the ANTLR token
   * @return the range
   */
  public static Range constructRange(Token token) {
    int line = token.getLine() - 1;
    int position = token.getCharPositionInLine();

    Position start = new Position(line, position);
    boolean zeroSize = token.getStopIndex() == -1 || token.getStopIndex() < token.getStartIndex();
    Position end =
        zeroSize
            ? start
            : new Position(line, position + token.getStopIndex() - token.getStartIndex() + 1);
    return new Range(start, end);
  }
}
