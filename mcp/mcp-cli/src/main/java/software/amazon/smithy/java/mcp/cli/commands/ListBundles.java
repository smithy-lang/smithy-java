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
        var installedBundles = context.config().getToolBundles();
        var commandLine = spec.commandLine();

        System.out.println(commandLine
                .getColorScheme()
                .string("@|bold,underline Registry MCP Servers:|@"));
        System.out.println();

        // Display registry bundles
        for (Registry.RegistryEntry entry : registry.listMcpBundles()) {
            var bundle = entry.getBundleMetadata();
            var installedBundle = installedBundles.get(bundle.getId());
            //If there is a local bundle with the same name, prefer that.
            boolean isInstalled = installedBundle != null;
            boolean hasLocalOverride = isInstalled && switch (installedBundle.getValue()) {
                case SmithyModeledBundleConfig config -> config.isLocal();
                case GenericToolBundleConfig config -> config.isLocal();
                default -> false;
            };
            String tag = null;
            if (hasLocalOverride) {
                tag = "locally-overriden";
            } else if (isInstalled) {
                tag = "installed";
            }
            printBundleInfo(commandLine, bundle.getId(), bundle.getName(), bundle.getDescription(), tag);
        }

        var localBundles = context.config()
                .getToolBundles()
                .entrySet()
                .stream()
                .filter(entry -> switch (entry.getValue().getValue()) {
                    case SmithyModeledBundleConfig config -> config.isLocal();
                    case GenericToolBundleConfig config -> config.isLocal();
                    default -> false;
                })
                .toList();

        if (!localBundles.isEmpty()) {
            System.out.println(commandLine
                    .getColorScheme()
                    .string("@|bold,underline Local MCP Servers:|@"));
            System.out.println();

            for (var localBundle : localBundles) {
                var bundleId = localBundle.getKey();
                var bundleMetadata = switch (localBundle.getValue().getValue()) {
                    case SmithyModeledBundleConfig config -> config.getMetadata();
                    case GenericToolBundleConfig config -> config.getMetadata();
                    default -> throw new IllegalStateException();
                };

                printBundleInfo(commandLine,
                        bundleId,
                        bundleMetadata.getName(),
                        bundleMetadata.getDescription(),
                        "local");
            }
        }
    }

    @Override
    protected String registryToUse(Config config) {
        return registryName;
    }

    private void printBundleInfo(
            CommandLine commandLine,
            String bundleId,
            String bundleName,
            String description,
            String tag
    ) {
        tag = tag == null ? "" : " [" + tag + "]";
        System.out.println(commandLine
                .getColorScheme()
                .string("@|bold " + bundleId + tag + "|@: " + bundleName));

        if (description == null) {
            description = "MCP server for " + bundleName;
        }
        System.out.print("\tDescription: ");
        System.out.println(description);
        System.out.println();
    }
}
