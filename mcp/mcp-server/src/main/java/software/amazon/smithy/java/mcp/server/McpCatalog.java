/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.server.Service;

/**
 * Thread-safe catalog of local and remote MCP tools and prompts.
 *
 * <p>Readers consume immutable snapshots. Mutations rebuild and atomically publish a
 * new snapshot so request execution never observes a partially refreshed catalog.
 */
final class McpCatalog implements McpSources {
    private static final InternalLogger LOG = InternalLogger.getLogger(McpCatalog.class);

    private final AtomicReference<CatalogState> state;
    private final AtomicReference<CompletableFuture<Void>> remoteStart = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> remoteCatalogLoad = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> remoteInitialization = new AtomicReference<>();
    private final ExecutorService notificationRefreshes = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<McpRemoteClient, RefreshState> refreshStates = new ConcurrentHashMap<>();
    private volatile Consumer<JsonRpcRequest> notificationWriter;
    private volatile Consumer<JsonRpcResponse> responseWriter = ignored -> {};
    private volatile JsonRpcRequest initializeRequest;
    private volatile McpProtocol initializeProtocol;

    McpCatalog(Map<String, Service> services, List<McpRemoteClient> remoteClients) {
        var clients = new HashMap<String, McpRemoteClient>();
        for (var client : remoteClients) {
            if (clients.put(client.name(), client) != null) {
                throw new IllegalArgumentException("Duplicate remote MCP client: " + client.name());
            }
        }
        var immutableServices = Map.copyOf(services);
        state = new AtomicReference<>(new CatalogState(
                immutableServices,
                clients,
                createLocalSnapshot(immutableServices, Map.of(), Map.of())));
    }

    @Override
    public McpSourceSnapshot snapshot() {
        return state.get().snapshot();
    }

    @Override
    public McpToolDescriptor tool(String name) {
        return state.get().snapshot().tools().get(name);
    }

    @Override
    public McpPromptDescriptor prompt(String normalizedName) {
        return state.get().snapshot().prompts().get(normalizedName);
    }

    @Override
    public Map<String, McpRemoteClient> remoteClients() {
        return state.get().remoteClients();
    }

    @Override
    public boolean containsServer(String id) {
        var current = state.get();
        return current.services().containsKey(id) || current.remoteClients().containsKey(id);
    }

    @Override
    public void bindTransport(
            Consumer<JsonRpcRequest> notificationWriter,
            Consumer<JsonRpcResponse> responseWriter
    ) {
        this.notificationWriter = notificationWriter;
        this.responseWriter = responseWriter;
        runOnce(remoteStart, () -> forEachRemoteInParallel("start", McpRemoteClient::start));
    }

    @Override
    public void initializeRemoteClients(
            JsonRpcRequest request,
            McpProtocol protocol
    ) {
        initializeRequest = request;
        initializeProtocol = protocol;
        runOnce(remoteInitialization, () -> {
            forEachRemoteInParallel(
                    "initialize",
                    client -> initializeAndRefresh(client, request, protocol, responseWriter));
            remoteCatalogLoad.compareAndSet(null, CompletableFuture.completedFuture(null));
        });
    }

    @Override
    public void ensureRemoteCatalogLoaded() {
        runOnce(remoteCatalogLoad, () -> forEachRemoteInParallel("refresh", this::refresh));
    }

    @Override
    public void addService(String id, Service service) {
        updateState(current -> {
            var services = new HashMap<>(current.services());
            services.put(id, service);

            var schemaIndex = createSchemaIndex(services);
            var schemaFactory = new McpSchemaFactory(schemaIndex);

            var tools = new HashMap<>(current.snapshot().tools());
            tools.entrySet()
                    .removeIf(entry -> entry.getValue().serverId().equals(id)
                            && entry.getValue().target() instanceof McpToolDescriptor.LocalTarget);
            tools.putAll(schemaFactory.createTools(Map.of(id, service)));
            tools.putAll(remoteTools(current.snapshot().tools()));

            return new CatalogState(
                    services,
                    current.remoteClients(),
                    new McpSourceSnapshot(
                            Map.copyOf(tools),
                            createPromptSnapshot(services, remotePrompts(current.snapshot().prompts())),
                            new SmithyDocumentAdapter(schemaIndex)));
        });
    }

