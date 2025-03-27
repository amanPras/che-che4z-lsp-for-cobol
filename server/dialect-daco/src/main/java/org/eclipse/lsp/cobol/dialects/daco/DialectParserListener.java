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
package org.eclipse.lsp.cobol.dialects.daco;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp.cobol.common.ErrorListenerForErrorCode;
import org.eclipse.lsp.cobol.common.error.ErrorSeverity;
import org.eclipse.lsp.cobol.common.error.ErrorSource;
import org.eclipse.lsp.cobol.common.error.SyntaxError;
import org.eclipse.lsp.cobol.common.model.Locality;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** This error listener registers syntax errors found by dialect parser. */
@Slf4j
class DialectParserListener extends BaseErrorListener implements ErrorListenerForErrorCode {
  private final String uri;

  @Getter private final List<SyntaxError> errors = new ArrayList<>();

  DialectParserListener(String uri) {
    this.uri = uri;
  }

  @Override
  public void syntaxError(
      Recognizer<?, ?> recognizer,
      Object offendingSymbol,
      int line,
      int charPositionInLine,
      String msg,
      RecognitionException e) {
    buildAndAddError(line, charPositionInLine, offendingSymbol, msg, syntaxErrorBuilder -> syntaxErrorBuilder.suggestionString(msg));
  }

  @Override
  public void syntaxError(Object offendingSymbol, int line, int charPositionInLine, Pair<String, String> errorPair, Exception e) {
    buildAndAddError(line, charPositionInLine, offendingSymbol, errorPair.getValue(), syntaxErrorBuilder -> syntaxErrorBuilder.suggestion(errorPair));
  }

  private void buildAndAddError(int line, int charPositionInLine, Object offendingSymbol, String errorMessage,
                                Consumer<SyntaxError.SyntaxErrorBuilder> suggestionConsumer) {
    int errorLine = line - 1;
    SyntaxError.SyntaxErrorBuilder builder =
        SyntaxError.syntaxError()
            .errorSource(ErrorSource.DIALECT)
            .location(
                Locality.builder()
                    .uri(uri)
                    .range(
                        new Range(
                            new Position(errorLine, charPositionInLine),
                            new Position(
                                errorLine,
                                charPositionInLine + getOffendingSymbolSize(offendingSymbol))))
                    .build()
                    .toOriginalLocation())
            .severity(getSeverity(errorMessage));
    suggestionConsumer.accept(builder);
    SyntaxError error = builder.build();
    LOG.debug("Syntax error by DialectParserListener " + error.toString());
    errors.add(error);
  }

  private static ErrorSeverity getSeverity(String msg) {
    // Message with id "parsers.intRangeValue" should be treated as warning
    return "Allowed range is 80 to 200".equals(msg) ? ErrorSeverity.WARNING : ErrorSeverity.ERROR;
  }

  private int getOffendingSymbolSize(Object offendingSymbol) {
    return Optional.ofNullable(offendingSymbol)
        .filter(t -> t instanceof CommonToken)
        .map(CommonToken.class::cast)
        .map(token -> token.getStopIndex() - token.getStartIndex() + 1)
        .orElse(0);
  }
}
