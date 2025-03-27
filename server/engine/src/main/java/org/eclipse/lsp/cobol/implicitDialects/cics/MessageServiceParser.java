/*
 * Copyright (c) 2023 Broadcom.
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
 *    DAF Trucks NV – implementation of DaCo COBOL statements
 *    and DAF development standards
 *
 */
package org.eclipse.lsp.cobol.implicitDialects.cics;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp.cobol.common.ErrorListenerForErrorCode;
import org.eclipse.lsp.cobol.common.message.MessageServiceProvider;
import org.eclipse.lsp.cobol.common.utils.BasicUtils;

import java.util.List;

/**
 * Provide the support of message externalization for Parser.
 *
 * <p>Usage: options { superClass = MessageServiceParser;}
 */
public abstract class MessageServiceParser extends Parser {

  /**
   * @param input {@link TokenStream}
   */
  MessageServiceParser(TokenStream input) {
    super(input);
  }

  /**
   * Extend the functionality of {@link org.eclipse.lsp.cobol.common.message.MessageService} for
   * {@link CICSParser}
   *
   * @param messageId Unique ID for each message in externalized message file.
   * @param parameters Arguments referenced by the format specifiers in the format string in
   *     externalized message file.
   */
  public void notifyError(String messageId, String... parameters) {
    Pair<String, String> message = getMessageForParser(messageId, parameters);
    notifyListeners(message);
  }

  private void notifyListeners(Pair<String, String> message) {
    notifyErrorListeners(getCurrentToken(), message, null);
  }

  /**
   * Notifies the error listeners about an exception occurred during parsing
   * @param offendingToken offending token
   * @param errorPair {@link Pair} of error code mapped to the corresponding error message/suggestion
   * @param exception {@link Exception} encountered during parsing
   */
  public void notifyErrorListeners(
          Token offendingToken, Pair<String, String> errorPair, RecognitionException exception) {
    _syntaxErrors++;
    int line = -1;
    int charPositionInLine = -1;
    line = offendingToken.getLine();
    charPositionInLine = offendingToken.getCharPositionInLine();

    ANTLRErrorListener listener = getErrorListenerDispatch();
    List<? extends ANTLRErrorListener> errorListeners = getErrorListeners();
    ErrorListenerForErrorCode errorCodeErrorListener =
            BasicUtils.getFirstInstanceOfType(errorListeners, ErrorListenerForErrorCode.class);
    if (errorCodeErrorListener != null) {
      errorCodeErrorListener.syntaxError(offendingToken, line, charPositionInLine, errorPair, exception);
    } else {
      listener.syntaxError(this, offendingToken, line, charPositionInLine, errorPair.getRight(), exception);
    }
  }

  private Pair<String, String> getMessageForParser(String messageKey, String... parameters) {
    return ((MessageServiceProvider) this.getErrorHandler())
        .getMessageService()
        .getMessage(messageKey, (Object[]) parameters);
  }
}
