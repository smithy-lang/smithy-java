/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
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
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.mcp.registry.model.InstallToolInput;
import software.amazon.smithy.java.mcp.registry.model.InstallToolOutput;
import software.amazon.smithy.java.mcp.registry.model.SearchToolsInput;
import software.amazon.smithy.java.mcp.registry.model.SearchToolsOutput;
import software.amazon.smithy.java.mcp.registry.model.Tool;
import software.amazon.smithy.java.mcp.registry.service.InstallToolOperation;
import software.amazon.smithy.java.mcp.registry.service.McpRegistry;
import software.amazon.smithy.java.mcp.registry.service.SearchToolsOperation;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.mcp.server.StdioProxy;
import software.amazon.smithy.java.server.FilteredService;
import software.amazon.smithy.java.server.OperationFilters;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.mcp.bundle.api.McpBundles;
import software.amazon.smithy.mcp.bundle.api.Registry;
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

    @Option(names = "--registry-server", description = "Serve the registry as an MCP server")
    boolean registryServer;

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
        // By default, load all available tools
        if (toolBundles == null || toolBundles.isEmpty()) {
            toolBundles = config.getToolBundles()
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

        if (toolBundles.isEmpty() && !registryServer) {
            throw new IllegalArgumentException("No bundles installed");
        }

        var registry = context.registry();

        List<McpBundleConfig> toolBundleConfigs = new ArrayList<>(toolBundles.size());

        for (var toolBundle : toolBundles) {
            var toolBundleConfig = config.getToolBundles().get(toolBundle);
            if (toolBundleConfig == null) {
                var bundle = registry.getMcpBundle(toolBundle);
                if (bundle == null) {
                    throw new IllegalArgumentException("Can't find a configured tool bundle for '" + toolBundle + "'.");
                } else {
                    toolBundleConfig = ConfigUtils.addMcpBundle(config, toolBundle, bundle);
                }
            }
            toolBundleConfigs.add(toolBundleConfig);
        }

        var services = new ArrayList<Service>();
        //TODO Till we implement the full MCP spec in MCPServerProxy we can only start a single proxy server.
        ProcessIoProxy proxyServer = null;
        for (var toolBundleConfig : toolBundleConfigs) {
            switch (toolBundleConfig.type()) {
                case smithyModeled -> {
                    if (proxyServer != null) {
                        throw new IllegalArgumentException("Generic MCP servers cannot be run with other MCP servers");
                    }
                    services.add(bundleToService(toolBundleConfig.getValue()));
                }
                case genericConfig -> {
                    if (!services.isEmpty() || proxyServer != null) {
                        throw new IllegalArgumentException("Generic MCP servers cannot be run with other MCP servers");
                    }
                    GenericToolBundleConfig genericToolBundleConfig = toolBundleConfig.getValue();
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
        if (proxyServer != null) {
            proxyServer.start();
            awaitCompletion = proxyServer::awaitCompletion;
            shutdownMethod = proxyServer::shutdown;
        } else {
            if (registryServer) {
                services.add(McpRegistry.builder()
                        .addInstallToolOperation(new InstallTool(registry, config))
                        .addSearchToolsOperation(new SearchOp(registry))
                        .build());
            }

            this.mcpServer =
                    (McpServer) McpServer.builder().stdio().addServices(services).name("smithy-mcp-server").build();
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

    private static Service bundleToService(SmithyModeledBundleConfig bundleConfig) {
        Service service =
                McpBundles.getService(ConfigUtils.getMcpBundle(bundleConfig.getName()));
        if (bundleConfig.hasAllowListedTools() || bundleConfig.hasBlockListedTools()) {
            var filter = OperationFilters.allowList(bundleConfig.getAllowListedTools())
                    .and(OperationFilters.blockList(bundleConfig.getBlockListedTools()));
            service = new FilteredService(service, filter);
        }
        return service;
    }

    private static final class SearchOp implements SearchToolsOperation {

        private final Registry registry;

        private SearchOp(Registry registry) {
            this.registry = registry;
        }

        @Override
        public SearchToolsOutput searchTools(SearchToolsInput input, RequestContext context) {
            var tools = registry.searchTools(input.getToolDescription(), input.getNumberOfTools());
            return SearchToolsOutput.builder()
                    .tools(tools.stream()
                            .map(t -> Tool.builder()
                                    .serverId(t.serverId())
                                    .toolName(t.toolName())
                                    .build())
                            .toList())
                    .build();
        }
    }

    private static ConcurrentHashMap<String, Boolean> ALLOWED_TOOLS = new ConcurrentHashMap<>();

    private final class InstallTool implements InstallToolOperation {

        private final Registry registry;
        private final Config config;

        private InstallTool(Registry registry, Config config) {
            this.registry = registry;
            this.config = config;
        }

        @Override
        public InstallToolOutput installTool(InstallToolInput input, RequestContext context) {
            var tool = input.getTool();
            var toolName = tool.getToolName();
            var serverId = tool.getServerId();
            if (!config.getToolBundles().containsKey(serverId)) {
                var bundle = registry.getMcpBundle(serverId);
                if (bundle == null) {
                    throw new IllegalArgumentException(
                            "Can't find a configured tool bundle for '" + serverId + "'.");
                } else {
                    try {
                        var mcpBundleConfig = ConfigUtils.addMcpBundle(config, serverId, bundle);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                GenericBundle bundle = ConfigUtils.getMcpBundle(serverId).getValue();
                if (!mcpServer.proxies.containsKey(serverId)) {
                    ALLOWED_TOOLS.put(toolName, true);
                    mcpServer.addNewProxy(StdioProxy.builder()
                            .name(serverId)
                            .toolFilter(StartServer::toolFilter)
                            .command(bundle.getRun().getExecutable())
                            .build());
                } else {
                    var proxy = mcpServer.proxies.get(serverId);
                    ALLOWED_TOOLS.put(toolName, true);
                    mcpServer.refreshTools(proxy);
                }
            }
            return InstallToolOutput.builder().message("Tool "+ toolName +" installed. Check your list of tools.").build();
        }
    }

    private static boolean toolFilter(ToolInfo toolInfo) {
        return ALLOWED_TOOLS.containsKey(toolInfo.getName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
