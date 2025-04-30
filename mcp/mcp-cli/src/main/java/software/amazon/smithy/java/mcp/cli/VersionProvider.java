/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import picocli.CommandLine;
import software.amazon.smithy.utils.IoUtils;

/**
 * Provides version information for the Smithy MCP CLI.
 * <p>
 * This class implements Picocli's IVersionProvider interface to provide
 * version information from a resource file.
 */
public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        return new String[] {IoUtils.readUtf8Resource(VersionProvider.class, "VERSION").trim()};
    }
}
