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
package org.eclipse.lsp.cobol.lsp.handlers.workspace;

import static org.eclipse.lsp.cobol.service.settings.SettingsParametersEnum.*;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp.cobol.common.message.LocaleStore;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.utils.LogLevelUtils;
import org.eclipse.lsp.cobol.lsp.DisposableLSPStateService;
import org.eclipse.lsp.cobol.service.delegates.completions.Keywords;
import org.eclipse.lsp.cobol.service.settings.SettingsService;
import org.eclipse.lsp.cobol.service.settings.layout.CodeLayoutStore;
import org.eclipse.lsp4j.DidChangeConfigurationParams;

/** LSP DidChangeConfiguration Handler */
@Slf4j
public class DidChangeConfigurationHandler {
  private final DisposableLSPStateService disposableLSPStateService;
  private final SettingsService settingsService;
  private final LocaleStore localeStore;
  private final Keywords keywords;
  private final MessageService messageService;
  private final CodeLayoutStore codeLayoutStore;

  @Inject
  public DidChangeConfigurationHandler(
      DisposableLSPStateService disposableLSPStateService,
      SettingsService settingsService,
      LocaleStore localeStore,
      Keywords keywords,
      MessageService messageService,
      CodeLayoutStore codeLayoutStore) {
    this.disposableLSPStateService = disposableLSPStateService;
    this.settingsService = settingsService;
    this.localeStore = localeStore;
    this.keywords = keywords;
    this.messageService = messageService;
    this.codeLayoutStore = codeLayoutStore;
  }

  /**
   * Handle didChangeConfiguration LSP event.
   *
   * @param params DidChangeConfigurationParams
   */
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    if (disposableLSPStateService.isServerShutdown()) {
      return;
    }
    messageService.reloadMessages();

    settingsService.fetchConfiguration(LOCALE.label).thenAccept(localeStore.notifyLocaleStore());
    settingsService
        .fetchConfiguration(LOGGING_LEVEL.label)
        .thenAccept(LogLevelUtils.updateLogLevel());
    settingsService
        .fetchConfiguration(COBOL_PROGRAM_LAYOUT.label)
        .thenAccept(codeLayoutStore.updateCodeLayout());
    keywords.updateStorage();
  }
}