    @Override
    public void addRemoteClient(McpRemoteClient client) {
        updateState(current -> {
            var clients = new HashMap<>(current.remoteClients());
            if (clients.put(client.name(), client) != null) {
                throw new IllegalArgumentException("Duplicate remote MCP client: " + client.name());
            }
            return new CatalogState(current.services(), clients, current.snapshot());
        });

        try {
            client.start();
            var currentInitializeRequest = initializeRequest;
            if (currentInitializeRequest != null) {
                initializeAndRefresh(
                        client,
                        currentInitializeRequest,
                        initializeProtocol,
                        responseWriter);
            } else {
                refresh(client);
            }
        } catch (RuntimeException e) {
            LOG.error("Failed to add remote MCP client: " + client.name(), e);
        }
    }

    @Override
    public Map<String, String> headerParameters(String toolName) {
        var tool = state.get().snapshot().tools().get(toolName);
        return tool == null ? Map.of() : tool.headerParameters();
    }

    @Override
    public void close() {
        notificationRefreshes.shutdownNow();
        remoteClients().values().forEach(client -> {
            try {
                client.close();
            } catch (RuntimeException e) {
                LOG.error("Failed to close remote MCP client: " + client.name(), e);
            }
        });
    }

    private void initializeAndRefresh(
            McpRemoteClient client,
            JsonRpcRequest request,
            McpProtocol protocol,
            Consumer<JsonRpcResponse> responseWriter
    ) {
        client.initialize(
                responseWriter,
                notification -> onRemoteNotification(client, notification),
                request,
                protocol);
        refresh(client);
    }

    private void onRemoteNotification(McpRemoteClient client, JsonRpcRequest notification) {
        if (McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED.wireName().equals(notification.getMethod())) {
            scheduleRefresh(client);
        }
        var writer = notificationWriter;
        if (writer != null) {
            writer.accept(notification);
        }
    }

    private void scheduleRefresh(McpRemoteClient client) {
        var state = refreshStates.computeIfAbsent(client, ignored -> new RefreshState());
        if (state.request()) {
            notificationRefreshes.submit(() -> runScheduledRefreshes(client, state));
        }
    }

    private void runScheduledRefreshes(McpRemoteClient client, RefreshState state) {
        while (state.takeRequest()) {
            refresh(client);
        }
    }

    private void refresh(McpRemoteClient client) {
        List<ToolInfo> remoteTools = List.of();
        boolean toolsLoaded = false;
        try {
            remoteTools = List.copyOf(client.listTools());
            toolsLoaded = true;
        } catch (RuntimeException e) {
            LOG.error("Failed to refresh tools from remote MCP client: " + client.name(), e);
        }

        List<PromptInfo> remotePrompts = List.of();
        boolean promptsLoaded = false;
        try {
            remotePrompts = List.copyOf(client.listPrompts());
            promptsLoaded = true;
        } catch (RuntimeException e) {
            LOG.error("Failed to refresh prompts from remote MCP client: " + client.name(), e);
        }

        mergeRemoteSnapshot(client, remoteTools, toolsLoaded, remotePrompts, promptsLoaded);
    }

    private void mergeRemoteSnapshot(
            McpRemoteClient client,
            List<ToolInfo> remoteTools,
            boolean toolsLoaded,
            List<PromptInfo> remotePrompts,
            boolean promptsLoaded
    ) {
        updateState(current -> {
            var tools = new HashMap<>(current.snapshot().tools());
            if (toolsLoaded) {
                tools.entrySet()
                        .removeIf(entry -> entry.getValue().target() instanceof McpToolDescriptor.RemoteTarget remote
                                && remote.client() == client);
                for (var info : remoteTools) {
                    tools.put(
                            info.getName(),
                            new McpToolDescriptor(
                                    info,
                                    client.name(),
                                    new McpToolDescriptor.RemoteTarget(client),
                                    McpHttpBinding.headerParameters(info)));
                }
            }

            var prompts = new HashMap<>(current.snapshot().prompts());
            if (promptsLoaded) {
                prompts.entrySet().removeIf(entry -> entry.getValue().remoteClient() == client);
                for (var info : remotePrompts) {
                    prompts.putIfAbsent(
                            PromptLoader.normalize(info.getName()),
                            new McpPromptDescriptor(new Prompt(info, client), client));
                }
            }

            return new CatalogState(
                    current.services(),
                    current.remoteClients(),
                    new McpSourceSnapshot(
                            Map.copyOf(tools),
                            Map.copyOf(prompts),
                            current.snapshot().documentAdapter()));
        });
    }

