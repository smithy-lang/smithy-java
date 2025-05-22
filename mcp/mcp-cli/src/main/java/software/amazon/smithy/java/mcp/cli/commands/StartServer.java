/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.ProcessStdIoProxy;
import software.amazon.smithy.java.mcp.cli.RegistryUtils;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.Location;
import software.amazon.smithy.java.mcp.cli.model.McpBundleConfig;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.server.FilteredService;
import software.amazon.smithy.java.server.OperationFilters;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.mcp.bundle.api.McpBundles;
import software.amazon.smithy.mcp.bundle.api.model.GenericBundle;

/**
 * Command to start a Smithy MCP server exposing specified tool bundles.
 * <p>
 * This command loads configured tool bundles and starts an MCP server that
 * exposes the operations provided by those bundles. The server runs until
 * interrupted or terminated.
 */
@Command(name = "start-server", description = "Starts an MCP server.")
public final class StartServer extends SmithyMcpCommand {

    @Parameters(paramLabel = "TOOL_BUNDLES", description = "Name(s) of the Tool Bundles to expose in this MCP Server.")
    List<String> toolBundles;

    /**
     * Executes the start-server command.
     * <p>
     * Loads the requested tool bundles from configuration, creates appropriate services,
     * and starts the MCP server.
     *
     * @param config The MCP configuration
     * @throws IllegalArgumentException If no tool bundles are configured or requested bundles not found
     */
    @Override
    public void execute(Config config) throws IOException {
        // By default, load all available tools
        if (toolBundles == null || toolBundles.isEmpty()) {
            try {
                toolBundles = new ArrayList<>(ConfigUtils.loadOrCreateConfig().getToolBundles().keySet());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (toolBundles.isEmpty()) {
            throw new IllegalArgumentException("No bundles installed");
        }

        List<McpBundleConfig> toolBundleConfigs = new ArrayList<>(toolBundles.size());

        for (var toolBundle : toolBundles) {
            var toolBundleConfig = config.getToolBundles().get(toolBundle);
            if (toolBundleConfig == null) {
                var bundle = RegistryUtils.getRegistry(config.getDefaultRegistry()).getMcpBundle(toolBundle);
                if (bundle == null) {
                    throw new IllegalArgumentException("Can't find a configured tool bundle for '" + toolBundle + "'.");
                } else {
                    toolBundleConfig = McpBundleConfig.builder()
                            .name(toolBundle)
                            .bundleLocation(Location.builder()
                                    .fileLocation(ConfigUtils.getBundleFileLocation(toolBundle).toString())
                                    .build())
                            .build();
                    ConfigUtils.addMcpBundle(config, toolBundle, bundle);
                }
            }
            toolBundleConfigs.add(toolBundleConfig);
        }
        var services = new ArrayList<Service>();
        GenericBundle genericBundle = null;
        for (var toolBundleConfig : toolBundleConfigs) {
            if (genericBundle != null) {
                throw new IllegalArgumentException("Multiple generic tool bundles are not supported right now.");
            }
            var bundle = ConfigUtils.getMcpBundle(toolBundleConfig.getName());
            switch (bundle.type()) {
                case smithyBundle -> {
                    Service service =
                            McpBundles.getService(bundle);
                    if (toolBundleConfig.hasAllowListedTools() || toolBundleConfig.hasBlockListedTools()) {
                        var filter = OperationFilters.allowList(toolBundleConfig.getAllowListedTools())
                                .and(OperationFilters.blockList(toolBundleConfig.getBlockListedTools()));
                        service = new FilteredService(service, filter);
                    }
                    services.add(service);
                }
                case genericBundle -> {
                    if (!services.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Multiple tool bundles with a generic bundle are not supported right now.");
                    }
                    genericBundle = bundle.getValue();
                }
                default ->
                    throw new IllegalArgumentException("Unknown tool bundle type '" + bundle.type() + "'.");
            }
        }
        if (genericBundle != null) {
            var proxy = ProcessStdIoProxy.builder()
                    .arguments(genericBundle.getArgs())
                    .command(genericBundle.getCommand())
                    .build();
            proxy.start();
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                proxy.shutdown().join();
            }
            return;
        }
        var mcpServer = McpServer.builder()
                .stdio()
                .addServices(services)
                .name("smithy-mcp-server")
                .build();
        mcpServer.start();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            mcpServer.shutdown().join();
        }
    }
}
