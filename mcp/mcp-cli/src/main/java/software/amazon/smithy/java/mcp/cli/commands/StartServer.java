/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.McpBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledBundleConfig;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.server.FilteredService;
import software.amazon.smithy.java.server.OperationFilters;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.mcp.bundle.api.Bundles;

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
    public void execute(Config config) {
        if (!config.hasToolBundles()) {
            throw new IllegalArgumentException(
                    "No Tool Bundles have been configured. Configure one using the configure-tool-bundle command.");
        }
        List<McpBundleConfig> toolBundleConfigs = new ArrayList<>(toolBundles.size());
        for (var toolBundle : toolBundles) {
            var toolBundleConfig = config.getToolBundles().get(toolBundle);
            if (toolBundleConfig == null) {
                throw new IllegalArgumentException("Can't find a configured tool bundle for '" + toolBundle + "'.");
            }
            toolBundleConfigs.add(toolBundleConfig);
        }
        var services = new ArrayList<Service>();
        for (var toolBundleConfig : toolBundleConfigs) {
            switch (toolBundleConfig.type()) {
                case smithyModeled -> {
                    SmithyModeledBundleConfig bundleConfig = toolBundleConfig.getValue();
                    Service service =
                            Bundles.getService(ConfigUtils.getMcpBundle(bundleConfig.getName()));
                    if (bundleConfig.hasAllowListedTools() || bundleConfig.hasBlockListedTools()) {
                        var filter = OperationFilters.allowList(bundleConfig.getAllowListedTools())
                                .and(OperationFilters.blockList(bundleConfig.getBlockListedTools()));
                        service = new FilteredService(service, filter);
                    }
                    services.add(service);
                }
                default ->
                    throw new IllegalArgumentException("Unknown tool bundle type '" + toolBundleConfig.type() + "'.");
            }
        }
        var mcpServer = McpServer.builder().stdio().addServices(services).name("smithy-mcp-server").build();
        mcpServer.start();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            mcpServer.shutdown().join();
        }
    }
}
