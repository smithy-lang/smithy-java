/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import java.io.IOException;
import java.util.List;
import picocli.CommandLine.Option;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.RegistryUtils;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;

@Command(name = "install", description = "Downloads and adds a bundle from the MCP registry.")
public class InstallBundle extends SmithyMcpCommand {

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided it will use the default registry.")
    String registry;

    @Option(names = {"-n", "--name"}, description = "Name of the MCP Bundle to install.")
    String name;

    @Option(names = {"--clients"},
            description = "Names of client configs to update. If not specified all client configs registered would be updated")
    List<String> clients;

    @Option(names = "--print-only",
            description = "If specified will not edit the client configs and only print to console.")
    boolean print;

    @Override
    protected void execute(Config config) throws IOException {
        if (registry != null && !config.getRegistries().containsKey(registry)) {
            throw new IllegalArgumentException("The registry '" + registry + "' does not exist.");
        }
        var bundle = RegistryUtils.getRegistry(registry).getMcpBundle(name);
        ConfigUtils.addMcpBundle(config, name, bundle);
    }
}
