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
package org.eclipse.lsp.cobol.core.messages;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp.cobol.common.message.LocaleStore;
import org.eclipse.lsp.cobol.common.message.MessageService;
import org.eclipse.lsp.cobol.common.message.MessageTemplate;
import org.eclipse.lsp.cobol.core.engine.dialects.WorkingFolderService;
import org.eclipse.lsp.cobol.service.settings.SettingsService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.MissingResourceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test to check PropertiesMessageService */
class PropertiesMessageServiceTest {

  private static MessageService messageService;
  private LocaleStore localeMock;
  SettingsService settingsService = mock(SettingsService.class);
  WorkingFolderService workingFolderService = mock(WorkingFolderService.class);

  @BeforeEach
  public void beforeAll() {
    localeMock = mock(LocaleStore.class);
    when(localeMock.getApplicationLocale()).thenReturn(Locale.ENGLISH);
    messageService =
        new PropertiesMessageService(
            "resourceBundles/test", localeMock, settingsService, workingFolderService);
  }

  @Test
  void whenValidMessageTemplateProvide_getFormattedMessage() {
    final Pair<String, String> message = messageService.getMessageWithErrorCode("1");
    assertEquals("This is a test.", message.getValue());

    final Pair<String, String> message1 = messageService.getMessageWithErrorCode("2", "TEST_PARAM");
    assertEquals("This is a test for parameters. Received params is -> TEST_PARAM .", message1.getValue());
  }

  @Test
  void whenValidMessageTemplateProvideFR_getFormattedMessage() {
    when(localeMock.getApplicationLocale()).thenReturn(Locale.FRENCH);
    MessageService messageServiceFR =
        new PropertiesMessageService(
            "resourceBundles/test", localeMock, settingsService, workingFolderService);
    assertEquals("French test selected.", messageServiceFR.getMessageWithErrorCode("1").getValue());

    assertEquals(
        "French test with parameters. Received params is -> TEST_PARAM .",
        messageServiceFR.getMessageWithErrorCode("2", "TEST_PARAM").getValue());
  }

  @Test
  void whenInValidMessageTemplatePathProvide_getException() {
    Assertions.assertThrows(
        MissingResourceException.class,
        () -> new PropertiesMessageService("dummy", localeMock, settingsService, workingFolderService));
  }

  @Test
  void whenEmptyMessageTemplateProvided_getNoException_getKeyInstead() {
    MessageService messageServiceLocal =
        new PropertiesMessageService(
            "resourceBundles/Test_messageServiceEmptyFile",
            localeMock,
                settingsService,
                workingFolderService
        );
    assertEquals("1", messageServiceLocal.getMessageWithErrorCode("1").getValue());
  }

  @Test
  void whenMultipleMsgServiceExist_thenSupportDuplicateKeys() {
    MessageService messageService1 =
        new PropertiesMessageService(
            "resourceBundles/test-2", localeMock, settingsService, workingFolderService);
    final Pair<String, String> formattedMessage = messageService1.getMessageWithErrorCode("1", localeMock);
    assertEquals("This is a duplicate key test for diff msg service.", formattedMessage.getValue());
  }

  @Test
  void testLocalizeTemplateNoArgs() {
    assertEquals("This is a test.", messageService.localizeTemplate(MessageTemplate.of("1")).getValue());
  }

  @Test
  void testLocalizeWithMixedArgs() {
    assertEquals(
        "Arg1: (4,nested arg: nest), arg2: def",
        messageService.localizeTemplate(
            MessageTemplate.of("3",
                    MessageTemplate.of("4", "nest"), "def")).getValue());
  }

  @Test
  void testLocalizeWithNumericArgs() {
    assertEquals(
        "Arg1: (4,nested arg: 1), arg2: 2",
        messageService.localizeTemplate(MessageTemplate.of("3",
                MessageTemplate.of("4", 1), 2)).getValue());
  }

  @Test
  void testLocalizeWithRecursiveArgs() {
    assertEquals(
        "Arg1: (3,Arg1: nest1, arg2: (4,nested arg: nest2)), arg2: (4,nested arg: (4,nested arg: (4,nested arg: very nested)))",
        messageService.localizeTemplate(
            MessageTemplate.of(
                "3",
                MessageTemplate.of("3", "nest1", MessageTemplate.of("4", "nest2")),
                MessageTemplate.of(
                    "4", MessageTemplate.of("4", MessageTemplate.of("4", "very nested")
                        ))
            )).getValue());
  }

  @Test
  void testLocalizeWithConcatenation() {
    Pair<String, String> actual = messageService.localizeTemplate(
            MessageTemplate.of(
                    "3",
                    MessageTemplate.concatenatingArgs(
                            "4", " - ", 1, 2, MessageTemplate.concatenatingArgs("4", " b ", "a", "c")),
                    4));
    assertEquals("Arg1: (4,nested arg: 1 - 2 - (4,nested arg: a b c)), arg2: 4", actual.getValue());
    assertEquals("3", actual.getKey());
  }
}
