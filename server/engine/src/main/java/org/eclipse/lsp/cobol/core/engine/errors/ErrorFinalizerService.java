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
 *
 */
package org.eclipse.lsp.cobol.core.engine.errors;

import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.lsp.cobol.common.error.ErrorSeverity;
import org.eclipse.lsp.cobol.common.error.ErrorSource;
import org.eclipse.lsp.cobol.common.error.SyntaxError;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.model.Locality;
import org.eclipse.lsp.cobol.core.engine.analysis.AnalysisContext;
import org.eclipse.lsp.cobol.core.semantics.CopybooksRepository;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.eclipse.lsp.cobol.core.engine.errors.DiagnosticSensitivity.NORMAL;

/**
 * Process errors for copybooks statements
 */
@Singleton
public class ErrorFinalizerService {

  private final MessageService messageService;
  private static DiagnosticSensitivity filterDiagnostics = NORMAL;

  @Inject
  public ErrorFinalizerService(MessageService messageService) {
    this.messageService = messageService;
  }

  /**
   * Localize syntax error
   * @param syntaxError - syntax error
   * @return localized syntax error
   */
  public SyntaxError localizeErrorMessage(SyntaxError syntaxError) {
    return ofNullable(syntaxError.getMessageTemplate())
        .map(messageService::localizeTemplate)
        .map(message -> syntaxError.toBuilder().messageTemplate(null).suggestionWithErrorCode(message).build())
        .orElse(syntaxError);
  }

  /**
   * Process errors to check if needed to create errors for copybook statements
   * @param ctx - an analysis context
   * @param copybooksRepository - copybook repository
   */
  public void processLateErrors(AnalysisContext ctx, CopybooksRepository copybooksRepository) {
    List<SyntaxError> accumulatedErrors = ctx.getAccumulatedErrors().stream()
            .filter(err -> filterDiagnotics(err, messageService.getFatalErrors())).collect(toList());
    accumulatedErrors.addAll(collectErrorsForCopybooks(accumulatedErrors, copybooksRepository));
    List<SyntaxError> distinct = accumulatedErrors.stream().distinct().collect(toList());
    ctx.getAccumulatedErrors().clear();
    ctx.getAccumulatedErrors().addAll(distinct);
  }

  private List<SyntaxError> collectErrorsForCopybooks(
      List<SyntaxError> errors, CopybooksRepository copybooksRepository) {
    Set<SyntaxError> processedErrors = new HashSet<>();
    errors.stream()
        .filter(shouldRaise(processedErrors))
        .forEach(err -> raiseError(err,  copybooksRepository, processedErrors));
    return new LinkedList<>(processedErrors);
  }

  private void raiseError(SyntaxError error,
                          CopybooksRepository copybooksRepository, Set<SyntaxError> processedErrors) {
    Stream.of(error)
        .filter(shouldRaise(processedErrors))
        .forEach(
            err -> {
              for (Locality locality : copybooksRepository.getDefinitionStatements().get(err.getLocation().getCopybookId())) {
                raiseErrorForCopybook(locality, processedErrors, copybooksRepository);
              }
            });
  }

  private void raiseErrorForCopybook(Locality copybookStatementLocality,
                                     Set<SyntaxError> processedErrors,
                                     CopybooksRepository copybooksRepository) {
    SyntaxError newError = SyntaxError.syntaxError()
        .location(copybookStatementLocality.toOriginalLocation())
        .errorSource(ErrorSource.COPYBOOK)
        .severity(ErrorSeverity.ERROR)
        .suggestionWithErrorCode(
            messageService.getMessageWithErrorCode("postprocessing.copybookHasErrors"))
        .build();
    if (processedErrors.contains(newError)) {
      return;
    }
    processedErrors.add(newError);
    for (Locality locality : copybooksRepository.getDefinitionStatements().get(newError.getLocation().getCopybookId())) {
      raiseErrorForCopybook(locality, processedErrors, copybooksRepository);
    }
  }

  private Predicate<SyntaxError> shouldRaise(Set<SyntaxError> processedErrors) {
    return err -> (err.getLocation() != null && err.getLocation().getCopybookId() != null
        && !processedErrors.contains(err));
  }

  /**
   * Filter diagnostic based on the diagnostic level provided by the client
   * @param syntaxError syntax error to be processed
   * @param fatalErrors
   * @return true if diagnostics is within the clients diagnostic level, false otherwise
   */
  public static boolean filterDiagnotics(SyntaxError syntaxError, Set<String> fatalErrors) {
    if (filterDiagnostics == NORMAL) return true;
    return ofNullable(syntaxError.getErrorCode())
            .map(errCode -> fatalErrors.contains(errCode.getLabel()))
            .orElse(false);
  }


  /**
   * update the diagnostics level set by client
   * @param levels
   */
  public void updateDiagnosticsLevel(List<Object> levels) {
    if (levels != null && !levels.isEmpty()) {
      if (levels.get(0) instanceof JsonElement) {
        JsonElement option = (JsonElement) levels.get(0);
        filterDiagnostics = DiagnosticSensitivity.valueOf(option.getAsString());
      }
    }
  }
}
