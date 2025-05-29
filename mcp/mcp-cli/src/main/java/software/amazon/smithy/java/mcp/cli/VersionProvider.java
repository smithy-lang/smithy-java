/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.Comparator;
import java.util.ServiceLoader;
import picocli.CommandLine;

/**
 * Provides version information for the Smithy MCP CLI.
 * <p>
 * This class implements Picocli's IVersionProvider interface to provide
 * version information from a resource file.
 */
public final class VersionProvider implements CommandLine.IVersionProvider {

    private static final McpCliVersionProvider VERSION_PROVIDER = ServiceLoader.load(McpCliVersionProvider.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .min(Comparator.comparing(McpCliVersionProvider::priority))
            .orElse(McpCliVersionProvider.getDefault());

    @Override
    public String[] getVersion() {
        return new String[] {VERSION_PROVIDER.getVersion()};
    }
}
