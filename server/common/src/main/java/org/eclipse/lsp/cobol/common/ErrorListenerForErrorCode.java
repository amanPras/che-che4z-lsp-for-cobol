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
package org.eclipse.lsp.cobol.common;

import org.apache.commons.lang3.tuple.Pair;

public interface ErrorListenerForErrorCode {
    /**
     * Basic contract for grammar error listeners which support error code mapped to an error message
     * @param offendingSymbol offendingSymbol
     * @param line line of error
     * @param charPositionInLine char position of error
     * @param errorPair error code mapped to an error message
     * @param e
     */
    void syntaxError(Object offendingSymbol,
                     int line,
                     int charPositionInLine,
                     Pair<String, String> errorPair,
                     Exception e);
}
