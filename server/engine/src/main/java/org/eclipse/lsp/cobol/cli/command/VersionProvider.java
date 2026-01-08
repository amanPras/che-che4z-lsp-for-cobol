/*
 * Copyright (c) 2026 Broadcom.
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
package org.eclipse.lsp.cobol.cli.command;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import picocli.CommandLine;

/** Reads version information */
public class VersionProvider implements CommandLine.IVersionProvider {

  public static final String IMPLEMENTATION_TITLE = "Implementation-Title";
  public static final String SEPARATOR = " : ";
  public static final String IMPLEMENTATION_VERSION = "Implementation-Version";
  public static final String LATEST_CLIENT_VERSION = "Latest-client-version";
  public static final String BUILD_TIME = "Build-Time";

  private static String get(Attributes attributes, String key) {
    return Objects.toString(attributes.get(new Attributes.Name(key)));
  }

  /**
   * @return server version information
   */
  @Override
  public String[] getVersion() throws IOException {
    return getClientVersion();
  }

  private String[] getClientVersion() throws IOException {
    Enumeration<URL> resources =
        CommandLine.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      try {
        Manifest manifest = new Manifest(url.openStream());
        if (isApplicableManifest(manifest)) {
          Attributes attr = manifest.getMainAttributes();
          return new String[] {
            IMPLEMENTATION_TITLE + SEPARATOR + get(attr, IMPLEMENTATION_TITLE),
            IMPLEMENTATION_VERSION + SEPARATOR + get(attr, IMPLEMENTATION_VERSION),
            LATEST_CLIENT_VERSION + SEPARATOR + get(attr, LATEST_CLIENT_VERSION), // Build-Time
            BUILD_TIME + SEPARATOR + get(attr, BUILD_TIME)
          };
        }
      } catch (IOException ex) {
        return new String[] {"Unable to fetch version information: " + ex};
      }
    }
    return new String[] {"Unable to fetch version information: "};
  }

  private boolean isApplicableManifest(Manifest manifest) {
    Attributes attributes = manifest.getMainAttributes();
    return "COBOL LSP :: Server".equals(get(attributes, IMPLEMENTATION_TITLE));
  }
}
