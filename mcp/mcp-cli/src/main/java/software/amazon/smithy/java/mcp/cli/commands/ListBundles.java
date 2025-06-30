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
import software.amazon.smithy.java.mcp.cli.model.GenericToolBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledBundleConfig;
import software.amazon.smithy.mcp.bundle.api.Registry;

@Command(name = "list", description = "List all the MCP servers present in the registry or installed locally.")
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
        var commandLine = spec.commandLine();

        // Display registry bundles
        for (Registry.RegistryEntry entry : registry.listMcpBundles()) {
            var bundle = entry.getBundleMetadata();
            boolean isInstalled = installedBundles.contains(bundle.getName());
            System.out.println(commandLine
                    .getColorScheme()
                    .string("@|bold " + bundle.getName() + (isInstalled ? " [installed]" : "") + "|@: "
                            + entry.getTitle()));
            var description = bundle.getDescription();
            if (description == null) {
                description = "MCP server for " + bundle.getName();
            }
            System.out.print("\tDescription: ");
            System.out.println(description);
            System.out.println();
        }

        // Display locally installed bundles that are not in the registry
        var registryBundleNames = registry.listMcpBundles()
                .stream()
                .map(entry -> entry.getBundleMetadata().getName())
                .collect(java.util.stream.Collectors.toSet());

        var localBundles = context.config()
                .getToolBundles()
                .entrySet()
                .stream()
                .filter(entry -> switch (entry.getValue().getValue()) {
                    case SmithyModeledBundleConfig config -> config.isLocal();
                    case GenericToolBundleConfig config -> config.isLocal();
                    default -> false;
                })
                .filter(entry -> !registryBundleNames.contains(entry.getKey()))
                .toList();

        if (!localBundles.isEmpty()) {
            System.out.println(commandLine
                    .getColorScheme()
                    .string("@|bold,underline Local Bundles:|@"));
            System.out.println();

            for (var localBundle : localBundles) {
                var bundleName = localBundle.getKey();
                var description = switch (localBundle.getValue().getValue()) {
                    case SmithyModeledBundleConfig config -> config.getDescription();
                    case GenericToolBundleConfig config -> config.getDescription();
                    default -> "";
                };

                System.out.println(commandLine
                        .getColorScheme()
                        .string("@|bold " + bundleName + " [local]|@"));
                System.out.print("\tDescription: ");
                System.out.println(description);
                System.out.println();
            }
        }
    }

    @Override
    protected String registryToUse(Config config) {
        return registryName;
    }
}
