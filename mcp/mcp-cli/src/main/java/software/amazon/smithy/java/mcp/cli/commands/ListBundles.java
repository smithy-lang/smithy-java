/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import picocli.CommandLine.Option;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;

@Command(name = "list", description = "List all the MCP Bundles available in the Registry")
public class ListBundles extends SmithyMcpCommand {

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided will list tools across all registries.")
    String registryName;

    @Override
    protected void execute(ExecutionContext context) {
        var registry = context.registry();
        registry
                .listMcpBundles()
                .forEach(bundle -> {
                    System.out.println(commandSpec().commandLine()
                            .getColorScheme()
                            .string("@|bold " + bundle.getName() + "|@"));
                    var description = bundle.getDescription();
                    if (description == null) {
                        description = "MCP server for " + bundle.getName();
                    }
                    System.out.print("\tDescription: ");
                    System.out.println(description);
                });

    }

    @Override
    protected String registryToUse(Config config) {
        return registryName;
    }
}
