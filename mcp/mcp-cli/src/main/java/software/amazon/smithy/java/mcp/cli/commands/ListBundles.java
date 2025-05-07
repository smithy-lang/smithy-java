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

@Command(name = "list", description = "List all the MCP Bundles available in the Registry")
public class ListBundles extends SmithyMcpCommand {

    //TODO make it non-required later and search across all registries.
    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided will list tools across all registries.",
            required = true)
    String registry;

    @Override
    protected void execute(Config config) {
        if (registry != null && !config.getRegistries().containsKey(registry)) {
            throw new IllegalArgumentException("The registry '" + registry + "' does not exist.");
        }
        RegistryUtils.getRegistry(registry)
                .listMcpBundles()
                .forEach(bundle -> System.out.println(bundle.getName() + " : " + bundle.getDescription()));

    }
}
