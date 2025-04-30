/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Bundle;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.GenericArguments;
import software.amazon.smithy.java.mcp.cli.model.Model;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledToolBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.ToolBundleConfig;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.server.FilteredService;
import software.amazon.smithy.java.server.OperationFilters;
import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.Service;

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
        List<ToolBundleConfig> toolBundleConfigs = new ArrayList<>(toolBundles.size());
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
                    SmithyModeledToolBundleConfig bundleConfig = toolBundleConfig.getValue();
                    Service service =
                            ProxyService.builder().bundle(convert(bundleConfig.getServiceDescriptor())).build();
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

    //TODO remove these after external types Schema if fixed.
    private static software.amazon.smithy.modelbundle.api.model.Bundle convert(Bundle bundle) {
        return software.amazon.smithy.modelbundle.api.model.Bundle.builder()
                .config(bundle.getConfig())
                .configType(bundle.getConfigType())
                .serviceName(bundle.getServiceName())
                .model(convert(bundle.getModel()))
                .requestArguments(convert(bundle.getRequestArguments()))
                .build();
    }

    private static software.amazon.smithy.modelbundle.api.model.GenericArguments convert(
            GenericArguments genericArguments
    ) {
        return software.amazon.smithy.modelbundle.api.model.GenericArguments.builder()
                .model(convert(genericArguments.getModel()))
                .identifier(genericArguments.getIdentifier())
                .build();
    }

    private static software.amazon.smithy.modelbundle.api.model.Model convert(Model model) {
        return software.amazon.smithy.modelbundle.api.model.Model.builder().smithyModel(model.getValue()).build();
    }
}