    private void forEachRemoteInParallel(
            String action,
            Consumer<McpRemoteClient> operation
    ) {
        var clients = remoteClients().values();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = clients.stream()
                    .map(client -> executor.submit(() -> {
                        try {
                            operation.accept(client);
                        } catch (RuntimeException e) {
                            LOG.error("Failed to " + action + " remote MCP client: " + client.name(), e);
                        }
                    }))
                    .toList();
            for (var task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new McpRemoteException("Interrupted while waiting for remote MCP clients", e);
                } catch (ExecutionException e) {
                    throw new McpRemoteException("Unexpected remote MCP task failure", e.getCause());
                }
            }
        }
    }

    private void runOnce(
            AtomicReference<CompletableFuture<Void>> state,
            Runnable operation
    ) {
        var created = new CompletableFuture<Void>();
        var active = state.compareAndExchange(null, created);
        if (active != null) {
            await(active);
            return;
        }

        try {
            operation.run();
            created.complete(null);
        } catch (RuntimeException e) {
            created.completeExceptionally(e);
            state.compareAndSet(created, null);
            throw e;
        }
    }

    private void await(CompletableFuture<Void> operation) {
        try {
            operation.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private void updateState(UnaryOperator<CatalogState> update) {
        while (true) {
            var current = state.get();
            var updated = update.apply(current);
            if (state.compareAndSet(current, updated)) {
                return;
            }
        }
    }

    private McpSourceSnapshot createLocalSnapshot(
            Map<String, Service> services,
            Map<String, McpToolDescriptor> remoteTools,
            Map<String, McpPromptDescriptor> remotePrompts
    ) {
        var schemaIndex = createSchemaIndex(services);
        var schemaFactory = new McpSchemaFactory(schemaIndex);
        var tools = new HashMap<>(schemaFactory.createTools(services));
        tools.putAll(remoteTools);

        return new McpSourceSnapshot(
                Map.copyOf(tools),
                createPromptSnapshot(services, remotePrompts),
                new SmithyDocumentAdapter(schemaIndex));
    }

    private SchemaIndex createSchemaIndex(Map<String, Service> services) {
        return SchemaIndex.compose(
                services.values().stream().map(Service::schemaIndex).toArray(SchemaIndex[]::new));
    }

    private Map<String, McpPromptDescriptor> createPromptSnapshot(
            Map<String, Service> services,
            Map<String, McpPromptDescriptor> remotePrompts
    ) {
        var prompts = new HashMap<String, McpPromptDescriptor>();
        for (var entry : PromptLoader.loadPrompts(services.values()).entrySet()) {
            prompts.put(entry.getKey(), new McpPromptDescriptor(entry.getValue(), null));
        }
        remotePrompts.forEach(prompts::putIfAbsent);
        return Map.copyOf(prompts);
    }

    private Map<String, McpToolDescriptor> remoteTools(Map<String, McpToolDescriptor> tools) {
        var result = new HashMap<String, McpToolDescriptor>();
        tools.forEach((name, tool) -> {
            if (tool.target() instanceof McpToolDescriptor.RemoteTarget) {
                result.put(name, tool);
            }
        });
        return result;
    }

    private Map<String, McpPromptDescriptor> remotePrompts(Map<String, McpPromptDescriptor> prompts) {
        var result = new HashMap<String, McpPromptDescriptor>();
        prompts.forEach((name, prompt) -> {
            if (prompt.remoteClient() != null) {
                result.put(name, prompt);
            }
        });
        return result;
    }

    private record CatalogState(
            Map<String, Service> services,
            Map<String, McpRemoteClient> remoteClients,
            McpSourceSnapshot snapshot) {
        private CatalogState {
            services = Map.copyOf(services);
            remoteClients = Map.copyOf(remoteClients);
        }
    }

    private static final class RefreshState {
        private static final int RUNNING = 1;
        private static final int REQUESTED = 1 << 1;

        private final AtomicInteger state = new AtomicInteger();

        boolean request() {
            while (true) {
                var current = state.get();
                var updated = current | RUNNING | REQUESTED;
                if (state.compareAndSet(current, updated)) {
                    return (current & RUNNING) == 0;
                }
            }
        }

        boolean takeRequest() {
            while (true) {
                var current = state.get();
                var hasRequest = (current & REQUESTED) != 0;
                var updated = hasRequest ? current & ~REQUESTED : 0;
                if (state.compareAndSet(current, updated)) {
                    return hasRequest;
                }
            }
        }
    }
}
