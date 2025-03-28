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
package org.eclipse.lsp.cobol.common.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp.cobol.common.mapping.OriginalLocation;
import org.eclipse.lsp.cobol.common.message.MessageTemplate;
import org.eclipse.lsp.cobol.common.model.Locality;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

/**
 * This value class represents a syntax or semantic error found during the analysis. The finalized
 * version should always contain a non-null {@link Locality}. If it has only the offended token
 * after the CobolLanguageEngine finishes analysis, then
 * this error is invalid.
 */
// Please, don't use static imports for this method:
// https://github.com/rzwitserloot/lombok/issues/2044
@Builder(builderMethodName = "syntaxError", toBuilder = true)
@Value
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SyntaxError {
  @EqualsAndHashCode.Include OriginalLocation location;
  @EqualsAndHashCode.Include MessageTemplate messageTemplate;
  @EqualsAndHashCode.Include String suggestionString;
  @EqualsAndHashCode.Include Pair<String, String> suggestion;
  @EqualsAndHashCode.Include ErrorSeverity severity;
  ErrorCode errorCode;
  @EqualsAndHashCode.Include ErrorSource errorSource;
  @EqualsAndHashCode.Include DiagnosticRelatedInformation relatedInformation;

  @EqualsAndHashCode.Include
  public String errorCodeLabel() {
    return this.errorCode != null ? this.errorCode.getLabel() : null;
  }

  public static class SyntaxErrorBuilder {
    private ErrorCode errorCode;

    public SyntaxError build() {
      return getSyntaxError();
    }

    private SyntaxError getSyntaxError() {
      if (errorCode == null) {
        if (messageTemplate != null) {
          errorCode = messageTemplate::getTemplate;
        } else if (suggestion != null) {
          setFromErrorPair();
        }
      }
      if (suggestionString == null && suggestion != null) {
        setFromErrorPair();
      }
      return new SyntaxError(location, messageTemplate, suggestionString, suggestion,
              severity, errorCode, errorSource, relatedInformation);
    }

    private void setFromErrorPair() {
      errorCode = errorCode != null ? errorCode : suggestion::getKey;
      suggestionString = suggestion.getValue();
    }
  }
}
