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
import * as t from "io-ts";
import { isLeft } from "fp-ts/Either";
import { PathReporter } from "io-ts/PathReporter";
import { workspace, Uri, FileSystemWatcher } from "vscode";
import { TextDecoder } from "util";

const ElementMetadata = t.type({
  processorGroup: t.string,
});

const ProcessorGroupDefinition = t.type({
  name: t.string,
  description: t.string,
});

const B4GTypeMetadataModel = t.type({
  elements: t.record(t.string, ElementMetadata),
  defaultProcessorGroup: t.string,
  definedProcessorGroups: t.array(ProcessorGroupDefinition),
  fileExtension: t.string,
});

const docToBridgeJsonContentMap: Map<string, B4GTypeMetadata | undefined> =
  new Map();
const bridgeJsonToDocUriMap: Map<string, string[]> = new Map();
export type B4GTypeMetadata = t.TypeOf<typeof B4GTypeMetadataModel>;

const BRIDGE4GIT_CONFIG_FILE = "**/.bridge.json";

export function decodeBridgeJson(json: unknown): B4GTypeMetadata | undefined {
  if (json === undefined) {
    return undefined;
  }
  try {
    const decoded = B4GTypeMetadataModel.decode(json); // Either<Errors, User>
    if (isLeft(decoded)) {
      throw Error(
        `Could not validate data: ${PathReporter.report(decoded).join("\n")}`,
      );
    }
    return decoded.right;
  } catch (e) {
    console.error(e);
    return undefined;
  }
}

export async function loadBridgeJsonContent(
  documentUri: Uri,
): Promise<unknown> {
  const docUriString = documentUri.toString();
  if (docToBridgeJsonContentMap.has(docUriString)) {
    return docToBridgeJsonContentMap.get(docUriString);
  }
  const b4gPath = Uri.joinPath(documentUri, "../.bridge.json");
  try {
    const bridge4GitDataJson: B4GTypeMetadata | undefined =
      await readBridge4GitJson(b4gPath);
    updateCache(documentUri, bridge4GitDataJson, b4gPath);
    return bridge4GitDataJson;
  } catch (e) {
    updateCache(documentUri, undefined, b4gPath);
    if (
      e &&
      typeof e === "object" &&
      "code" in e &&
      e.code !== "FileNotFound"
    ) {
      console.error(e);
    }
    return undefined;
  }
}
const watcherChangeEventHandler = async (uri: Uri) => {
  if (bridgeJsonToDocUriMap.has(uri.toString())) {
    await reloadBridgeJsonContent(uri);
  }
};

async function readBridge4GitJson(b4gPath: Uri) {
  const bridge4GitDataString = new TextDecoder().decode(
    await workspace.fs.readFile(b4gPath),
  );
  const bridge4GitDataJson: unknown = JSON.parse(bridge4GitDataString);
  return decodeBridgeJson(bridge4GitDataJson);
}

export function setupBridge4GitWatcher(): FileSystemWatcher {
  const bridge4GitWatcher = workspace.createFileSystemWatcher(
    BRIDGE4GIT_CONFIG_FILE,
  );
  const events: Array<
    keyof Pick<FileSystemWatcher, "onDidChange" | "onDidCreate" | "onDidDelete">
  > = ["onDidChange", "onDidCreate", "onDidDelete"];
  events.forEach((event) =>
    bridge4GitWatcher[event](watcherChangeEventHandler),
  );
  return bridge4GitWatcher;
}

async function reloadBridgeJsonContent(b4gPath: Uri) {
  try {
    const bridge4GitDataJson: B4GTypeMetadata | undefined =
      await readBridge4GitJson(b4gPath);
    reloadCache(bridge4GitDataJson, b4gPath);
  } catch (e) {
    reloadCache(undefined, b4gPath);
    if (
      e &&
      typeof e === "object" &&
      "code" in e &&
      e.code !== "FileNotFound"
    ) {
      console.error(e);
    }
    return undefined;
  }
}

function updateCache(
  documentUri: Uri,
  result: B4GTypeMetadata | undefined,
  b4gPath: Uri,
) {
  const documentUriString = documentUri.toString();
  const b4gPathString = b4gPath.toString();

  docToBridgeJsonContentMap.set(documentUriString, result);

  if (bridgeJsonToDocUriMap.has(b4gPathString)) {
    const existingArray = bridgeJsonToDocUriMap.get(b4gPathString)!;
    if (!existingArray.includes(documentUriString)) {
      existingArray.push(documentUriString);
    }
  } else {
    bridgeJsonToDocUriMap.set(b4gPathString, [documentUriString]);
  }
}

function reloadCache(result: B4GTypeMetadata | undefined, b4gPath: Uri) {
  if (bridgeJsonToDocUriMap.has(b4gPath.toString())) {
    const existingDoc = bridgeJsonToDocUriMap.get(b4gPath.toString())!;
    docToBridgeJsonContentMap.forEach((_val, key) => {
      if (existingDoc.includes(key.toString())) {
        docToBridgeJsonContentMap.set(key, result);
      }
    });
  } else {
    bridgeJsonToDocUriMap.set(b4gPath.toString(), []);
  }
}
