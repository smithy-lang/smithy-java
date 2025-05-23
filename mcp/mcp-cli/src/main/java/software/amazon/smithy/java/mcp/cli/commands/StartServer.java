/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.RegistryUtils;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.Location;
import software.amazon.smithy.java.mcp.cli.model.McpBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledBundleConfig;
import software.amazon.smithy.java.mcp.registry.model.InstallServerInput;
import software.amazon.smithy.java.mcp.registry.model.InstallServerOutput;
import software.amazon.smithy.java.mcp.registry.model.ListServersInput;
import software.amazon.smithy.java.mcp.registry.model.ListServersOutput;
import software.amazon.smithy.java.mcp.registry.model.ServerEntry;
import software.amazon.smithy.java.mcp.registry.service.InstallServerOperation;
import software.amazon.smithy.java.mcp.registry.service.ListServersOperation;
import software.amazon.smithy.java.mcp.registry.service.McpRegistry;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.server.FilteredService;
import software.amazon.smithy.java.server.OperationFilters;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.mcp.bundle.api.McpBundles;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;

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

    @Option(names = "--registry-server", description = "Serve the registry as an MCP server")
    boolean registryServer;

    private volatile McpServer mcpServer;

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

        if (toolBundles.isEmpty() && !registryServer) {
            throw new IllegalStateException("No bundles installed");
        }

        List<McpBundleConfig> toolBundleConfigs = new ArrayList<>(toolBundles.size());

        for (var toolBundle : toolBundles) {
            var toolBundleConfig = config.getToolBundles().get(toolBundle);
            if (toolBundleConfig == null) {
                var bundle = RegistryUtils.getRegistry().getMcpBundle(toolBundle);
                if (bundle == null) {
                    throw new IllegalArgumentException("Can't find a configured tool bundle for '" + toolBundle + "'.");
                } else {
                    toolBundleConfig = McpBundleConfig.builder()
                            .smithyModeled(SmithyModeledBundleConfig.builder()
                                    .name(toolBundle)
                                    .bundleLocation(Location.builder()
                                            .fileLocation(ConfigUtils.getBundleFileLocation(toolBundle).toString())
                                            .build())
                                    .build())
                            .build();
                    ConfigUtils.addMcpBundle(config, toolBundle, bundle);
                }
            }
            toolBundleConfigs.add(toolBundleConfig);
        }

        var services = new ArrayList<Service>();
        for (var toolBundleConfig : toolBundleConfigs) {
            services.add(bundleToService(toolBundleConfig));
        }

        if (registryServer) {
            services.add(McpRegistry.builder()
                    .addInstallServerOperation(new InstallOp())
                    .addListServersOperation(new ListOp())
                    .build());
        }

        this.mcpServer =
                (McpServer) McpServer.builder().stdio().addServices(services).name("smithy-mcp-server").build();
        mcpServer.start();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            mcpServer.shutdown().join();
        }
    }

    private static Service bundleToService(McpBundleConfig toolBundleConfig) {
        switch (toolBundleConfig.type()) {
            case smithyModeled -> {
                SmithyModeledBundleConfig bundleConfig = toolBundleConfig.getValue();
                Service service =
                        McpBundles.getService(ConfigUtils.getMcpBundle(bundleConfig.getName()));
                if (bundleConfig.hasAllowListedTools() || bundleConfig.hasBlockListedTools()) {
                    var filter = OperationFilters.allowList(bundleConfig.getAllowListedTools())
                            .and(OperationFilters.blockList(bundleConfig.getBlockListedTools()));
                    service = new FilteredService(service, filter);
                }
                return service;
            }
            default ->
                throw new IllegalArgumentException("Unknown tool bundle type '" + toolBundleConfig.type() + "'.");
        }
    }

    private static final class ListOp implements ListServersOperation {
        @Override
        public ListServersOutput listServers(ListServersInput input, RequestContext context) {
            var servers = RegistryUtils.getRegistry()
                    .listMcpBundles()
                    .stream()
                    .unordered()
                    .collect(Collectors.toMap(
                            BundleMetadata::getName,
                            bundle -> ServerEntry.builder()
                                    .description(bundle.getDescription())
                                    .build()));
            return ListServersOutput.builder()
                    .servers(servers)
                    .build();
        }
    }

    private final class InstallOp implements InstallServerOperation {
        @Override
        public InstallServerOutput installServer(InstallServerInput input, RequestContext context) {
            try {
                var config = ConfigUtils.loadOrCreateConfig();
                if (!config.getToolBundles().containsKey(input.getServerName())) {
                    var bundle = RegistryUtils.getRegistry().getMcpBundle(input.getServerName());
                    if (bundle == null) {
                        throw new IllegalArgumentException(
                                "Can't find a configured tool bundle for '" + input.getServerName() + "'.");
                    } else {
                        var mcpBundleConfig = ConfigUtils.addMcpBundle(config, input.getServerName(), bundle);
                        mcpServer.addNewService(bundleToService(mcpBundleConfig));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return InstallServerOutput.builder().build();
        }
    }
}
