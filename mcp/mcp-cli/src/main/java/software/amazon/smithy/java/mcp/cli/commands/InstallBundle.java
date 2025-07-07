/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ClientsInput;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;

@Command(name = "install", description = "Downloads and installs a MCP server from the MCP registry.")
public class InstallBundle extends SmithyMcpCommand {

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the mcp server from. If not provided it will use the default registry.")
    String registryName;

    @Parameters(description = "Id(s) of the MCP server(s) to install.")
    Set<String> ids;

    @ArgGroup
    ClientsInput clientsInput;

    @Override
    protected void execute(ExecutionContext context) throws IOException {
        var registry = context.registry();
        var config = context.config();

        // First, collect all server and validate they exist
        Map<String, Bundle> bundles = new HashMap<>();
        for (var id : ids) {
            var bundle = registry.getMcpBundle(id);
            if (bundle == null) {
                throw new IllegalArgumentException("MCP with id '" + id + "' not found.");
            }
            bundles.put(id, bundle);
        }

        // Now install all server
        for (var entry : bundles.entrySet()) {
            var id = entry.getKey();
            var bundle = entry.getValue();
            ConfigUtils.addMcpBundle(config, id, bundle);
            ConfigUtils.createWrapperAndUpdateClientConfigs(id, bundle, config, clientsInput);
            System.out.println("Successfully installed " + id);
        }
    }

    @Override
    protected String registryToUse(Config config) {
        return registryName;
    }
}
