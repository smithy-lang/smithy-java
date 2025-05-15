/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import picocli.CommandLine.Option;
import software.amazon.smithy.java.mcp.cli.RegistryUtils;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;

@Command(name = "add-bundle", description = "Downloads and adds a bundle from the MCP registry.")
public class AddBundle extends SmithyMcpCommand {

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided will list tools across all registries.")
    String registry;

    @Option(names = {"-n", "--name"}, description = "Name of the MCP Bundle to install.")
    String name;

    @Override
    protected void execute(Config config) {
        if (registry != null && !config.getRegistries().containsKey(registry)) {
            throw new IllegalArgumentException("The registry '" + registry + "' does not exist.");
        }
        RegistryUtils.getRegistry(registry).getMcpBundle(name);
    }
}
