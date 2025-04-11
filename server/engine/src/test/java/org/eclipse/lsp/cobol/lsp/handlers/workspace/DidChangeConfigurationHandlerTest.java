/*
 * Copyright (c) 2024 Broadcom.
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
package org.eclipse.lsp.cobol.lsp.handlers.workspace;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.eclipse.lsp.cobol.service.settings.SettingsParametersEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.eclipse.lsp.cobol.common.dialects.CobolLanguageId;
import org.eclipse.lsp.cobol.common.message.LocaleStore;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.lsp.*;
import org.eclipse.lsp.cobol.service.CobolLSPServerStateService;
import org.eclipse.lsp.cobol.service.delegates.completions.Keywords;
import org.eclipse.lsp.cobol.service.settings.SettingsService;
import org.eclipse.lsp.cobol.service.settings.layout.CodeLayoutStore;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests {@link DidChangeConfigurationHandler} */
class DidChangeConfigurationHandlerTest {
  /** Test no watchers added or removed when the path is empty */
  @Test
  void testChangeConfigurationNoPathToRegister() throws InterruptedException {
    DisposableLSPStateService stateService = new CobolLSPServerStateService();
    SettingsService settingsService = mock(SettingsService.class);
    LocaleStore localeStore = mock(LocaleStore.class);
    Keywords keywords = mock(Keywords.class);
    MessageService messageService = mock(MessageService.class);

    DidChangeConfigurationHandler didChangeConfigurationHandler =
        new DidChangeConfigurationHandler(
            stateService,
            settingsService,
            localeStore,
            keywords,
            messageService,
            getMockLayoutStore());

    when(settingsService.fetchConfiguration(LOCALE.label))
        .thenReturn(completedFuture(singletonList("LOCALE")));
    when(settingsService.fetchConfiguration(LOGGING_LEVEL.label))
        .thenReturn(completedFuture(singletonList("INFO")));
    when(settingsService.fetchConfiguration(COBOL_PROGRAM_LAYOUT.label))
        .thenReturn(completedFuture(ImmutableList.of(CobolLanguageId.COBOL.getLayout())));
    when(localeStore.notifyLocaleStore()).thenReturn(e -> {});

    ArgumentCaptor<String> capture = forClass(String.class);
    didChangeConfigurationHandler.didChangeConfiguration(
        new DidChangeConfigurationParams(new Object()));
    verify(settingsService, times(3)).fetchConfiguration(String.valueOf(capture.capture()));
    assertEquals(
        ImmutableList.of(LOCALE.label, LOGGING_LEVEL.label, COBOL_PROGRAM_LAYOUT.label),
        capture.getAllValues());
  }

  private CodeLayoutStore getMockLayoutStore() {
    CodeLayoutStore layoutStore = mock(CodeLayoutStore.class);
    when(layoutStore.getCodeLayout())
        .thenReturn(Optional.ofNullable(CobolLanguageId.COBOL.getLayout()));
    when(layoutStore.updateCodeLayout()).thenReturn(mock -> {});
    return layoutStore;
  }
}
