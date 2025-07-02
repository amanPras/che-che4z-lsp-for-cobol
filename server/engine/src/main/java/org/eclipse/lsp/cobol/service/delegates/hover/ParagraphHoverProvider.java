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
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.eclipse.lsp.cobol.common.AnalysisResult;
import org.eclipse.lsp.cobol.common.model.Describable;
import org.eclipse.lsp.cobol.common.model.tree.*;
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
    Optional<Node> nodeOpts =
        rootNode.flatMap(
            root ->
                RangeUtils.findNodeByPosition(
                    root, position.getTextDocument().getUri(), position.getPosition()));
    if (!nodeOpts.isPresent()) return null;
    Node node = nodeOpts.get();
    if (!(node instanceof CodeBlockUsageNode)) return null;
    List<Location> definitions = ((CodeBlockUsageNode) node).getDefinitions();
    if (definitions == null || definitions.isEmpty()) return null;

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
