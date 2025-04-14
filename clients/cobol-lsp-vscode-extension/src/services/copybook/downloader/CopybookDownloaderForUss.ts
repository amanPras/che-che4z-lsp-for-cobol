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
import { SettingsService } from "../../Settings";
import { splitFilename } from "../../util/FSUtils";
import { CopybookName } from "../CopybookDownloadService";
import { DownloadUtil } from "./DownloadUtil";
import {
  MemberCacheItem,
  ZoweExplorerDownloader,
} from "./ZoweExplorerDownloader";
import * as vscode from "vscode";

/**
 * Copybook downloader from USS using Zowe Explorer
 */
export class CopybookDownloaderForUss extends ZoweExplorerDownloader {
  constructor(storagePath: string, explorerAPI: IApiRegisterClient) {
    super(storagePath, explorerAPI);
  }

  /**
   * Downloads a file from USS using Zowe explorer
   *
   * @param copybookName Copybook to be downloaded.
   * @param ussPath ussPath in mainframe.
   * @param profile zowe profile name
   */
  async downloadCopybook(
    copybookName: CopybookName,
    ussPath: string,
    profile: string,
    _extensions: string[],
  ): Promise<boolean> {
    const has = await this.hasMember(profile, ussPath, copybookName.name);
    const memberList = await this.getAllMembers(profile, ussPath);
    const remoteCopybook = DownloadUtil.getRemoteCopybookName(
      memberList.map((m) => m.name),
      copybookName.name,
    );
    return !!(
      remoteCopybook &&
      has &&
      (await this.downloadCopybookFromMFUsingZowe(
        ussPath,
        remoteCopybook,
        profile,
      ))
    );
  }

  public async getAllMembers(
    profileName: string,
    dataset: string,
  ): Promise<MemberCacheItem[]> {
    const id = this.createId(profileName, dataset);

    if (this.memberListCache.has(id)) {
      return this.memberListCache.get(id)!;
    }

    const profile = DownloadUtil.loadProfile(profileName, this.explorerAPI);

    const allowedCopybooksExtensions =
      await SettingsService.getCopybookExtension();
    const allowedNoExtension = allowedCopybooksExtensions?.includes("");

    const members: MemberCacheItem[] = [];

    await this.limitFailedRequests(
      `list USS directory ${profileName}/${dataset}`,
      async () => {
        const response = await this.explorerAPI
          .getUssApi(profile)
          .fileList(dataset);

        for (const file of response.apiResponse.items) {
          if (file.mode.charAt(0) === "-") {
            const [name, extension] = splitFilename(file.name);

            if (extension) {
              if (allowedCopybooksExtensions?.includes(extension)) {
                members.push({ name, extension });
              }
            } else if (allowedNoExtension) {
              members.push({ name });
            }
          }
        }
      },
    );

    this.memberListCache.set(id, members);
    return members;
  }

  /**
   * Downloads file using Zowe explorer from USS based on passed parameters
   * @param dataset  dataset name
   * @param member member name
   * @param profileName ZE profile name
   */
  override async downloadCopybookContent(
    dataset: string,
    member: string,
    profileName: string,
  ): Promise<boolean> {
    const loadedProfile = DownloadUtil.loadProfile(
      profileName,
      this.explorerAPI,
    );
    const downloadOptions = this.getDownloadOptions(
      profileName,
      dataset,
      member,
      loadedProfile,
    );

    await this.explorerAPI
      .getUssApi(loadedProfile)
      .getContents(`${dataset}/${member}`, downloadOptions.apiOptions);

    if (downloadOptions.decode) {
      await this.decodeBinaryContent(
        downloadOptions.fileUri,
        downloadOptions.decode,
      );
    }

    return true;
  }

  public async hasMember(
    profileName: string,
    uss: string,
    copybookName: string,
  ): Promise<boolean> {
    const members = await this.getAllMembers(profileName, uss);

    if (members.some((member) => member.name.toUpperCase() === copybookName)) {
      return true;
    }
    return false;
  }

  public async resolveCopybookUri(
    profileName: string,
    uss: string,
    copybookName: string,
  ) {
    const memberList = await this.getAllMembers(profileName, uss);
    const member = memberList.find((m) => m.name === copybookName);

    if (member) {
      return vscode.Uri.parse(
        `zowe-uss:/${profileName}${uss}/${member.name}${member.extension}`,
      );
    }
  }
}
