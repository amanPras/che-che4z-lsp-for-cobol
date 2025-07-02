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
package org.eclipse.lsp.cobol.service.delegates.hover;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.eclipse.lsp.cobol.common.AnalysisResult;
import org.eclipse.lsp.cobol.common.model.Describable;
import org.eclipse.lsp.cobol.common.model.Locality;
import org.eclipse.lsp.cobol.common.model.NodeType;
import org.eclipse.lsp.cobol.common.model.tree.Node;
import org.eclipse.lsp.cobol.common.model.tree.ProgramNode;
import org.eclipse.lsp.cobol.common.model.tree.RootNode;
import org.eclipse.lsp.cobol.common.symbols.CodeBlockReference;
import org.eclipse.lsp.cobol.common.symbols.ProcedureId;
import org.eclipse.lsp.cobol.common.symbols.SymbolTable;
import org.eclipse.lsp.cobol.common.utils.RangeUtils;
import org.eclipse.lsp.cobol.lsp.SourceUnitGraph;
import org.eclipse.lsp.cobol.service.CobolDocumentModel;
import org.eclipse.lsp4j.*;

/** The class provides hover information for Procedures and Sections. */
public class ParagraphHoverProvider implements HoverProvider {

  /**
   * @param document - document model that contains a semantic context
   * @param position - cursor position
   * @param documentGraph - workspace doc graph
   * @return
   */
  @Nullable
  @Override
  public Hover getHover(
      @Nullable CobolDocumentModel document,
      @NonNull TextDocumentPositionParams position,
      SourceUnitGraph documentGraph) {
    Optional<AnalysisResult> analysisResult =
        Optional.ofNullable(document).map(CobolDocumentModel::getAnalysisResult);
    if (!analysisResult.isPresent()) return null;
    Optional<RootNode> rootNode =
        Optional.of(document)
            .map(CobolDocumentModel::getAnalysisResult)
            .map(AnalysisResult::getRootNode);
    Optional<Node> node =
        rootNode.flatMap(
            root ->
                RangeUtils.findNodeByPosition(
                    root, position.getTextDocument().getUri(), position.getPosition()));
    if (!node.isPresent()) return null;
    Optional<ProgramNode> programNode =
        node.get().getNearestParentByType(NodeType.PROGRAM).map(ProgramNode.class::cast);
    if (!programNode.isPresent()) return null;
    SymbolTable symbolTable =
        analysisResult.get().getSymbolTableMap().get(SymbolTable.generateKey(programNode.get()));
    Map<ProcedureId, CodeBlockReference> procedures = symbolTable.getProcedures();

    List<Location> definitions =
        procedures.values().stream()
            .filter(
                codeBlockReference ->
                    codeBlockReference.getUsage().stream()
                        .anyMatch(
                            usage ->
                                RangeUtils.isInside(
                                    position.getTextDocument().getUri(),
                                    position.getPosition(),
                                    Locality.builder()
                                        .range(usage.getRange())
                                        .uri(usage.getUri())
                                        .build())))
            .map(CodeBlockReference::getDefinitions)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    return rootNode
        .get()
        .getDepthFirstStream()
        .filter(n -> definitions.contains(n.getLocality().toLocation()))
        .filter(Describable.class::isInstance)
        .map(Describable.class::cast)
        .findFirst()
        .map(
            element ->
                new Hover(
                    new MarkupContent(
                        MarkupKind.MARKDOWN,
                        "```cobol\n" + element.getFormattedDisplayString() + "\n```")))
        .orElse(null);
  }
}
