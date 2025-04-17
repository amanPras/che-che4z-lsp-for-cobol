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
 *   Broadcom, Inc. - initial API and implementation
 */

import {
  DEFAULT_DIALECT,
  ENDEVOR_PROCESSOR,
  FAILED_REQUESTS_LIMIT,
  PATHS_DSN,
  PATHS_USS,
  PROVIDE_PROFILE_MSG,
  SETTINGS_CPY_NDVR_DEPENDENCIES,
} from "../../../constants";
import { CopybookDownloadService } from "../../../services/copybook/CopybookDownloadService";
import { ProfileUtils } from "../../../services/util/ProfileUtils";
import { Utils } from "../../../services/util/Utils";
import * as vscode from "vscode";
import {
  notFoundErrorMock,
  permissionsErrorMock,
  unauthorizedErrorMock,
  createZoweExplorerMock,
} from "../../../__mocks__/getZoweExplorerMock.utility";
import { DownloadUtil } from "../../../services/copybook/downloader/DownloadUtil";
import { E4E } from "../../../type/e4eApi";
import * as ProcessorGroups from "../../../services/ProcessorGroups";
import { SettingsService } from "../../../services/Settings";
import { URI as Uri } from "vscode-uri";
import { FileNotFound } from "../../../__mocks__/vscode";

jest.mock("../../../services/reporter");
Utils.getZoweExplorerAPI = jest
  .fn()
  .mockReturnValue({ api: createZoweExplorerMock });

