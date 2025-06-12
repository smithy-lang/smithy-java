/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.ConfigurationCommand;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.ClientConfig;

@Command(
        name = "client-config",
        description = "Configure MCP client settings",
        subcommands = {
                ClientConfigCommand.ListCommand.class,
                ClientConfigCommand.AddCommand.class,
                ClientConfigCommand.RemoveCommand.class
        })
final class ClientConfigCommand {

    @Command(name = "list", description = "List all configured MCP clients")
    public static final class ListCommand extends SmithyMcpCommand implements ConfigurationCommand {

        @Override
        protected void execute(ExecutionContext context) throws Exception {
            var clientConfigs = context.config().getClientConfigs();

            if (clientConfigs == null || clientConfigs.isEmpty()) {
                System.out.println("No client configurations found.");
                return;
            }

            System.out.println("Configured MCP clients:");
            var userHome = System.getProperty("user.home");
            for (var clientConfig : clientConfigs) {
                if (Boolean.TRUE.equals(clientConfig.isDisabled())) {
                    continue;
                }
                var defaultMarker = clientConfig.isIsDefault() ? " (default)" : "";
                var displayPath = clientConfig.getFilePath();
                if (displayPath.startsWith(userHome)) {
                    displayPath = "~" + displayPath.substring(userHome.length());
                }
                System.out.printf("  - %s: %s%s%n",
                        clientConfig.getName(),
                        displayPath,
                        defaultMarker);
            }
        }
    }

    @Command(name = "add", description = "Add a new MCP client configuration")
    public static final class AddCommand extends SmithyMcpCommand implements ConfigurationCommand {

        @Parameters(index = "0", description = "Client name (e.g., 'q-cli')")
        String clientName;

        @Parameters(index = "1", description = "Configuration file path", arity = "0..1")
        String filePath;

        @Option(names = {"--overwrite"}, description = "Overwrite existing client configuration if it exists",
                defaultValue = "false")
        boolean overwrite;

        @Override
        protected void execute(ExecutionContext context) throws Exception {
            var currentConfig = context.config();
            var existingConfigs = new ArrayList<>(
                    currentConfig.getClientConfigs() != null ? currentConfig.getClientConfigs() : List.of());

            var existing = existingConfigs.stream()
                    .filter(c -> c.getName().equals(clientName))
                    .findFirst();

            if (filePath == null || filePath.isEmpty()) {
                if (existing.isEmpty()) {
                    throw new IllegalArgumentException("Client configuration '" + clientName
                            + "' not found. Please provide a file path to create a new configuration.");
                }

                var existingConfig = existing.get();
                if (!Boolean.TRUE.equals(existingConfig.isDisabled())) {
                    System.out.printf("Client configuration '%s' is already enabled.%n", clientName);
                    return;
                }

                var enabledConfig = existingConfig.toBuilder()
                        .disabled(false)
                        .build();

                existingConfigs.removeIf(c -> c.getName().equals(clientName));
                existingConfigs.add(enabledConfig);

                var updatedConfig = currentConfig.toBuilder()
                        .clientConfigs(existingConfigs)
                        .build();

                ConfigUtils.updateConfig(updatedConfig);

                System.out.printf("Enabled client configuration '%s'.%n", clientName);
                return;
            }

            if (existing.isPresent() && !overwrite) {
                System.out.printf("Warning: Client configuration '%s' already exists. Use --overwrite to replace it.%n",
                        clientName);
                return;
            }

            var resolvedPath = Paths.get(filePath);
            if (!resolvedPath.isAbsolute()) {
                resolvedPath = Paths.get(System.getProperty("user.home")).resolve(filePath);
            }

            ClientConfig pathClash = null;
            for (var config : existingConfigs) {
                if (!config.getName().equals(clientName) && config.getFilePath().equals(resolvedPath.toString())) {
                    pathClash = config;
                    break;
                }
            }

            if (pathClash != null) {
                System.out.printf("Warning: The file path '%s' is already used by client configuration '%s'. " +
                        "Please remove the other configuration first or use a different path.%n",
                        resolvedPath,
                        pathClash.getName());
                return;
            }

            if (existing.isPresent()) {
                existingConfigs.removeIf(c -> c.getName().equals(clientName));
            }

            var newClientConfig = ClientConfig.builder()
                    .name(clientName)
                    .filePath(resolvedPath.toString())
                    .build();

            existingConfigs.add(newClientConfig);

            var updatedConfig = currentConfig.toBuilder()
                    .clientConfigs(existingConfigs)
                    .build();

            ConfigUtils.updateConfig(updatedConfig);

            var action = existing.isPresent() ? "Updated" : "Added";
            System.out.printf("%s client configuration '%s': %s%n",
                    action,
                    clientName,
                    resolvedPath);
        }
    }

    @Command(name = "remove", description = "Remove an MCP client configuration")
    public static final class RemoveCommand extends SmithyMcpCommand implements ConfigurationCommand {

        @Parameters(index = "0", description = "Client name to remove")
        String clientName;

        @Override
        protected void execute(ExecutionContext context) throws Exception {
            var currentConfig = context.config();
            var existingConfigs = new ArrayList<>(
                    currentConfig.getClientConfigs() != null ? currentConfig.getClientConfigs() : List.of());

            var toRemove = existingConfigs.stream()
                    .filter(c -> c.getName().equals(clientName))
                    .findFirst();

            if (toRemove.isEmpty()) {
                throw new IllegalArgumentException("Client configuration for '" + clientName + "' not found.");
            }

            var clientToRemove = toRemove.get();

            if (clientToRemove.isIsDefault()) {
                var disabledConfig = clientToRemove.toBuilder()
                        .disabled(true)
                        .build();

                existingConfigs.removeIf(c -> c.getName().equals(clientName));
                existingConfigs.add(disabledConfig);

                var updatedConfig = currentConfig.toBuilder()
                        .clientConfigs(existingConfigs)
                        .build();

                ConfigUtils.updateConfig(updatedConfig);

                System.out.printf("Disabled default client configuration '%s'.%n", clientName);
            } else {
                existingConfigs.removeIf(c -> c.getName().equals(clientName));

                var updatedConfig = currentConfig.toBuilder()
                        .clientConfigs(existingConfigs)
                        .build();

                ConfigUtils.updateConfig(updatedConfig);

                System.out.printf("Removed client configuration '%s'.%n", clientName);
            }
        }
    }
}
