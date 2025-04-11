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
package org.eclipse.lsp.cobol.service.copybooks;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.eclipse.lsp.cobol.test.engine.UseCaseUtils.DOCUMENT_URI;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import org.eclipse.lsp.cobol.common.CleanerPreprocessor;
import org.eclipse.lsp.cobol.common.ResultWithErrors;
import org.eclipse.lsp.cobol.common.copybook.*;
import org.eclipse.lsp.cobol.common.error.ErrorSeverity;
import org.eclipse.lsp.cobol.common.error.SyntaxError;
import org.eclipse.lsp.cobol.common.mapping.ExtendedText;
import org.eclipse.lsp.cobol.common.mapping.OriginalLocation;
import org.eclipse.lsp.cobol.common.utils.PredefinedCopybooks;
import org.eclipse.lsp.cobol.lsp.jrpc.CobolLanguageClient;
import org.eclipse.lsp.cobol.service.providers.ClientProvider;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

/**
 * This unit tests check the of the {@link CopybookServiceImpl} how it resolves the copybook
 * requests.
 */
class CopybookServiceTest {
  private static final String VALID_CPY_NAME = "VALIDNAME";
  private static final String VALID_CPY_URI = "file:///c:/workspace/.c4z/.copybooks/VALIDNAME.CPY";
  private static final String INVALID_CPY_NAME = "INVALID";
  private static final String INVALID_2_CPY_NAME = "INVALID_2";
  private static final String PARENT_CPY_NAME = "PARENT";
  private static final String PARENT_CPY_URI = "file:///c:/workspace/.c4z/.copybooks/PARENT.CPY";
  private static final String PARENT_CONTENT = "         COPY NESTED.";
  private static final String NESTED_CPY_NAME = "nested";
  private static final String CONTENT = "content";
  private static final String SQLCA = "SQLCA";
  private static final String SQLDA = "SQLDA";
  private static final String DOCUMENT_2_URI = "file:///c:/workspace/document2.cbl";
  private static final String DOCUMENT_3_URI = "implicit:///implicitCopybooks/SQLCA_DB2.cpy";
  private static final String COPYBOOK_3_NAME = "SQLCA_DB2";
  private final CobolLanguageClient client = mock(CobolLanguageClient.class);
  private final CleanerPreprocessor preprocessor = mock(CleanerPreprocessor.class);
  private final Path cpyPath = mock(Path.class);
  private final Path parentPath = mock(Path.class);
  private final String languageId = "cobol";

  @BeforeEach
  void setupMocks() {
    when(preprocessor.cleanUpCode(anyString(), anyString()))
        .thenAnswer(
            (Answer<ResultWithErrors<ExtendedText>>)
                invocation ->
                    ResultWithErrors.of(
                        new ExtendedText(invocation.getArgument(1), invocation.getArgument(0))));
    when(client.resolveCopybook(DOCUMENT_URI, VALID_CPY_NAME, "COBOL"))
        .thenReturn(supplyAsync(() -> VALID_CPY_URI));
    when(client.resolveCopybook(DOCUMENT_URI, INVALID_CPY_NAME, "COBOL"))
        .thenReturn(supplyAsync(() -> null));
    when(client.resolveCopybook(DOCUMENT_URI, COPYBOOK_3_NAME, "COBOL"))
        .thenReturn(supplyAsync(() -> DOCUMENT_3_URI));

    when(cpyPath.toUri()).thenReturn(URI.create(VALID_CPY_URI));
    when(parentPath.toUri()).thenReturn(URI.create(PARENT_CPY_URI));
  }

  /**
   * Test a main positive scenario when the copybook exists, and the request invoked while copybook
   * analysis enabled.
   */
  @Test
  void testRequestWhileCopybookAnalysisActiveProcessed() {
    CopybookName copybookName = createCopybook(VALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();
    when(client.resolveCopybookUri(DOCUMENT_URI, VALID_CPY_NAME, "COBOL"))
        .thenReturn(supplyAsync(() -> VALID_CPY_URI));
    when(client.getFileContent(VALID_CPY_URI)).thenReturn(supplyAsync(() -> CONTENT));
    CopybookModel copybookModel =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_URI),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                preprocessor)
            .getResult();

