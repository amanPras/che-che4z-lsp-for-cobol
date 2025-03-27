/*
 * Copyright (c) 2020 Broadcom.
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
package org.eclipse.lsp.cobol.core.visitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
import org.eclipse.lsp.cobol.common.mapping.ExtendedDocument;
import org.eclipse.lsp.cobol.common.model.Locality;
import org.eclipse.lsp.cobol.parser.WarningRecognitionException;
import org.eclipse.lsp.cobol.core.semantics.CopybooksRepository;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/** This error listener registers syntax errors found by the COBOL parser. */
@Slf4j
@RequiredArgsConstructor
public class ParserListener extends BaseErrorListener implements ErrorListenerForErrorCode {

  private final ExtendedDocument extendedDocument;
  private final CopybooksRepository copybooksRepository;
  @Getter private final List<SyntaxError> errors = new ArrayList<>();

  @Override
  public void syntaxError(
          Recognizer<?, ?> recognizer,
          Object offendingSymbol,
          int line,
          int charPositionInLine,
          String msg,
          RecognitionException e) {
    buildAndAddError(line, charPositionInLine, offendingSymbol, msg, e, builder -> builder.suggestionString(msg));
  }

  @Override
  public void syntaxError(Object offendingSymbol, int line, int charPositionInLine, Pair<String, String> errorPair, Exception e) {
    buildAndAddError(line, charPositionInLine, offendingSymbol, errorPair.getValue(), e, builder -> builder.suggestion(errorPair));
  }

  private static ErrorSeverity getErrorSeverity(Exception e) {
    ErrorSeverity severity = ErrorSeverity.ERROR;
    if (e instanceof WarningRecognitionException) {
      severity = ErrorSeverity.WARNING;
    }
    return severity;
  }

  private int getOffendingSymbolSize(Object offendingSymbol) {
    return Optional.ofNullable(offendingSymbol)
        .filter(t -> t instanceof CommonToken)
        .map(CommonToken.class::cast)
        .map(token -> token.getStopIndex() - token.getStartIndex() + 1)
        .orElse(0);
  }

  private void buildAndAddError(int line, int charPositionInLine, Object offendingSymbol, String errorMessage, Exception e,
                                Consumer<SyntaxError.SyntaxErrorBuilder> suggestionConsumer) {
    Range range = new Range(
            new Position(line - 1, charPositionInLine), new Position(line - 1,
            charPositionInLine + getOffendingSymbolSize(offendingSymbol)));

    if ("token recognition error at: '\\n'".equals(errorMessage)) {
      return;
    }
    Location location = extendedDocument.mapLocation(range);
    SyntaxError syntaxError = buildSyntaxError(location, e, suggestionConsumer);
    LOG.debug("Syntax error by ParserListener " + syntaxError.toString());
    errors.add(syntaxError);
  }

  private SyntaxError buildSyntaxError(Location location, Exception e, Consumer<SyntaxError.SyntaxErrorBuilder> suggestionConsumer) {
    SyntaxError.SyntaxErrorBuilder errorBuilder =
            SyntaxError.syntaxError()
                    .errorSource(ErrorSource.PARSING)
                    .location(
                            Locality.builder()
                                    .uri(location.getUri())
                                    .range(location.getRange())
                                    .copybookId(copybooksRepository.getCopybookIdByUri(location.getUri()))
                                    .build()
                                    .toOriginalLocation())
                    .severity(getErrorSeverity(e));
    suggestionConsumer.accept(errorBuilder);
      return errorBuilder.build();
  }
}
