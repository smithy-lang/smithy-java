/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;

@Command(name = "list", description = "List all the MCP Bundles available in the Registry")
public class ListBundles extends SmithyMcpCommand {

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided will list tools across all registries.")
    String registryName;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    protected void execute(ExecutionContext context) {
        var registry = context.registry();
        var installedBundles = context.config().getToolBundles().keySet();
        for (BundleMetadata bundle : registry
                .listMcpBundles()) {
            boolean isInstalled = installedBundles.contains(bundle.getName());
            var commandLine = spec.commandLine();
            System.out.println(commandLine
                    .getColorScheme()
                    .string("@|bold " + bundle.getName() + (isInstalled ? " [installed]" : "") + "|@"));
            var description = bundle.getDescription();
            if (description == null) {
                description = "MCP server for " + bundle.getName();
            }
            System.out.print("\tDescription: ");
            System.out.println(description);
        }

    }

    @Override
    protected String registryToUse(Config config) {
        return registryName;
    }
}
