/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.ProcessIoProxy;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.GenericToolBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.McpBundleConfig;
import software.amazon.smithy.java.mcp.cli.model.SmithyModeledBundleConfig;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.mcp.server.StdioProxy;
import software.amazon.smithy.java.mcp.server.ToolFilter;
import software.amazon.smithy.java.mcp.toolassistant.model.InstallToolInput;
import software.amazon.smithy.java.mcp.toolassistant.model.InstallToolOutput;
import software.amazon.smithy.java.mcp.toolassistant.model.SearchToolsInput;
import software.amazon.smithy.java.mcp.toolassistant.model.SearchToolsOutput;
import software.amazon.smithy.java.mcp.toolassistant.model.Tool;
import software.amazon.smithy.java.mcp.toolassistant.service.InstallToolOperation;
import software.amazon.smithy.java.mcp.toolassistant.service.SearchToolsOperation;
import software.amazon.smithy.java.mcp.toolassistant.service.ToolAssistant;
import software.amazon.smithy.java.server.FilteredService;
import software.amazon.smithy.java.server.OperationFilters;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.mcp.bundle.api.McpBundles;
import software.amazon.smithy.mcp.bundle.api.Registry;
import software.amazon.smithy.mcp.bundle.api.SearchableRegistry;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
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

    @Parameters(paramLabel = "MCP_SERVER_IDS",
            description = "Id(s) of MCP Servers to start and expose as a single MCP server.")
    List<String> mcpServerIds = List.of();

    @Option(names = {"--tool-assistant", "-ts"}, description = "Exposes a Tool Assistant to Search and Install tools")
    boolean toolAssistant;

    @Unmatched
    List<String> additionalArgs;

    private volatile McpServer mcpServer;

    /**
     * Executes the start-server command.
     * <p>
     * Loads the requested tool bundles from configuration, creates appropriate services,
     * and starts the MCP server.
     *
     * @param context {@link ExecutionContext}
     * @throws IllegalArgumentException If no tool bundles are configured or requested bundles not found
     */
    @Override
    public void execute(ExecutionContext context) throws IOException {

        var config = context.config();
        // By default, load all available tools only if not in tool-assistant mode.
        if (!toolAssistant && mcpServerIds.isEmpty()) {
            mcpServerIds = config.getToolBundles()
                    .entrySet()
                    .stream()
                    .filter(entry -> {
                        // By default, only include smithy bundles if no bundles are specified.
                        // We can undo this once we have fanout support for generic bundles.
                        return entry.getValue().type() == McpBundleConfig.Type.smithyModeled;
                    })
                    .map(Map.Entry::getKey)
                    .toList();
        }

        if (!toolAssistant && mcpServerIds.isEmpty()) {
            throw new IllegalArgumentException("No MCP servers installed");
        }

        var registry = context.registry();

        List<McpBundleConfig> toolBundleConfigs = new ArrayList<>(mcpServerIds.size());

        for (var id : mcpServerIds) {
            var toolBundleConfig = config.getToolBundles().get(id);
            if (toolBundleConfig == null) {
                var bundle = registry.getMcpBundle(id);
                if (bundle == null) {
                    throw new IllegalArgumentException("Can't find a configured MCP server for '" + id + "'.");
                } else {
                    toolBundleConfig = ConfigUtils.addMcpBundle(config, id, bundle);
                }
            }
            toolBundleConfigs.add(toolBundleConfig);
        }

        var services = new HashMap<String, Service>();
        var allowerServers = new HashSet<String>();
        //TODO Till we implement the full MCP spec in MCPServerProxy we can only start a single proxy server.
        ProcessIoProxy proxyServer = null;
        for (var toolBundleConfig : toolBundleConfigs) {
            switch (toolBundleConfig.type()) {
                case smithyModeled -> {
                    if (proxyServer != null) {
                        throw new IllegalArgumentException("Generic MCP servers cannot be run with other MCP servers");
                    }
                    SmithyModeledBundleConfig smithyConfig = toolBundleConfig.getValue();
                    services.put(smithyConfig.getName(), bundleToService(smithyConfig));
                    allowerServers.add(smithyConfig.getName());
                }
                case genericConfig -> {
                    if (!services.isEmpty() || proxyServer != null) {
                        throw new IllegalArgumentException("Generic MCP servers cannot be run with other MCP servers");
                    }
                    GenericToolBundleConfig genericToolBundleConfig = toolBundleConfig.getValue();
                    allowerServers.add(genericToolBundleConfig.getName());

                    GenericBundle genericBundle =
                            ConfigUtils.getMcpBundle(genericToolBundleConfig.getName()).getValue();
                    List<String> combinedArgs = new ArrayList<>();
                    var execSpec = genericBundle.getRun();
                    if (execSpec.getArgs() != null) {
                        combinedArgs.addAll(execSpec.getArgs());
                    }
                    if (additionalArgs != null) {
                        combinedArgs.addAll(additionalArgs);
                    }

                    proxyServer = ProcessIoProxy.builder()
                            .command(execSpec.getExecutable())
                            .arguments(combinedArgs)
                            .environmentVariables(System.getenv())
                            .build();
                }
                default -> throw new IllegalArgumentException("Unknown tool bundle type: " + toolBundleConfig.type());
            }

        }

        ThrowingRunnable awaitCompletion;
        Supplier<CompletableFuture<Void>> shutdownMethod;
        Set<String> allowedTools = null;
        if (proxyServer != null) {
            proxyServer.start();
            awaitCompletion = proxyServer::awaitCompletion;
            shutdownMethod = proxyServer::shutdown;
        } else {
            if (toolAssistant) {
                allowedTools = new CopyOnWriteArraySet<>();
                final SearchToolsOperation searchToolsOperation;
                if (registry instanceof SearchableRegistry searchableRegistry) {
                    searchToolsOperation = new SearchOp(searchableRegistry);
                    allowedTools.add("SearchTools");
                } else {
                    searchToolsOperation = (ignored, ignored1) -> {
                        throw new UnsupportedOperationException("This registry isn't searchable");
                    };
                }
                allowedTools.add("InstallTool");
                services.put("tool-assistant",
                        ToolAssistant.builder()
                                .addInstallToolOperation(new InstallTool(registry, config, allowedTools))
                                .addSearchToolsOperation(searchToolsOperation)
                                .build());
            }

            var metrics = context.metrics();
            var mcpsStarted = String.join(":", services.keySet());
            metrics.addProperty("McpStarted", mcpsStarted);

            if (toolAssistant) {
                metrics.addCount("ToolAssistant", 1);
            }

            this.mcpServer =
                    (McpServer) McpServer.builder()
                            .stdio()
                            .addService(services)
                            .toolFilter(getToolFilter(allowerServers, allowedTools))
                            .name("smithy-mcp-server")
                            .build();
            mcpServer.start();
            awaitCompletion = mcpServer::awaitCompletion;
            shutdownMethod = mcpServer::shutdown;
        }

        boolean shutdown = false;
        try {
            awaitCompletion.run();
            shutdown = true;
            shutdownMethod.get().join();
        } catch (Exception e) {
            if (!shutdown) {
                shutdownMethod.get().join();
            }
        }
    }

    private ToolFilter getToolFilter(Set<String> allowedServers, Set<String> tools) {
        return (mcpServerName, toolName) -> {
            if (allowedServers.contains(mcpServerName)) {
                return true;
            }
            return tools == null || tools.contains(toolName);
        };
    }

    private static Service bundleToService(SmithyModeledBundleConfig bundleConfig) {
        var service = bundleToService(ConfigUtils.getMcpBundle(bundleConfig.getName()));
        if (bundleConfig.hasAllowListedTools() || bundleConfig.hasBlockListedTools()) {
            var filter = OperationFilters.allowList(bundleConfig.getAllowListedTools())
                    .and(OperationFilters.blockList(bundleConfig.getBlockListedTools()));
            service = new FilteredService(service, filter);
        }
        return service;
    }

    private static Service bundleToService(Bundle bundle) {
        return McpBundles.getService(bundle);
    }

    private static final class SearchOp implements SearchToolsOperation {

        private final SearchableRegistry registry;

        private SearchOp(SearchableRegistry registry) {
            this.registry = registry;
        }

        @Override
        public SearchToolsOutput searchTools(SearchToolsInput input, RequestContext context) {
            var tools = registry.searchTools(input.getToolDescription(), input.getNumberOfTools());
            return SearchToolsOutput.builder()
                    .tools(tools.stream()
                            .map(t -> Tool.builder()
                                    .toolName(t.serverId() + "__" + t.toolName())
                                    .build())
                            .toList())
                    .build();
        }
    }

    private final class InstallTool implements InstallToolOperation {

        private final Registry registry;
        private final Config config;
        private final Set<String> installedTools;

        private InstallTool(Registry registry, Config config, Set<String> installedTools) {
            this.registry = registry;
            this.config = config;
            this.installedTools = installedTools;
        }

        @Override
        public InstallToolOutput installTool(InstallToolInput input, RequestContext context) {
            var tool = input.getToolName();
            var toolParts = tool.split("__");
            if (toolParts.length < 2) {
                throw new IllegalArgumentException("Invalid tool name");
            }
            var serverId = toolParts[0];
            var toolName = Arrays.stream(toolParts).skip(1).collect(Collectors.joining("__"));
            Bundle bundle;
            if (!config.getToolBundles().containsKey(serverId)) {
                bundle = registry.getMcpBundle(serverId);
                if (bundle == null) {
                    throw new IllegalArgumentException("Can't find a configured tool bundle for '" + serverId + "'.");
                } else {
                    try {
                        ConfigUtils.addMcpBundle(config, serverId, bundle);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                bundle = ConfigUtils.getMcpBundle(serverId);
            }
            switch (bundle.type()) {
                case genericBundle -> {
                    GenericBundle genericBundle = bundle.getValue();
                    if (!mcpServer.containsMcpServer(serverId)) {
                        mcpServer.addNewProxy(StdioProxy.builder()
                                .name(serverId)
                                .command(genericBundle.getRun().getExecutable())
                                .build());
                    }
                }
                case smithyBundle -> {
                    if (!mcpServer.containsMcpServer(serverId)) {
                        mcpServer.addNewService(serverId, bundleToService(bundle));
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported bundle type: " + bundle.type());
            }
            mcpServer.refreshTools();
            installedTools.add(toolName);
            return InstallToolOutput.builder()
                    .message("Tool " + toolName + " installed. Check your list of tools.")
                    .build();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