describe("Tests copybook download service", () => {
  let downloadService: CopybookDownloadService;

  let workspaceConfigurationMock: Record<
    string,
    string[] | string | undefined
  > = {};
  let profileName: string;

  let zoweExplorerMock: IApiRegisterClient;
  let zoweMockUnauthorizedError: IApiRegisterClient;
  let zoweMockNotFoundError: IApiRegisterClient;

  beforeAll(() => {
    zoweExplorerMock = createZoweExplorerMock();
    zoweMockUnauthorizedError = createZoweExplorerMock(
      unauthorizedErrorMock,
      unauthorizedErrorMock,
    );
    zoweMockNotFoundError = createZoweExplorerMock(
      notFoundErrorMock,
      notFoundErrorMock,
    );
  });

  beforeEach(() => {
    downloadService = new CopybookDownloadService(
      "storage-path",
      {} as unknown as IApiRegisterClient,
    );
    downloadService["processDownloadError"] = jest.fn();

    jest.spyOn(vscode.workspace, "getConfiguration").mockImplementation(
      () =>
        ({
          get: (key: string) => workspaceConfigurationMock[key],
        }) as unknown as vscode.WorkspaceConfiguration,
    );

    jest
      .spyOn(ProfileUtils, "getAvailableProfiles")
      .mockReturnValue(["profile"]);

    profileName = "profile";
    jest
      .spyOn(ProfileUtils, "getProfileNameForCopybook")
      .mockImplementation(() => profileName);

    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe("checks the prerequisites are checked before resolving remote copybooks", () => {
    describe("ZOWE API is missing", () => {
      it("checks download fails if ZE apis are missing", async () => {
        const resolver = new CopybookDownloadService(
          "storage-path",
          undefined,
          undefined,
        );
        const result = await resolver.resolveCopybookURI(
          "file://doc-ui",
          "copybook",
          DEFAULT_DIALECT,
        );
        expect(result).toBeNull();
      });

      // it("checks missing explorer api produces diagnostics when processor groups have dsn or uss config", async () => {
      //   const diagnosticsService = new DownloadDiagnosticsService();
      //   const service = new CopybookDownloadService(
      //     "storage-path",
      //     undefined,
      //     e4eMock,
      //     undefined,
      //     diagnosticsService,
      //   );
      //   const diagnosticsSpy = jest
      //     .spyOn(diagnosticsService, "showDiagnostics")
      //     .mockImplementation();

      //   const spyConfig = jest.spyOn(
      //     ProcessorGroups,
      //     "loadProcessorGroupCopybookPathsConfig",
      //   );
      //   spyConfig.mockResolvedValue([
      //     {
      //       dataset: "dataset",
      //       uss: "uss",
      //     },
      //   ]);
      //   await service.downloadCopybooks("file:///document-uri", [
      //     { name: "copybook", dialect: DEFAULT_DIALECT },
      //   ]);
      //   expect(diagnosticsSpy).toHaveBeenCalledWith(
      //     expect.objectContaining({ path: "/document-uri" }),
      //     [
      //       {
      //         message: "Zowe Explorer is not installed",
      //         range: {
      //           end: { character: 0, line: 1 },
      //           start: { character: 0, line: 0 },
      //         },
      //         severity: 1,
      //       },
      //     ],
      //   );
      // });

      // it("checks installing e4e or zowe api removes download diagnostics", async () => {
      //   const diagnosticsService = new DownloadDiagnosticsService();
      //   const service = new CopybookDownloadService(
      //     "storage-path",
      //     undefined,
      //     undefined,
      //     undefined,
      //     diagnosticsService,
      //   );
      //   const showDiagnosticsSpy = jest
      //     .spyOn(diagnosticsService, "showDiagnostics")
      //     .mockImplementation();

      //   const clearDiagnosticsSpy = jest
      //     .spyOn(diagnosticsService, "clearDiagnostics")
      //     .mockImplementation();

      //   const spyConfig = jest.spyOn(
      //     ProcessorGroups,
      //     "loadProcessorGroupCopybookPathsConfig",
      //   );
      //   spyConfig.mockResolvedValue([
      //     {
      //       dataset: "dataset",
      //       uss: "uss",
      //     },
      //   ]);
      //   await service.downloadCopybooks("file:///document-uri", [
      //     { name: "copybook", dialect: DEFAULT_DIALECT },
      //   ]);

      //   service.explorerAppeared(zoweExplorerMock);
      //   service.e4eAppeared(e4eMock);

      //   expect(showDiagnosticsSpy).toHaveBeenCalledWith(
      //     expect.objectContaining({ path: "/document-uri" }),
      //     [
      //       {
      //         message: "Zowe Explorer is not installed",
      //         range: {
      //           end: { character: 0, line: 1 },
      //           start: { character: 0, line: 0 },
      //         },
      //         severity: 1,
      //       },
      //     ],
      //   );

      //   expect(clearDiagnosticsSpy).toHaveBeenCalledTimes(2);
      // });
    });

    describe("Endevor API missing", () => {
      // it("checks missing e4e api produces diagnostics when processor groups have endevor config", async () => {
      //   const diagnosticsService = new DownloadDiagnosticsService();
      //   const service = new CopybookDownloadService(
      //     "storage-path",
      //     zoweExplorerMock,
      //     undefined,
      //     undefined,
      //     diagnosticsService,
      //   );
      //   const diagnosticsSpy = jest
      //     .spyOn(diagnosticsService, "showDiagnostics")
      //     .mockImplementation();
      //   const spyConfig = jest.spyOn(
      //     ProcessorGroups,
      //     "loadProcessorGroupCopybookPathsConfig",
      //   );
      //   spyConfig.mockResolvedValue([
      //     {
      //       environment: "environment",
      //       stage: "1",
      //       system: "system",
      //       subsystem: "subsystem",
      //       type: "type",
      //     },
      //   ]);
      //   await service.downloadCopybooks("file:///document-uri", [
      //     { name: "copybook", dialect: DEFAULT_DIALECT },
      //   ]);
      //   expect(diagnosticsSpy).toHaveBeenCalledWith(
      //     expect.objectContaining({ scheme: "file", path: "/document-uri" }),
      //     [
      //       {
      //         message: "Explorer for Endevor is not installed",
      //         range: {
      //           end: { character: 0, line: 1 },
      //           start: { character: 0, line: 0 },
      //         },
      //         severity: 1,
      //       },
      //     ],
      //   );
      // });
    });

    describe("unknown-profile", () => {
      beforeEach(() => {
        workspaceConfigurationMock[PATHS_DSN] = ["TEST.COBOL.COPYBOOK"];
        profileName = "unknown-profile";
      });

      it("checks download fails when provided profile is not a valid profile", async () => {
        await downloadService.resolveCopybookURI(
          "file://document-uri",
          "copybook-name",
          DEFAULT_DIALECT,
        );
        expect(downloadService["processDownloadError"]).toHaveBeenCalledWith(
          `${PROVIDE_PROFILE_MSG} Provided invalid profile name: unknown-profile`,
        );
      });
    });

    describe("profile not provided", () => {
      beforeEach(() => {
        workspaceConfigurationMock[PATHS_DSN] = ["TEST.COBOL.COPYBOOK"];
        profileName = "";
      });

      it("checks download fails when provided profile is not provided", async () => {
        await downloadService.resolveCopybookURI(
          "file://document-uri",
          "copybook-name",
          DEFAULT_DIALECT,
        );
        expect(downloadService["processDownloadError"]).toHaveBeenCalledWith(
          PROVIDE_PROFILE_MSG,
        );
      });
    });

    describe("no profile and no remote location configured", () => {
      beforeEach(() => {
        workspaceConfigurationMock[PATHS_DSN] = [];
        workspaceConfigurationMock[PATHS_USS] = [];
        profileName = "";
      });

      it("should not show the missing zowe profile message", async () => {
        await downloadService.resolveCopybookURI(
          "file://document-uri",
          "copybook-name",
          DEFAULT_DIALECT,
        );
        expect(downloadService["processDownloadError"]).not.toHaveBeenCalled();
      });
    });

    describe("credentials check", () => {
      describe("invalid credentials", () => {
        beforeEach(() => {
          downloadService = new CopybookDownloadService(
            "storage-path",
            zoweMockUnauthorizedError,
          );
          downloadService["processDownloadError"] = jest.fn();
        });

        describe("uss configuration", () => {
          beforeEach(() => {
            workspaceConfigurationMock[PATHS_DSN] = undefined;
            workspaceConfigurationMock[PATHS_USS] = ["/u/test/copybooks"];
          });

          it("checks profile with invalid credentials do not trigger download", async () => {
            await downloadService.resolveCopybookURI(
              "file://document-uri",
              "copybook-name",
              DEFAULT_DIALECT,
            );

            expect(unauthorizedErrorMock).toHaveBeenCalledWith(
              "/u/test/copybooks",
            );
            expect(zoweMockUnauthorizedError.getUssApi).toHaveBeenCalled();
            expect(zoweMockUnauthorizedError.getMvsApi).not.toHaveBeenCalled();

            expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(
              "Incorrect credentials in Zowe profile profile.",
            );
          });
        });

        describe("mvs configuration", () => {
          beforeEach(() => {
            workspaceConfigurationMock[PATHS_DSN] = ["TEST.COBOL.COPYBOOK"];
            workspaceConfigurationMock[PATHS_USS] = ["/u/test/copybooks"];
          });

          it("checks profile with invalid credentials do not trigger download", async () => {
            await downloadService.resolveCopybookURI(
              "file://document-uri",
              "copybook-name",
              DEFAULT_DIALECT,
            );

            expect(unauthorizedErrorMock).toHaveBeenCalledWith(
              "TEST.COBOL.COPYBOOK",
            );
            expect(zoweMockUnauthorizedError.getUssApi).not.toHaveBeenCalled();
            expect(zoweMockUnauthorizedError.getMvsApi).toHaveBeenCalled();

            expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(
              "Incorrect credentials in Zowe profile profile.",
            );
          });
        });
      });

      describe("credentials are valid but copybook dataset doesn't exists", () => {
        beforeEach(() => {
          downloadService = new CopybookDownloadService(
            "storage-path",
            zoweMockNotFoundError,
          );
          workspaceConfigurationMock[PATHS_DSN] = ["TEST.COBOL.COPYBOOK"];
          workspaceConfigurationMock[PATHS_USS] = ["/u/test/copybooks"];

          downloadService.resolveCopybookURI = jest
            .fn()
            .mockResolvedValue(null);
        });

        it("credentials are considered valid, copybooks can be downloaded", async () => {
          await downloadService.resolveCopybookURI(
            "file://document-uri",
            "copybook-name",
            DEFAULT_DIALECT,
          );

          expect(vscode.window.showErrorMessage).not.toHaveBeenCalledWith(
            "Incorrect credentials in Zowe profile profile.",
          );

          expect(downloadService.resolveCopybookURI).toHaveBeenCalled();
        });
      });

      describe("credentials are valid and dataset exists, but user doesn't have permissions for the dataset", () => {
        beforeEach(() => {
          downloadService = new CopybookDownloadService(
            "storage-path",
            createZoweExplorerMock(permissionsErrorMock),
          );
          workspaceConfigurationMock[PATHS_DSN] = ["TEST.COBOL.COPYBOOK"];
          workspaceConfigurationMock[PATHS_USS] = ["/u/test/copybooks"];

          downloadService.resolveCopybookURI = jest
            .fn()
            .mockResolvedValue(null);
        });

        it("credentials are considered valid, copybooks can be downloaded", async () => {
          await downloadService.resolveCopybookURI(
            "file://document-uri",
            "copybook-name",
            DEFAULT_DIALECT,
          );

          expect(vscode.window.showErrorMessage).not.toHaveBeenCalledWith(
            "Incorrect credentials in Zowe profile profile.",
          );

          expect(downloadService.resolveCopybookURI).toHaveBeenCalled();
        });
      });
    });

    describe("if user is able to list the configured copybook dataset, credentials are considered as valid", () => {
      beforeEach(() => {
        downloadService = new CopybookDownloadService(
          "storage-path",
          zoweExplorerMock,
        );
      });

      it("correct credentials don't trigger error message", async () => {
        await downloadService.resolveCopybookURI(
          "file://document-uri",
          "copybook-name",
          DEFAULT_DIALECT,
        );

        expect(vscode.window.showErrorMessage).not.toHaveBeenCalledWith(
          "Incorrect credentials in Zowe profile profile.",
        );
      });
    });

    it("no profile checks are done when download configurations are not configured", async () => {
      const downloadService = new CopybookDownloadService(
        "storage-path",
        zoweExplorerMock,
      );
      ProfileUtils.getAvailableProfiles = jest.fn().mockReturnValue("profile");
      downloadService["processDownloadError"] = jest.fn();
      workspaceConfigurationMock[PATHS_DSN] = undefined;
      workspaceConfigurationMock[PATHS_USS] = undefined;
      expect(
        await downloadService.resolveCopybookURI(
          "file://document-uri",
          "copybook-name",
          DEFAULT_DIALECT,
        ),
      ).toBe(undefined);
      expect(vscode.window.showErrorMessage).not.toHaveBeenCalledWith(
        "Incorrect credentials in Zowe profile profile.",
      );
    });

    it("checks locked profile do not trigger download", async () => {
      const downloadService = new CopybookDownloadService(
        "storage-path",
        zoweExplorerMock,
      );
      ProfileUtils.getAvailableProfiles = jest.fn().mockReturnValue("profile");
      DownloadUtil.isProfileLocked = jest.fn().mockReturnValue(true);
      downloadService["processDownloadError"] = jest.fn();
      expect(
        await downloadService.resolveCopybookURI(
          "file://document-uri",
          "copybook-name",
          DEFAULT_DIALECT,
        ),
      ).toBe(undefined);
    });
    it("checks an invalid zowe profile in a proc group is reported to the user", async () => {
      const mocked = jest.spyOn(
        ProcessorGroups,
        "loadProcessorGroupCopybookPathsConfig",
      );
      DownloadUtil.isProfileLocked = jest.fn().mockReturnValue(false);
      mocked.mockResolvedValue([
        "/libs",
        { dataset: "dataset", profile: "invalidProfile" },
      ]);
      const downloadService = new CopybookDownloadService(
        "storage-path",
        zoweExplorerMock,
      );

      expect(
        await downloadService.resolveCopybookURI(
          "file://document-uri",
          "copybook-name",
          DEFAULT_DIALECT,
        ),
      ).toBe(undefined);
      expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(
        "Please specify a valid Zowe Explorer profile in proc_grps.json to download copybooks from the mainframe. Provided invalid profile name: invalidProfile",
      );
      mocked.mockResolvedValue([]);
    });
  });

  describe("checks order of resolution [E4E, DSN and USS order]", () => {
    beforeEach(() => {
      jest.clearAllMocks();
    });

    describe("order of resolution is same as the one provided in user settings", () => {
      beforeAll(() => {
        workspaceConfigurationMock = {
          [PATHS_DSN]: ["dsn", "dsn-2"],
          [PATHS_USS]: ["uss"],
        };
      });

      it("checks clear cache do not throw error when ZE apis are missing", () => {
        const resolver = new CopybookDownloadService(
          "storage-path",
          undefined,
          undefined,
        );
        resolver.clearCache();
      });

      it("checks clear cache calls e4e clear config", () => {
        const resolver = new CopybookDownloadService(
          "storage-path",
          undefined,
          {} as unknown as E4E,
        );
        const clearConfigs = jest.fn();
        resolver["e4eDownloader"]!.clearConfigs = clearConfigs;
        resolver.clearCache();
        expect(clearConfigs).toHaveBeenCalled();
      });

      describe("listRemoteCopybooks", () => {
        let zoweExplorerApiMock: IApiRegisterClient;
        let getAllMembersMock: jest.SpyInstance<IZosFilesResponseMemberList>;
        let fileListMock: jest.SpyInstance<IZosFilesResponseFileList>;
        let datasetMembers: string[] = [];
        let ussFiles: { name: string; mode?: string }[] = [];

        beforeEach(() => {
          getAllMembersMock = jest.fn().mockResolvedValue({
            apiResponse: {
              items: datasetMembers.map((member) => ({ member: member })),
            },
          });
          fileListMock = jest.fn().mockResolvedValue({
            apiResponse: {
              items: ussFiles.map((member) => ({
                name: member.name,
                mode: member.mode ?? "-",
              })),
            },
          });

          zoweExplorerApiMock = {
            getMvsApi: () => ({
              allMembers: getAllMembersMock,
            }),
            getUssApi: () => ({
              fileList: fileListMock,
            }),
            getExplorerExtenderApi: () => ({
              getProfile: () => "profile",
              getProfilesCache: () => ({
                loadNamedProfile: () => ({ name: "profile" }),
              }),
            }),
          } as unknown as IApiRegisterClient;

          jest
            .spyOn(SettingsService, "getProfileName")
            .mockReturnValue("profile");
        });

        afterEach(() => {
          jest.restoreAllMocks();
        });

        describe("list members of the mvs dataset and uss directories", () => {
          beforeAll(() => {
            datasetMembers = ["AAA", "BBB", "XXX"];
            ussFiles = [{ name: "USSA" }, { name: "USSB" }];
            workspaceConfigurationMock = {
              "paths-dsn": ["DATASET.WITH.COPYBOOK"],
              "paths-uss": ["/user/a/copybooks"],
              "copybook-extensions": [".CPY", ".cpy", ""],
            };
          });

          it("return list of all members of the dataset", async () => {
            const cds = new CopybookDownloadService(
              "/globalStorage",
              zoweExplorerApiMock,
            );

            const results = await cds.listRemoteCopybooks(
              Uri.file("/test.cbl").toString(),
              DEFAULT_DIALECT,
            );

            expect(results).toEqual(expect.arrayContaining(datasetMembers));
            expect(results).toEqual(
              expect.arrayContaining(ussFiles.map((n) => n.name)),
            );
          });
        });

        describe("No directories and files with incorrect extension are returned from uss as copybooks for completions", () => {
          beforeAll(() => {
            datasetMembers = [];
            ussFiles = [
              { name: ".", mode: "drwxr-xr-x" },
              { name: "..", mode: "drwxr-xr-x" },
              {
                name: "CORRECT.CPY",
                mode: "-rwxr-xr-x",
              },
              {
                name: "lowercase.cpy",
                mode: "-rwxr-xr-x",
              },
              {
                name: "invalid.txt",
                mode: "-rwxr-xr-x",
              },
              {
                name: "NOEXT",
                mode: "-rwxr-xr-x",
              },
            ];
            workspaceConfigurationMock = {
              "paths-dsn": [],
              "paths-uss": ["/user/a/copybooks"],
              "copybook-extensions": [".CPY", ".cpy", ""],
            };
          });

          it("return list of all members of the dataset", async () => {
            const cds = new CopybookDownloadService(
              "/globalStorage",
              zoweExplorerApiMock,
            );

            const results = await cds.listRemoteCopybooks(
              Uri.file("/test.cbl").toString(),
              DEFAULT_DIALECT,
            );

            expect(results).toEqual(["CORRECT", "lowercase", "NOEXT"]);
          });
        });

        describe("Error handling ", () => {
          const errorMessage =
            "Rest API failure with HTTP(S) status 404 ISRZ002 Data set not cataloged - 'DATASET.WITH.COPYBOOK' was not found in catalog.";

          beforeAll(() => {
            ussFiles = [{ name: "USSA" }, { name: "USSB" }];
            workspaceConfigurationMock = {
              "paths-dsn": ["DATASET.WITH.COPYBOOK"],
              "paths-uss": ["/user/a/copybooks"],
              "copybook-extensions": [".CPY", ".cpy", ""],
            };
          });

          describe("Error in listing one directory should not affect listing other directories", () => {
            it("return list of all members of the uss, and logs error listing of the dataset", async () => {
              const outputChannelMock = { appendLine: jest.fn() };

              getAllMembersMock = jest
                .fn()
                .mockRejectedValue(new Error(errorMessage));

              const cds = new CopybookDownloadService(
                "/globalStorage",
                zoweExplorerApiMock,
                undefined,
                outputChannelMock as unknown as vscode.OutputChannel,
              );

              const results = await cds.listRemoteCopybooks(
                Uri.file("/test.cbl").toString(),
                DEFAULT_DIALECT,
              );

              expect(results).toEqual(expect.arrayContaining(datasetMembers));
              expect(results).toEqual(
                expect.arrayContaining(ussFiles.map((n) => n.name)),
              );

              expect(outputChannelMock.appendLine).toHaveBeenCalledWith(
                expect.stringContaining(errorMessage),
              );
            });
          });

          describe("Failed request to list dataset should not be repeated indefinitely", () => {
            it("Successful requests are unlimited", async () => {
              const cds = new CopybookDownloadService(
                "/globalStorage",
                zoweExplorerApiMock,
              );

              for (let attempt = 0; attempt < 10; attempt++) {
                await cds.listRemoteCopybooks(
                  Uri.file("/test.cbl").toString(),
                  DEFAULT_DIALECT,
                );
                cds.clearCache();
              }

              expect(getAllMembersMock).toHaveBeenCalledTimes(10);
            });

            it("Failing requests are blocked after n attempts", async () => {
              getAllMembersMock = jest
                .fn()
                .mockRejectedValue(new Error(errorMessage));

              const cds = new CopybookDownloadService(
                "/globalStorage",
                zoweExplorerApiMock,
              );

              for (let attempt = 0; attempt < 10; attempt++) {
                await cds.listRemoteCopybooks(
                  Uri.file("/test.cbl").toString(),
                  DEFAULT_DIALECT,
                );
              }

              expect(getAllMembersMock).toHaveBeenCalledTimes(
                FAILED_REQUESTS_LIMIT,
              );

              expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(
                `Request to list dataset members profile/DATASET.WITH.COPYBOOK keeps failing repeatedly. Disabling future requests. ${errorMessage}`,
                "Keep disabled",
                "Reenable",
              );
            });

            it("Reenable failed Zowe request command unblocks failed requests", async () => {
              getAllMembersMock = jest
                .fn()
                .mockRejectedValue(new Error(errorMessage));

              const cds = new CopybookDownloadService(
                "/globalStorage",
                zoweExplorerApiMock,
              );

              for (let attempt = 0; attempt < 10; attempt++) {
                await cds.listRemoteCopybooks(
                  Uri.file("/test.cbl").toString(),
                  DEFAULT_DIALECT,
                );
              }

              expect(getAllMembersMock).toHaveBeenCalledTimes(
                FAILED_REQUESTS_LIMIT,
              );

              cds.reenableFailedRequests();

              for (let attempt = 0; attempt < 10; attempt++) {
                await cds.listRemoteCopybooks(
                  Uri.file("/test.cbl").toString(),
                  DEFAULT_DIALECT,
                );
              }

              expect(getAllMembersMock).toHaveBeenCalledTimes(
                FAILED_REQUESTS_LIMIT * 2,
              );
            });
          });
        });

        describe("Do not require Zowe profile configuration if no remote location is configured", () => {
          beforeEach(() => {
            workspaceConfigurationMock = {
              "paths-dsn": [],
              "paths-uss": [],
              "copybook-extensions": [".CPY", ".cpy", ""],
            };
            profileName = "";
          });

          it("no error popup is shown", async () => {
            const cds = new CopybookDownloadService(
              "/globalStorage",
              zoweExplorerApiMock,
            );
            await cds.listRemoteCopybooks(
              Uri.file("/test.cbl").toString(),
              DEFAULT_DIALECT,
            );

            expect(vscode.window.showErrorMessage).not.toHaveBeenCalled();
          });
        });

        describe("Check Zowe profile configuration if remote location is configured", () => {
          beforeEach(() => {
            workspaceConfigurationMock = {
              "paths-dsn": ["DATASET.WITH.COPYBOOKS"],
              "paths-uss": [],
              "copybook-extensions": [".CPY", ".cpy", ""],
            };
            profileName = "";
          });

          it("error popup is shown", async () => {
            const cds = new CopybookDownloadService(
              "/globalStorage",
              zoweExplorerApiMock,
            );
            await cds.listRemoteCopybooks(
              Uri.file("/test.cbl").toString(),
              DEFAULT_DIALECT,
            );

            expect(vscode.window.showErrorMessage).toHaveBeenCalledWith(
              "Please specify a valid Zowe Explorer profile to download copybooks from the mainframe.",
              "Change settings",
            );
          });
        });
      });

      describe("resolveCopybookURI", () => {
        let zoweExplorerApiMock: IApiRegisterClient;
        let allMembersMock: jest.SpyInstance<IZosFilesResponseMemberList>;
        let fileListMock: jest.SpyInstance<IZosFilesResponseFileList>;
        beforeEach(() => {
          allMembersMock = jest.fn().mockImplementation((dsn) => {
            return {
              apiResponse: {
                items:
                  dsn === "DATASET.WITH.COPYBOOKS"
                    ? [{ member: "COPYBOOK" }]
                    : [],
              },
            };
          });
          fileListMock = jest.fn().mockImplementation((uss) => {
            return {
              apiResponse: {
                items:
                  uss === "/remote/uss/copybooks"
                    ? [{ name: "COPYBOOK.CPY", mode: "-" }]
                    : [],
              },
            };
          });

          zoweExplorerApiMock = {
            getMvsApi: () => ({
              allMembers: allMembersMock,
            }),
            getUssApi: () => ({
              fileList: fileListMock,
            }),
            getExplorerExtenderApi: () => ({
              getProfile: () => "profile",
              getProfilesCache: () => ({
                loadNamedProfile: () => ({ name: "profile" }),
              }),
            }),
          } as unknown as IApiRegisterClient;

          jest
            .spyOn(SettingsService, "getProfileName")
            .mockReturnValue("profile");
        });
        describe("resolve local copybooks", () => {
          describe("default COBOL dialect", () => {
            let findFilesSpy: jest.SpyInstance;

            beforeEach(() => {
              workspaceConfigurationMock = {
                "paths-local": ["copybooks"],
                "copybook-extensions": [".CPY"],
              };
              profileName = "";
              findFilesSpy = jest
                .spyOn(vscode.workspace, "findFiles")
                .mockResolvedValue([
                  vscode.Uri.parse("file:///workspace/copybooks/COPYBOOK.cpy"),
                ]);
            });

            it("local copybook workspace folder is searched", async () => {
              const cds = new CopybookDownloadService(
                "/globalStorage",
                zoweExplorerApiMock,
              );
              const result = await cds.resolveCopybookURI(
                Uri.file("/test.cbl").toString(),
                "COPYBOOK",
                DEFAULT_DIALECT,
              );

              expect(result?.toString()).toEqual(
                "file:///workspace/copybooks/COPYBOOK.cpy",
              );

              expect(findFilesSpy).toHaveBeenCalledWith({
                base: expect.objectContaining({
                  path: "/workspace/copybooks",
                  scheme: "file",
                }) as vscode.Uri,
                pattern: "{COPYBOOK.CPY}",
              });
            });
          });

          describe("local dialect copybook resolution", () => {
            let findFilesSpy: jest.SpyInstance;

            beforeEach(() => {
              workspaceConfigurationMock = {
                "paths-local": ["copybooks"],
                "dialect.paths-local": ["/dialect/copybooks"],
                "copybook-extensions": [".CPY"],
              };
              findFilesSpy = jest
                .spyOn(vscode.workspace, "findFiles")
                .mockResolvedValue([
                  vscode.Uri.parse("file:///dialect/copybooks/COPYBOOK.CPY"),
                ]);
            });
            it("uses dialect path configuration, not generic copybooks", async () => {
              const downloader = new CopybookDownloadService("/storagePath");
              const result = await downloader.resolveCopybookURI(
                Uri.file("/test.cbl").toString(),
                "COPYBOOK",
                "dialect",
              );

              expect(result?.toString()).toEqual(
                "file:///dialect/copybooks/COPYBOOK.CPY",
              );

              expect(findFilesSpy).toHaveBeenCalledTimes(1);
              expect(findFilesSpy).toHaveBeenCalledWith({
                base: expect.objectContaining({
                  path: "/dialect/copybooks",
                  scheme: "file",
                }) as vscode.Uri,
                pattern: "{COPYBOOK.CPY}",
              });
            });
          });
        });

        describe("resolve remote dsn copybooks", () => {
          beforeEach(() => {
            workspaceConfigurationMock = {
              "paths-dsn": ["OTHER.DATASET", "DATASET.WITH.COPYBOOKS"],
              "copybook-extensions": [".CPY"],
            };
            profileName = "profile";
          });

          it("zowe ds uri is constructed", async () => {
            const cds = new CopybookDownloadService(
              "/globalStorage",
              zoweExplorerApiMock,
            );
            const result = await cds.resolveCopybookURI(
              Uri.file("/test.cbl").toString(),
              "COPYBOOK",
              DEFAULT_DIALECT,
            );

            expect(result).toEqual(
              "zowe-ds:/profile/DATASET.WITH.COPYBOOKS/COPYBOOK",
            );
          });
        });

        describe("resolve remote uss copybooks", () => {
          beforeEach(() => {
            workspaceConfigurationMock = {
              "paths-uss": ["/user/copybooks", "/remote/uss/copybooks"],
              "copybook-extensions": [".CPY"],
            };
            profileName = "profile";
          });

          it("zowe ds uri is constructed", async () => {
            const cds = new CopybookDownloadService(
              "/globalStorage",
              zoweExplorerApiMock,
            );
            const result = await cds.resolveCopybookURI(
              Uri.file("/test.cbl").toString(),
              "COPYBOOK",
              DEFAULT_DIALECT,
            );

            expect(result).toEqual(
              "zowe-uss:/profile/remote/uss/copybooks/COPYBOOK.CPY",
            );
          });
        });

        describe("resolve remote endevor copybooks", () => {
          describe("endevor members", () => {
            let dataset: string;
            let e4eMock: E4E;
            let cachedFileExists: boolean = false;

            beforeEach(() => {
              workspaceConfigurationMock = {
                [SETTINGS_CPY_NDVR_DEPENDENCIES]: ENDEVOR_PROCESSOR,
                ["compiler"]: "",
                ["preprocessors"]: [],
              };
              dataset = "ENDEVOR.DATASET.COPYBOOK";

              e4eMock = {
                isEndevorElement: jest.fn().mockResolvedValue(true),
                getProfileInfo: jest.fn().mockResolvedValue({
                  profile: "profile",
                  instance: "instance",
                }),
                listElements: jest.fn().mockResolvedValue([]),
                getElement: jest.fn(),
                listMembers: jest.fn().mockResolvedValue(["COPYBOOK"]),
                getMember: jest.fn().mockResolvedValue([]),
                getConfiguration: jest.fn().mockResolvedValue({
                  pgms: [{ pgroup: "pgroup" }],
                  pgroups: [
                    {
                      name: "pgroup",
                      libs: [{ dataset }],
                    },
                  ],
                }),
                onDidChangeElement: jest.fn(),
              };

              jest
                .spyOn(vscode.workspace.fs, "stat")
                .mockImplementation(async (_uri: vscode.Uri) => {
                  if (!cachedFileExists) {
                    throw new FileNotFound();
                  }
                  return Promise.resolve({} as vscode.FileStat);
                });
            });

            it("local cached of copybook uri is returned", async () => {
              const cds = new CopybookDownloadService(
                "/globalStorage",
                undefined,
                e4eMock,
              );
              const documentUri = Uri.file("/test.cbl").toString();
              const result = await cds.resolveCopybookURI(
                documentUri,
                "COPYBOOK",
                DEFAULT_DIALECT,
              );

              expect(vscode.workspace.fs.writeFile).toHaveBeenCalledWith(
                expect.objectContaining({
                  scheme: "file",
                  path: "/globalStorage/e4e/copybooks/instance.profile/ENDEVOR.DATASET.COPYBOOK/COPYBOOK",
                }),
                expect.anything(),
              );

              expect(result).toEqual(
                expect.stringMatching(
                  "file:///globalStorage/e4e/copybooks/instance.profile/ENDEVOR.DATASET.COPYBOOK/COPYBOOK",
                ),
              );
            });

            it("if copybook is requested multiple times, E4E is called just once", async () => {
              const cds = new CopybookDownloadService(
                "/globalStorage",
                undefined,
                e4eMock,
              );
              const documentUri = Uri.file("/test.cbl").toString();
              const resultFirst = await cds.resolveCopybookURI(
                documentUri,
                "COPYBOOK",
                DEFAULT_DIALECT,
              );
              expect(resultFirst).toEqual(
                expect.stringMatching(
                  "file:///globalStorage/e4e/copybooks/instance.profile/ENDEVOR.DATASET.COPYBOOK/COPYBOOK",
                ),
              );
              expect(e4eMock.getMember).toHaveBeenCalledTimes(1);

              cachedFileExists = true;

              const resultSecond = await cds.resolveCopybookURI(
                documentUri,
                "COPYBOOK",
                DEFAULT_DIALECT,
              );
              expect(resultSecond).toEqual(resultFirst);
              expect(e4eMock.getMember).toHaveBeenCalledTimes(1);
            });
          });

          describe("endevor elements", () => {
            let e4eMock: E4E;
            let cachedFileExists: boolean = false;

            beforeEach(() => {
              workspaceConfigurationMock = {
                [SETTINGS_CPY_NDVR_DEPENDENCIES]: ENDEVOR_PROCESSOR,
                ["compiler"]: "",
                ["preprocessors"]: [],
              };

              e4eMock = {
                isEndevorElement: jest.fn().mockResolvedValue(true),
                getProfileInfo: jest.fn().mockResolvedValue({
                  profile: "profile",
                  instance: "instance",
                }),
                listElements: jest
                  .fn()
                  .mockResolvedValue([["COPYBOOK", "12345"]]),
                getElement: jest.fn().mockResolvedValue([""]),
                listMembers: jest.fn().mockResolvedValue([]),
                getMember: jest.fn().mockResolvedValue([]),
                getConfiguration: jest.fn().mockResolvedValue({
                  pgms: [{ pgroup: "pgroup" }],
                  pgroups: [
                    {
                      name: "pgroup",
                      libs: [
                        {
                          use_map: false,
                          environment: "environment",
                          stage: "stage",
                          system: "system",
                          subsystem: "subsystem",
                          type: "type",
                        },
                      ],
                    },
                  ],
                }),
                onDidChangeElement: jest.fn(),
              };

              jest
                .spyOn(vscode.workspace.fs, "stat")
                .mockImplementation(async (_uri: vscode.Uri) => {
                  if (!cachedFileExists) {
                    throw new FileNotFound();
                  }
                  return Promise.resolve({} as vscode.FileStat);
                });
            });

            it("local cached of copybook uri is returned", async () => {
              const cds = new CopybookDownloadService(
                "/globalStorage",
                undefined,
                e4eMock,
              );
              const documentUri = Uri.file("/test.cbl").toString();
              const result = await cds.resolveCopybookURI(
                documentUri,
                "COPYBOOK",
                DEFAULT_DIALECT,
              );

              expect(vscode.workspace.fs.writeFile).toHaveBeenCalledWith(
                expect.objectContaining({
                  scheme: "file",
                  path: "/globalStorage/e4e/copybooks/instance.profile/environment/stage/system/subsystem/type/COPYBOOK",
                }),
                expect.anything(),
              );

              expect(result).toEqual(
                expect.stringMatching(
                  "file:///globalStorage/e4e/copybooks/instance.profile/environment/stage/system/subsystem/type/COPYBOOK",
                ),
              );
            });

            it("if cached version is available e4e is not called again", async () => {
              cachedFileExists = true;
              const cds = new CopybookDownloadService(
                "/globalStorage",
                undefined,
                e4eMock,
              );
              const documentUri = Uri.file("/test.cbl").toString();
              const result = await cds.resolveCopybookURI(
                documentUri,
                "COPYBOOK",
                DEFAULT_DIALECT,
              );

              expect(vscode.workspace.fs.writeFile).not.toHaveBeenCalled();
              expect(e4eMock.getElement).not.toHaveBeenCalled();

              expect(result).toEqual(
                expect.stringMatching(
                  "file:///globalStorage/e4e/copybooks/instance.profile/environment/stage/system/subsystem/type/COPYBOOK",
                ),
              );
            });
          });

          describe("don't resolve endevor copybooks if SETTINGS_CPY_NDVR_DEPENDENCIES is set to ZOWE", () => {
            it(() => {
              throw new Error("TODO");
            });
          });
        });

        describe("resolve processor group copybooks", () => {
          describe("copybooks search respects processor group definitions order", () => {
            it(() => {
              throw new Error("TODO");
              // VV Original version of the test
              // const filename = "cobolFileName";
              // const group = "group";
              // const profile = { profile: "profile", instance: "instance" };
              // const datasetFirst = {
              //   pgms: [
              //     {
              //       program: filename,
              //       pgroup: group,
              //     },
              //   ],
              //   pgroups: [
              //     {
              //       name: group,
              //       libs: [
              //         {
              //           dataset: "dataset",
              //         },
              //         {
              //           use_map: false,
              //           environment: "environment",
              //           stage: "stage",
              //           system: "system",
              //           subsystem: "subsystem",
              //           type: "type",
              //         },
              //       ],
              //     },
              //   ],
              // };
              // const endevorFirst = {
              //   pgms: [
              //     {
              //       program: filename,
              //       pgroup: group,
              //     },
              //   ],
              //   pgroups: [
              //     {
              //       name: group,
              //       libs: [
              //         {
              //           use_map: false,
              //           environment: "environment",
              //           stage: "stage",
              //           system: "system",
              //           subsystem: "subsystem",
              //           type: "type",
              //         },
              //         {
              //           dataset: "dataset",
              //         },
              //       ],
              //     },
              //   ],
              // };

              // const unreachable = jest.fn();
              // const listMembers = jest.fn(async () => Promise.resolve(["copybook"]));
              // const listElements = jest.fn(async () =>
              //   Promise.resolve([
              //     ["copybook", "abcdef0123456789"],
              //     ["copybook2", "0123456789abcdef"],
              //   ] as [string, string][]),
              // );

              // it("checks copybooks search respects processor group definitions order", async () => {
              //     SettingsService.getCopybookExtension = jest
              //       .fn()
              //       .mockReturnValue(Promise.resolve([""]));
              //     SettingsService.getCopybookLocalPath = jest
              //       .fn()
              //       .mockReturnValue(Promise.resolve([]));
              //     SettingsService.getDsnPath = jest
              //       .fn()
              //       .mockReturnValue(["/configured/path"]);

              //     const zoweApi: IApiRegisterClient = {
              //       getExplorerExtenderApi: unreachable,
              //       getUssApi: unreachable,
              //       getMvsApi: unreachable,
              //       registeredApiTypes: unreachable,
              //     };

              //     const downloader = new CopybookDownloadService("/storagePath", zoweApi, {
              //       isEndevorElement(_uri: string) {
              //         return false;
              //       },
              //       onDidChangeElement: unreachable,
              //       listMembers,
              //       listElements,
              //       getMember: unreachable,
              //       getElement: unreachable,
              //       async getProfileInfo(_uri) {
              //         return Promise.resolve({
              //           instance: "instance",
              //           profile: "profile",
              //         });
              //       },
              //       getConfiguration: unreachable,
              //     });
              //     SettingsService.getUssPath = jest.fn().mockReturnValue(["uss/path"]);
              //     SettingsService.getDsnPath = jest.fn().mockReturnValue(["dsn/path"]);
              //     downloader["dsnDownloader"]!.hasMember = jest.fn().mockResolvedValue(false);
              //     downloader["ussDownloader"]!.hasMember = jest.fn().mockResolvedValue(true);
              //     downloader["e4eDownloader"]!.hasElement = jest.fn().mockResolvedValue(true);
              //     const searchSpy = jest.spyOn(fsUtils, "searchCopybookInExtensionFolder");
              //     searchSpy.mockReturnValue(undefined);

              //     const spyConfig = jest.spyOn(
              //       ProcessorGroups,
              //       "loadProcessorGroupCopybookPathsConfig",
              //     );
              //     spyConfig.mockResolvedValue([
              //       "/libs",
              //       { dataset: "procGroupDataset", profile: "procGroupProfile" },
              //       {
              //         environment: "environment",
              //         system: "system",
              //         subsystem: "subsystem",
              //         stage: "1",
              //         type: "copy",
              //         profile: "instance@profile",
              //       },
              //       { uss: "ussFile", profile: "profile" },
              //     ]);

              //     await downloader.resolveCopybookHandler(
              //       "file:///cobolFileName",
              //       "copybookName",
              //       "dialectType",
              //     );

              //     expect(searchSpy).toHaveBeenNthCalledWith(
              //       1,
              //       "copybookName",
              //       ["/libs"],
              //       [""],
              //       "/storagePath",
              //     );
              //     expect(searchSpy).toHaveBeenNthCalledWith(
              //       2,
              //       "copybookName",
              //       [
              //         "/storagePath/e4e/copybooks/instance.profile/environment/1/system/subsystem/copy/MAP",
              //       ],
              //       [""],
              //       "/storagePath",
              //     );
              //   });
            });
          });
        });

        describe("resolve copybooks when no configuration is provided in settings", () => {
          it(() => {
            throw new Error("TODO");
            // globSyncMockResult = [];
            // settingsMockProperties = {};

            // ProfileUtils.getProfileNameForCopybook = jest
            //   .fn()
            //   .mockReturnValue(undefined);
            // const downloader = new CopybookDownloadService(
            //   "/storagePath",
            //   undefined,
            //   undefined,
            // );
            // const uri: string | undefined = await downloader.resolveCopybookHandler(
            //   "file:///" + copybookName,
            //   "PRGNAME",
            //   "COBOL",
            // );
            // expect(uri).toBe(undefined);

            // expect(spySearchInWorkspace).toHaveBeenCalledTimes(7);
          });
        });

        describe("resolution priorities", () => {
          describe("endevor program & endevor dependency setting = ENDEVOR_PROCESSOR", () => {
            describe("only endevor is used to resolve copybooks, zowe not called", () => {
              it(() => {
                throw new Error("TODO");
              });
            });

            describe("if not found in endevor, other locations are not searched", () => {
              it(() => {
                throw new Error("TODO");
              });
            });
          });

          describe("endevor program & endevor dependency setting = ZOWE", () => {
            describe("local & zowe copybooks are used for resolution", () => {
              it(() => {
                throw new Error("TODO");
              });
            });
          });

          describe("processor groups", () => {
            it(() => {
              throw new Error("TODO");
            });

            //it("checks dsn settings used when no settings provided in processor group definitions", async () => {
            //   const spyConfig = jest.spyOn(
            //     ProcessorGroups,
            //     "loadProcessorGroupCopybookPathsConfig",
            //   );
            //   spyConfig.mockResolvedValue([]);
            //   workspaceConfigurationMock[PATHS_DSN] = ["dsn"];

            //   const downloader = new CopybookDownloadService(
            //     "storage-path",
            //     zoweExplorerMock,
            //     undefined,
            //   );
            //   downloader["dsnDownloader"]!.downloadCopybook = jest
            //     .fn()
            //     .mockReturnValue(true);
            //   downloader["ussDownloader"]!.downloadCopybook = jest.fn();

            //   await downloader.downloadCopybook(
            //     { name: "copybook", dialect: "COBOL" },
            //     "file://document-uri",
            //   );
            //   expect(
            //     downloader["dsnDownloader"]!.downloadCopybook,
            //   ).toHaveBeenCalledWith(
            //     { name: "copybook", dialect: "COBOL" },
            //     "dsn",
            //     "profile",
            //   );
            // });

            // it("checks settings in processor groups used first", async () => {
            //   const settingsDsnSpy = jest.spyOn(SettingsService, "getDsnPath");
            //   const settingsUssSpy = jest.spyOn(SettingsService, "getDsnPath");

            //   const spyConfig = jest.spyOn(
            //     ProcessorGroups,
            //     "loadProcessorGroupCopybookPathsConfig",
            //   );
            //   spyConfig.mockResolvedValue([
            //     "/libs",
            //     { dataset: "procGroupDataset", profile: "procGroupProfile" },
            //     { uss: "uss", profile: "profile" },
            //   ]);

            //   const downloader = new CopybookDownloadService(
            //     "storage-path",
            //     zoweExplorerMock,
            //     undefined,
            //   );
            //   downloader["dsnDownloader"]!.downloadCopybook = jest
            //     .fn()
            //     .mockReturnValue(true);

            //   await downloader.downloadCopybook(
            //     { name: "copybook", dialect: "COBOL" },
            //     "file://document-uri",
            //   );
            //   expect(
            //     downloader["dsnDownloader"]!.downloadCopybook,
            //   ).toHaveBeenCalledWith(
            //     { name: "copybook", dialect: "COBOL" },
            //     "procGroupDataset",
            //     "procGroupProfile",
            //   );
            //   expect(settingsDsnSpy).toHaveBeenCalledTimes(0);
            //   expect(settingsUssSpy).toHaveBeenCalledTimes(0);
            // });

            // it("checks settings in processor group definitions used in order ", async () => {
            //   const spyConfig = jest.spyOn(
            //     ProcessorGroups,
            //     "loadProcessorGroupCopybookPathsConfig",
            //   );
            //   spyConfig.mockResolvedValue([
            //     { uss: "ussFile", profile: "profile" },
            //     { dataset: "procGroupDataset", profile: "procGroupProfile" },
            //     "/libs",
            //   ]);

            //   const downloader = new CopybookDownloadService(
            //     "storage-path",
            //     zoweExplorerMock,
            //     undefined,
            //   );
            //   downloader["dsnDownloader"]!.downloadCopybook = jest
            //     .fn()
            //     .mockReturnValue(true);
            //   downloader["ussDownloader"]!.downloadCopybook = jest
            //     .fn()
            //     .mockReturnValue(true);

            //   await downloader.downloadCopybook(
            //     { name: "copybook", dialect: "COBOL" },
            //     "file://document-uri",
            //   );
            //   expect(
            //     downloader["ussDownloader"]!.downloadCopybook,
            //   ).toHaveBeenCalledWith(
            //     { name: "copybook", dialect: "COBOL" },
            //     "ussFile",
            //     "profile",
            //     [".CPY", ".cpy", ""],
            //   );
            // });

            // it("checks download does not perform when endevor location settings in processor group has invalid profile", async () => {
            //   const spyConfig = jest.spyOn(
            //     ProcessorGroups,
            //     "loadProcessorGroupCopybookPathsConfig",
            //   );
            //   spyConfig.mockResolvedValue([
            //     {
            //       environment: "environment",
            //       system: "system",
            //       subsystem: "subsystem",
            //       stage: "stage",
            //       type: "type",
            //       profile: "invalid@invalid",
            //     },
            //   ]);

            //   const downloader = new CopybookDownloadService(
            //     "storage-path",
            //     undefined,
            //     e4eMockInvalidProfile,
            //   );

            //   const spyDownloadElement = (downloader[
            //     "e4eDownloader"
            //   ]!.downloadElementE4E = jest.fn().mockResolvedValue(false));

            //   await downloader.downloadCopybook(
            //     { name: "copybook", dialect: "COBOL" },
            //     "file://document-uri",
            //   );
            //   expect(spyDownloadElement).toHaveBeenCalledTimes(0);
            // });
            // it("checks endevor locations in proccesor groups definitions resolves prerequiste", async () => {
            //   workspaceConfigurationMock[PATHS_DSN] = [];
            //   workspaceConfigurationMock[PATHS_USS] = [];

            //   const service = new CopybookDownloadService(
            //     "storage-path",
            //     zoweExplorerMock,
            //     e4eMock,
            //   );
            //   const spyConfig = jest.spyOn(
            //     ProcessorGroups,
            //     "loadProcessorGroupCopybookPathsConfig",
            //   );
            //   spyConfig.mockResolvedValue([
            //     {
            //       environment: "environment",
            //       stage: "1",
            //       system: "system",
            //       subsystem: "subsystem",
            //       type: "type",
            //     },
            //   ]);
            //   const downloadSpy = jest.spyOn(
            //     service["e4eDownloader"]!,
            //     "downloadElementE4E",
            //   );
            //   await service.downloadCopybooks("file://document-uri", [
            //     { name: "copybook", dialect: DEFAULT_DIALECT },
            //   ]);
            //   expect(downloadSpy).toHaveBeenCalledWith(
            //     { instance: "instance", profile: "profile" },
            //     {
            //       element: "COPYBOOK",
            //       environment: "environment",
            //       fingerprint: "",
            //       stage: "1",
            //       subsystem: "subsystem",
            //       system: "system",
            //       type: "type",
            //       use_map: true,
            //     },
            //   );
            // });
          });

          describe("With both local and zowe locations defined in the settings.json, the search is applied on local resources first", () => {
            it(() => {
              throw new Error("TODO");
            });
          });

          describe("DSN has priority over USS", () => {
            it(() => {
              throw new Error("TODO");
            });
          });

          describe("USS is not called if DSN resolves", () => {
            it(() => {
              throw new Error("TODO");
            });
          });

          describe("the order of resolution is same as the one provided in user settings", () => {
            // beforeAll(() => {
            //   workspaceConfigurationMock = {
            //     [PATHS_DSN]: ["dsn", "dsn-2"],
            //     [PATHS_USS]: ["uss"],
            //   };
            // });
            // it("checks the order of resolution is same as the one provided in user settings", async () => {
            //   const downloader = new CopybookDownloadService(
            //     "storage-path",
            //     zoweExplorerMock,
            //     undefined,
            //   );
            //   downloader["dsnDownloader"]!.downloadCopybook = jest
            //     .fn()
            //     .mockReturnValueOnce(false)
            //     .mockReturnValue(true);
            //   downloader["ussDownloader"]!.downloadCopybook = jest.fn();
            //   await downloader.downloadCopybook(
            //     { name: "copybook", dialect: "COBOL" },
            //     "file://document-uri",
            //   );
            //   expect(
            //     downloader["dsnDownloader"]!.downloadCopybook,
            //   ).toHaveBeenCalledWith(
            //     { name: "copybook", dialect: "COBOL" },
            //     "dsn",
            //     "profile",
            //   );
            //   expect(
            //     downloader["dsnDownloader"]!.downloadCopybook,
            //   ).toHaveBeenCalledWith(
            //     { name: "copybook", dialect: "COBOL" },
            //     "dsn-2",
            //     "profile",
            //   );
            // });
          });
        });
      });
    });
  });
});
