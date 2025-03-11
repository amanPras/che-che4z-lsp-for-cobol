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
import { workspace, Uri } from "vscode";
import { PathReporter } from "io-ts/lib/PathReporter";
import { isLeft } from "fp-ts/Either";
import { TextDecoder } from "util";

const PG_FOLDER = ".cobolplugin";
const PGR_PGM_FILE = "pgm_conf.json";
const PG_PROC_FILE = "proc_grps.json";
const DEFAULT_PROCESSOR_GROUP_CONFIG = "[]";
const DEFAULT_PROCESSOR_CONFIG = '{ "pgms": [] }';
type WorkspaceConfigEntry =
  | [string | undefined, string | undefined]
  | undefined;
const workspaceConfiguration: Map<string, WorkspaceConfigEntry> = new Map();

enum ProcessorIndex {
  PROCESSOR_GROUP = 0,
  PROCESSOR_CONFIG = 1,
}
const ProgramsConfigModel = t.type({
  pgms: t.array(
    t.type({
      program: t.string,
      pgroup: t.string,
    }),
  ),
});

export type ProgramsConfig = t.TypeOf<typeof ProgramsConfigModel>;

const PreprocessorModel = t.union([
  t.string,
  t.intersection([
    t.type({ name: t.string }),
    t.partial({
      libs: t.array(t.string),
      "copybook-extensions": t.array(t.string),
      "compiler-options": t.array(t.string),
      "copybook-file-encoding": t.string,
      "target-sql-backend": t.string,
    }),
  ]),
]);

export type Preprocessor = t.TypeOf<typeof PreprocessorModel>;

const ProcessorGroupModel = t.intersection([
  t.type({
    name: t.string,
  }),
  t.partial({
    preprocessor: t.union([PreprocessorModel, t.array(PreprocessorModel)]),
    libs: t.array(t.string),
    "copybook-extensions": t.array(t.string),
    "compiler-options": t.array(t.string),
    "copybook-file-encoding": t.string,
    "target-sql-backend": t.string,
  }),
]);

export type ProcessorGroup = t.TypeOf<typeof ProcessorGroupModel>;

export async function readProgramConfigFileContent(
  documentUri: Uri,
): Promise<ProgramsConfig> {
  const EMPTY = { pgms: [] };

  const ws = workspace.getWorkspaceFolder(documentUri);
  if (ws === undefined) {
    return EMPTY;
  }
  const wsUriString = ws.uri.toString();
  const pgmCfgPath = Uri.joinPath(ws.uri, PG_FOLDER, PGR_PGM_FILE);
  let programConfig: string;
  const cachedValue = workspaceConfiguration.get(wsUriString);

  if (cachedValue && cachedValue[ProcessorIndex.PROCESSOR_CONFIG]) {
    programConfig = cachedValue[ProcessorIndex.PROCESSOR_CONFIG];
  } else {
    programConfig = await readFileAndCache(
      wsUriString,
      pgmCfgPath,
      ProcessorIndex.PROCESSOR_CONFIG,
    );
  }
  const json: unknown = JSON.parse(programConfig);
  const decoded = ProgramsConfigModel.decode(json);
  if (isLeft(decoded)) {
    throw Error(
      `Could not validate data: ${PathReporter.report(decoded).join("\n")}`,
    );
  }
  return decoded.right;
}

export async function readProcessorGroupsFileContent(
  documentUri: Uri,
): Promise<ProcessorGroup[]> {
  const ws = workspace.getWorkspaceFolder(documentUri);
  if (ws === undefined) {
    return [];
  }

  const wsUriString = ws.uri.toString();
  const procCfgPath = Uri.joinPath(ws.uri, PG_FOLDER, PG_PROC_FILE);
  try {
    const ProcessorGrpupsModel = t.type({
      pgroups: t.array(ProcessorGroupModel),
    });
    let processorGroupConfig: string;
    const cachedValue = workspaceConfiguration.get(wsUriString);

    if (cachedValue && cachedValue[ProcessorIndex.PROCESSOR_GROUP]) {
      processorGroupConfig = cachedValue[ProcessorIndex.PROCESSOR_GROUP];
    } else {
      processorGroupConfig = await readFileAndCache(
        wsUriString,
        procCfgPath,
        ProcessorIndex.PROCESSOR_GROUP,
      );
    }
    const json: unknown = JSON.parse(processorGroupConfig);

    const decoded = ProcessorGrpupsModel.decode(json);
    if (isLeft(decoded)) {
      throw Error(
        `Could not validate data: ${PathReporter.report(decoded).join("\n")}`,
      );
    }
    return decoded.right.pgroups;
  } catch (e) {
    if (
      e &&
      typeof e === "object" &&
      "code" in e &&
      e.code !== "FileNotFound"
    ) {
      console.error(e);
    }
    return [];
  }
}

async function readFileAndCache(
  wsUriString: string,
  pgmCfgPath: Uri,
  cacheIndex: ProcessorIndex,
): Promise<string> {
  try {
    const fileContent = new TextDecoder().decode(
      await workspace.fs.readFile(pgmCfgPath),
    );
    // Update the cache with the new value
    const config = workspaceConfiguration.get(wsUriString);
    const newConfig: WorkspaceConfigEntry =
      cacheIndex === ProcessorIndex.PROCESSOR_GROUP
        ? [
            fileContent,
            config ? config[ProcessorIndex.PROCESSOR_CONFIG] : undefined,
          ]
        : [
            config ? config[ProcessorIndex.PROCESSOR_GROUP] : undefined,
            fileContent,
          ];
    workspaceConfiguration.set(wsUriString, newConfig);
    return fileContent;
  } catch (e) {
    if (
      e &&
      typeof e === "object" &&
      "code" in e &&
      e.code !== "FileNotFound"
    ) {
      console.error(e);
    }
    if (!workspaceConfiguration.has(wsUriString)) {
      const emptyValue: WorkspaceConfigEntry =
        cacheIndex === ProcessorIndex.PROCESSOR_GROUP
          ? [DEFAULT_PROCESSOR_GROUP_CONFIG, undefined]
          : [undefined, DEFAULT_PROCESSOR_CONFIG];
      workspaceConfiguration.set(wsUriString, emptyValue);
    } else {
      const config = workspaceConfiguration.get(wsUriString);
      if (config) {
        config[cacheIndex] =
          cacheIndex === ProcessorIndex.PROCESSOR_GROUP
            ? DEFAULT_PROCESSOR_GROUP_CONFIG
            : DEFAULT_PROCESSOR_CONFIG;
        workspaceConfiguration.set(wsUriString, config);
      }
    }
    return cacheIndex === ProcessorIndex.PROCESSOR_GROUP
      ? DEFAULT_PROCESSOR_GROUP_CONFIG
      : DEFAULT_PROCESSOR_CONFIG;
  }
}
