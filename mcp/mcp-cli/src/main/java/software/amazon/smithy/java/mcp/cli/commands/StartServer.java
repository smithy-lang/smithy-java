/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
import software.amazon.smithy.mcp.bundle.api.Registry;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;
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
                        .addInstallServerOperation(new InstallOp(registry, config))
                        .addListServersOperation(new ListOp(registry))
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

    private static final class ListOp implements ListServersOperation {

        private final Registry registry;

        private ListOp(Registry registry) {
            this.registry = registry;
        }

        @Override
        public ListServersOutput listServers(ListServersInput input, RequestContext context) {
            var servers = registry
                    .listMcpBundles()
                    .stream()
                    .unordered()
                    .map(Registry.RegistryEntry::getBundleMetadata)
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

        private final Registry registry;
        private final Config config;

        private InstallOp(Registry registry, Config config) {
            this.registry = registry;
            this.config = config;
        }

        @Override
        public InstallServerOutput installServer(InstallServerInput input, RequestContext context) {
            try {
                if (!config.getToolBundles().containsKey(input.getServerName())) {
                    var bundle = registry.getMcpBundle(input.getServerName());
                    if (bundle == null) {
                        throw new IllegalArgumentException(
                                "Can't find a configured tool bundle for '" + input.getServerName() + "'.");
                    } else {
                        var mcpBundleConfig = ConfigUtils.addMcpBundle(config, input.getServerName(), bundle);
                        mcpServer.addNewService(bundleToService(mcpBundleConfig.getValue()));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return InstallServerOutput.builder().build();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
