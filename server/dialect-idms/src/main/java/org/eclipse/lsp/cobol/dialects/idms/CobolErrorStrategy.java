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
package org.eclipse.lsp.cobol.dialects.idms;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.message.MessageServiceProvider;

/**
 * This implementation of the error strategy customizes error messages that are extracted from the
 * parsing exceptions
 */
@Slf4j
// for test
@NoArgsConstructor
class CobolErrorStrategy extends DefaultErrorStrategy implements MessageServiceProvider {
  private static final String REPORT_NO_VIABLE_ALTERNATIVE =
      "ErrorStrategy.reportNoViableAlternative";
  private static final String REPORT_MISSING_TOKEN = "ErrorStrategy.reportMissingToken";

  @Getter @Setter private MessageService messageService;
  @Getter @Setter private ErrorMessageHelper errorMessageHelper;

  CobolErrorStrategy(MessageService messageService) {
    this.messageService = messageService;
    this.errorMessageHelper = new ErrorMessageHelper(messageService);
  }

  @Override
  public void reportError(Parser recognizer, RecognitionException e) {
    // if we've already reported an error and have not matched a token
    // yet successfully, don't report any errors.
    if (inErrorRecoveryMode(recognizer)) {
      return; // don't report spurious errors
    }
    beginErrorCondition(recognizer);

    if (e instanceof NoViableAltException) {
      reportNoViableAlternative(recognizer, (NoViableAltException) e);
    } else if (e instanceof InputMismatchException) {
      reportInputMismatch(recognizer, (InputMismatchException) e);
    } else if (e instanceof FailedPredicateException) {
      reportFailedPredicate(recognizer, (FailedPredicateException) e);
    } else {
      reportUnrecognizedException(recognizer, e);
    }
  }

  private void reportUnrecognizedException(Parser recognizer, RecognitionException e) {
    LOG.error("unknown recognition error type: " + e.getClass().getName());
    recognizer.notifyErrorListeners(e.getOffendingToken(), e.getMessage(), e);
  }

  @Override
  protected void reportInputMismatch(Parser recognizer, InputMismatchException e) {
    Token token = e.getOffendingToken();
    Pair<String, String> errorCodePair = errorMessageHelper.getInputMismatchMessage(recognizer, e, token, getOffendingToken(e));
    notifyToAppropriateListener(recognizer, e, token, errorCodePair);
  }

  @Override
  protected void reportNoViableAlternative(Parser recognizer, NoViableAltException e) {
    String messageParams = errorMessageHelper.retrieveInputForNoViableException(recognizer, e);
    Pair<String, String> errorPair =
        messageService.getMessage(REPORT_NO_VIABLE_ALTERNATIVE, messageParams);
    notifyToAppropriateListener(recognizer, e, errorPair);
  }

  @Override
  protected void reportUnwantedToken(Parser recognizer) {
    if (inErrorRecoveryMode(recognizer)) {
      return;
    }
    beginErrorCondition(recognizer);
    Token currentToken = recognizer.getCurrentToken();
    Pair<String, String> errorPair = errorMessageHelper.getUnwantedTokenMessage(recognizer, currentToken);
    notifyToAppropriateListener(recognizer, null, currentToken, errorPair);
  }

  @Override
  protected void reportMissingToken(Parser recognizer) {
    if (inErrorRecoveryMode(recognizer)) {
      return;
    }
    beginErrorCondition(recognizer);
    Pair<String, String> errorPair =
        messageService.getMessage(
            REPORT_MISSING_TOKEN,
            errorMessageHelper.getExpectedText(recognizer),
            ErrorMessageHelper.getRule(recognizer));
    notifyToAppropriateListener(recognizer, null, recognizer.getCurrentToken(), errorPair);
  }

  private String getOffendingToken(InputMismatchException e) {
    return getTokenErrorDisplay(e.getOffendingToken());
  }

  private static void notifyToAppropriateListener(
          Parser recognizer, RecognitionException e, Pair<String, String> msg) {
    notifyToAppropriateListener(recognizer, e, e.getOffendingToken(), msg);
  }

  private static void notifyToAppropriateListener(
          Parser recognizer, RecognitionException e, Token token, Pair<String, String> msg) {
    if (recognizer instanceof MessageServiceParser) {
      MessageServiceParser parser = (MessageServiceParser) recognizer;
      parser.notifyErrorListeners(token, msg, e);
    } else {
      recognizer.notifyErrorListeners(token, msg.getRight(), e);
    }
  }
}
