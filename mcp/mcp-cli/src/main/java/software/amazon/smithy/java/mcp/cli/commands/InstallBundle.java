/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.McpServerConfig;

@Command(name = "install", description = "Downloads and adds a bundle from the MCP registry.")
public class InstallBundle extends SmithyMcpCommand {

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided it will use the default registry.")
    String registryName;

    @Parameters(description = "Name of the MCP bundle to install.")
    String name;

    @Option(names = {"--clients"},
            description = "Names of client configs to update. If not specified all client configs registered would be updated")
    Set<String> clients = Set.of();

    @Option(names = "--print-only",
            description = "If specified will not edit the client configs and only print to console.")
    Boolean print;

    @Override
    protected void execute(ExecutionContext context) throws IOException {
        var registry = context.registry();
        var config = context.config();
        var bundle = registry.getMcpBundle(name);
        ConfigUtils.addMcpBundle(config, name, bundle);
        var newConfig = McpServerConfig.builder().command("mcp-registry").args(List.of("start-server", name)).build();
        if (print == null) {
            //By default, print the output if there are no configured client configs.
            print = !config.hasClientConfigs();
        }
        ConfigUtils.addToClientConfigs(config, name, clients, newConfig);

        System.out.println("Successfully installed " + name);
        if (print) {
            System.out.println("You can add the following to your MCP Servers config to use " + name);
            System.out.println(newConfig);
        }
    }

    @Override
    protected String registryToUse(Config config) {
        return registryName;
    }
}