    assertEquals(
        new CopybookModel(
            copybookName.toCopybookId(DOCUMENT_URI), copybookName, VALID_CPY_URI, CONTENT),
        copybookModel);
  }

  /**
   * Test a main positive scenario when the copybook exists, and the request invoked while copybook
   * analysis is enabled.
   */
  @Test
  @Disabled("handed this responsibility to client")
  void testResponseIfFileNotExists() {
    CopybookName copybookName = createCopybook(VALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();
    CopybookModel copybookModel =
        copybookService
            .resolve(
                CopybookId.fromString(copybookName.getDisplayName()),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();

    assertEquals(
        new CopybookModel(copybookName.toCopybookId(DOCUMENT_URI), copybookName, null, null),
        copybookModel);
  }

  /**
   * Test an empty request sent if URI cannot be found (if copybook doesn't exist) no to block the
   * analysis thread.
   */
  @Test
  void testRequestWhenUriNotFoundProcessed() {
    CopybookName copybookName = new CopybookName(INVALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();
    when(client.resolveCopybookUri(anyString(), anyString(), anyString()))
        .thenReturn(supplyAsync(() -> null));
    CopybookModel copybookModel =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_URI),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();

    assertEquals(
        new CopybookModel(copybookName.toCopybookId(DOCUMENT_URI), copybookName, null, null),
        copybookModel);
  }

  /**
   * Test no new file system calls invoked when the copybook resolved first time in "did open"
   * analysis, and the copybook cached.
   */
  @Test
  @Disabled("copybook resolution is in sync now and is called on each analysis")
  void testNoNewClientCallsOnDidChange() {
    CopybookName copybookName = createCopybook(VALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();
    CopybookModel copybookModelEnabled =
        copybookService
            .resolve(
                CopybookId.fromString(copybookName.getDisplayName()),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();
    CopybookModel copybookModelSkipped =
        copybookService
            .resolve(
                CopybookId.fromString(copybookName.getDisplayName()),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();

    assertEquals(
        new CopybookModel(
            copybookName.toCopybookId(DOCUMENT_URI), copybookName, VALID_CPY_URI, CONTENT),
        copybookModelEnabled);
    assertEquals(
        new CopybookModel(
            copybookName.toCopybookId(DOCUMENT_URI), copybookName, VALID_CPY_URI, CONTENT),
        copybookModelSkipped);
  }

  /**
   * Test when cache invalidation invoked, the next copybook requests tries to resolve URI in order
   * to avoid dirty state
   */
  @Test
  @Disabled("no cache")
  void testCacheInvalidation() {
    CopybookName copybookName = createCopybook(VALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();

    CopybookModel copybookModel =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_URI),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();
    assertEquals(
        new CopybookModel(
            copybookName.toCopybookId(DOCUMENT_URI), copybookName, VALID_CPY_URI, CONTENT),
        copybookModel);

    copybookModel =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_URI),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();
    assertEquals(
        new CopybookModel(
            copybookName.toCopybookId(DOCUMENT_URI), copybookName, VALID_CPY_URI, CONTENT),
        copybookModel);
  }

  @Disabled("Cache is removed")
  @Test
  void cacheInvalidationForNonImplicitCopybook() {
    CopybookName copybookName = createCopybook(COPYBOOK_3_NAME);
    CopybookService copybookService = createCopybookService();

    CopybookModel copybookModel =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_URI),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();
    assertEquals(
        new CopybookModel(
            copybookName.toCopybookId(DOCUMENT_URI), copybookName, DOCUMENT_3_URI, CONTENT),
        copybookModel);

    copybookModel =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_URI),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();
    assertEquals(
        new CopybookModel(
            copybookName.toCopybookId(DOCUMENT_URI), copybookName, DOCUMENT_3_URI, CONTENT),
        copybookModel);
  }

  /**
   * Test {@link CopybookService} responds even if the {@link LanguageClient} return invalid result
   */
  @Test
  @Disabled("now its client responsibility to send either a content or null")
  void testServiceRespondsIfClientSendsInvalidResult() {
    CopybookName copybookName = new CopybookName(VALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();

    when(client.resolveCopybook(anyString(), anyString(), any())).thenReturn(completedFuture(null));
    CopybookModel copybookModel =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_URI),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();

    assertEquals(
        new CopybookModel(copybookName.toCopybookId(DOCUMENT_URI), copybookName, null, null),
        copybookModel);
  }

  //  /**
  //   * Test the service must collect not resolved copybooks and sends downloading request for
  // them.
  //   * The server must track missed copybooks for each document separately. The server must send a
  //   * downloading request only once. Any subsequent Done analysis events should not trigger
  //   * downloading requests until new missed copybooks found.
  //   */
  //  @Test
  //  void testServiceSendsDownloadingRequestForAnalysisFinishedEvent() {
  //    CopybookServiceImpl copybookService = createCopybookService();
  //
  //    when(client.resolveCopybook(DOCUMENT_2_URI, INVALID_2_CPY_NAME, "COBOL"))
  //        .thenReturn(completedFuture(null));
  //
  //    // First document parsed
  //    CopybookName copybookInvalid = createCopybook(INVALID_CPY_NAME);
  //    CopybookModel invalidCpy =
  //        copybookService
  //            .resolve(
  //                CopybookId.fromString(INVALID_CPY_NAME),
  //                copybookInvalid,
  //                DOCUMENT_URI,
  //                DOCUMENT_URI,
  //                null)
  //            .getResult();
  //    CopybookName copybookValid = createCopybook(VALID_CPY_NAME);
  //    CopybookModel validCpy =
  //        copybookService
  //            .resolve(
  //                CopybookId.fromString(VALID_CPY_NAME),
  //                copybookValid,
  //                DOCUMENT_URI,
  //                DOCUMENT_URI,
  //                null)
  //            .getResult();
  //    // Second document parsed
  //    CopybookName copybookInvalid2 = createCopybook(INVALID_2_CPY_NAME);
  //    CopybookModel invalidCpy2 =
  //        copybookService
  //            .resolve(
  //                copybookInvalid2.toCopybookId(DOCUMENT_2_URI),
  //                copybookInvalid2,
  //                DOCUMENT_2_URI,
  //                DOCUMENT_2_URI,
  //                null)
  //            .getResult();
  //
  //    // Check that all copybook models are correct
  //    assertEquals(
  //        new CopybookModel(
  //            copybookValid.toCopybookId(DOCUMENT_URI), copybookValid, VALID_CPY_URI, CONTENT),
  //        validCpy);
  //    assertEquals(
  //        new CopybookModel(copybookInvalid.toCopybookId(DOCUMENT_URI), copybookInvalid, null,
  // null),
  //        invalidCpy);
  //    assertEquals(
  //        new CopybookModel(
  //            copybookInvalid2.toCopybookId(DOCUMENT_2_URI), copybookInvalid2, null, null),
  //        invalidCpy2);
  //    CopyBookDTO invalidCopybook = new CopyBookDTO(copybookInvalid);
  //    // First document parsing done
  //    verify(client, times(1))
  //        .downloadCopybooks(DOCUMENT_URI, ImmutableList.of(invalidCopybook), true);
  //
  //    // Others parsing done events for first document are not trigger settingsService
  //
  //    verify(client, times(1))
  //        .downloadCopybooks(DOCUMENT_URI, ImmutableList.of(invalidCopybook), true);
  //    CopyBookDTO invalidCopybook2 = new CopyBookDTO(copybookInvalid2);
  //    // Second document parsing done
  //    verify(client, times(1))
  //        .downloadCopybooks(DOCUMENT_2_URI, ImmutableList.of(invalidCopybook2), true);
  //  }

  /** Test that the service resolves the SQLDA predefined copybook */
  @Test
  void testSqldaCopybookResolutionDoesNotRelyOnBackend() {
    CopybookName copybookName = new CopybookName(SQLDA);
    CopybookServiceImpl copybookService = createCopybookService();

    when(client.resolveCopybookUri(DOCUMENT_URI, SQLDA, "COBOL"))
        .thenReturn(supplyAsync(() -> null));

    assertEquals(
        copybookService.resolve(
            CopybookId.fromString(copybookName.getDisplayName()),
            copybookName,
            DOCUMENT_URI,
            DOCUMENT_URI,
            null),
        copybookService.resolve(
            CopybookId.fromString(copybookName.getDisplayName()),
            copybookName,
            DOCUMENT_URI,
            DOCUMENT_URI,
            null));
  }

  /**
   * Test the service collects all the copybooks that were not resolved and sends a request to
   * resolve all of them, including nested ones
   */
  @Test
  void testServiceSendsDownloadingRequestForAllNotResolvedCopybooks() {
    CopybookServiceImpl copybookService = createCopybookService();
    when(client.resolveCopybookUri(eq(DOCUMENT_URI), anyString(), eq("COBOL")))
        .thenReturn(supplyAsync(() -> null));

    when(client.resolveCopybookUri(DOCUMENT_URI, PARENT_CPY_NAME, "COBOL"))
        .thenReturn(supplyAsync(() -> PARENT_CPY_URI));
    when(client.getFileContent(PARENT_CPY_URI)).thenReturn(supplyAsync(() -> PARENT_CONTENT));

    CopybookName copybookInvalid = createCopybook(INVALID_CPY_NAME);
    CopybookModel invalidCpy =
        copybookService
            .resolve(
                copybookInvalid.toCopybookId(DOCUMENT_URI),
                copybookInvalid,
                DOCUMENT_URI,
                DOCUMENT_URI,
                preprocessor)
            .getResult();
    CopybookName copybookParent = createCopybook(PARENT_CPY_NAME);
    CopybookModel parentCpy =
        copybookService
            .resolve(
                copybookParent.toCopybookId(DOCUMENT_URI),
                copybookParent,
                DOCUMENT_URI,
                DOCUMENT_URI,
                preprocessor)
            .getResult();
    // Nested copybook declaration
    CopybookName copybookNested = createCopybook(NESTED_CPY_NAME);
    CopybookModel nestedCpy =
        copybookService
            .resolve(
                CopybookId.fromString(NESTED_CPY_NAME),
                copybookNested,
                DOCUMENT_URI,
                PARENT_CPY_URI,
                preprocessor)
            .getResult();

    // Check that all copybook models are correct
    assertEquals(
        new CopybookModel(copybookInvalid.toCopybookId(DOCUMENT_URI), copybookInvalid, null, null),
        invalidCpy);
    assertEquals(
        new CopybookModel(
            copybookParent.toCopybookId(DOCUMENT_URI),
            copybookParent,
            PARENT_CPY_URI,
            PARENT_CONTENT),
        parentCpy);
    assertEquals(
        new CopybookModel(copybookNested.toCopybookId(DOCUMENT_URI), copybookNested, null, null),
        nestedCpy);
  }

  @Test
  void testPredefinedCopybooksResolvedInsteadOfStaticOnes() {
    final String copybookName = PredefinedCopybooks.Copybook.SQLCA.name();
    final String copybookUri = "file:///c:/workspace/.c4z/.copybooks/" + copybookName + ".cpy";

    when(client.resolveCopybookUri(DOCUMENT_URI, copybookName, "COBOL"))
        .thenReturn(supplyAsync(() -> copybookUri));
    when(client.getFileContent(copybookUri)).thenReturn(supplyAsync(() -> CONTENT));

    CopybookServiceImpl copybookService = createCopybookService();
    // Assert the copybook was resolved from the workspace
    final CopybookModel model =
        copybookService
            .resolve(
                CopybookId.fromString(copybookName),
                new CopybookName(copybookName),
                DOCUMENT_URI,
                DOCUMENT_URI,
                preprocessor)
            .getResult();

    // Assert the copybook was resolved from the workspace
    assertEquals(copybookUri, model.getUri());
    assertEquals("content", model.getContent());
    verify(client, times(1)).resolveCopybookUri(DOCUMENT_URI, copybookName, "COBOL");
  }

  private CopybookServiceImpl createCopybookService() {
    ClientProvider provider = new ClientProvider();
    provider.setClient(client);
    return new CopybookServiceImpl(provider);
  }

  private CopybookName createCopybook(String displayName) {
    return CopybookName.builder().displayName(displayName).build();
  }

  @Test
  @Disabled("cache disabled")
  void store() {
    CopybookName copybookName = createCopybook(VALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();
    CopybookModel copybookModel =
        copybookService
            .resolve(
                CopybookId.fromString(copybookName.getDisplayName()),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();
    CopybookModel resolve;
    resolve =
        copybookService
            .resolve(
                copybookName.toCopybookId(DOCUMENT_2_URI),
                copybookName,
                DOCUMENT_2_URI,
                DOCUMENT_2_URI,
                null)
            .getResult();
    assertNull(resolve.getContent());
    resolve =
        copybookService
            .resolve(
                CopybookId.fromString(copybookName.getDisplayName()),
                copybookName,
                DOCUMENT_URI,
                DOCUMENT_URI,
                null)
            .getResult();
    assertEquals(CONTENT, resolve.getContent());
  }

  @Test
  void whenErrorInPreprocessOfCopybook_thenResolveReturnsPreprocessErrors() {
    CopybookName copybookName = createCopybook(VALID_CPY_NAME);
    CopybookService copybookService = createCopybookService();
    String copybookContent = "SOME TEXT";
    when(client.resolveCopybookUri(DOCUMENT_URI, VALID_CPY_NAME, "COBOL"))
        .thenReturn(supplyAsync(() -> "uri"));
    when(client.getFileContent("uri")).thenReturn(supplyAsync(() -> copybookContent));
    SyntaxError expectedSyntaxError =
        SyntaxError.syntaxError()
            .location(new OriginalLocation(null, VALID_CPY_NAME))
            .suggestion("some suggestion")
            .severity(ErrorSeverity.ERROR)
            .build();
    when(preprocessor.cleanUpCode(anyString(), anyString()))
        .thenReturn(
            new ResultWithErrors<>(
                new ExtendedText(copybookContent, null),
                Collections.singletonList(expectedSyntaxError)));

    ResultWithErrors<CopybookModel> resolvedCopybook =
        copybookService.resolve(
            CopybookId.fromString(copybookName.getDisplayName()),
            copybookName,
            DOCUMENT_URI,
            DOCUMENT_URI,
            preprocessor);

    CopybookModel copybookModel = resolvedCopybook.getResult();
    assertEquals(copybookModel.getContent(), copybookContent);
    assertEquals(resolvedCopybook.getErrors().get(0), expectedSyntaxError);
  }
}
