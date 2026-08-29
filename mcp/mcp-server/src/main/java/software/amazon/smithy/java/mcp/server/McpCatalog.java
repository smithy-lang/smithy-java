/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.serde.document.Document;
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
    private static final int MAX_ACTIVE_CURSORS = 1_024;
    private static final long CURSOR_TTL_NANOS = TimeUnit.MINUTES.toNanos(5);
    private static final long REMOTE_RETRY_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final AtomicReference<CatalogState> state;
    private final McpProtocolRegistry protocols;
    private final McpServerIdentity identity;
    private final LongSupplier nanoTime;
    private final long remoteRetryCooldownNanos;
    private final AtomicReference<CompletableFuture<Void>> remoteStart = new AtomicReference<>();
    private final Map<RemoteCatalogKey, RemoteLoadState> remoteCatalogLoads =
            new ConcurrentHashMap<>();
    private final ExecutorService notificationRefreshes = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<McpRemoteClient, RefreshState> toolRefreshStates = new ConcurrentHashMap<>();
    private final Map<McpRemoteClient, RefreshState> promptRefreshStates = new ConcurrentHashMap<>();
    private final Map<String, PageCursor<ToolInfo>> toolCursors = new ConcurrentHashMap<>();
    private final Map<String, PageCursor<PromptInfo>> promptCursors = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<JsonRpcRequest>> notificationWriters =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<JsonRpcResponse>> responseWriters =
            new CopyOnWriteArrayList<>();
    private volatile JsonRpcRequest initializeRequest;
    private volatile McpProtocol initializeProtocol;

    McpCatalog(Map<String, Service> services, List<McpRemoteClient> remoteClients) {
        this(
                services,
                remoteClients,
                McpProtocolRegistry.create(List.of(), List.of(), false),
                new McpServerIdentity("mcp-server", "1.0.0"),
                System::nanoTime,
                REMOTE_RETRY_COOLDOWN_NANOS);
    }

    McpCatalog(
            Map<String, Service> services,
            List<McpRemoteClient> remoteClients,
            McpProtocolRegistry protocols,
            McpServerIdentity identity
    ) {
        this(
                services,
                remoteClients,
                protocols,
                identity,
                System::nanoTime,
                REMOTE_RETRY_COOLDOWN_NANOS);
    }

    McpCatalog(
            Map<String, Service> services,
            List<McpRemoteClient> remoteClients,
            McpProtocolRegistry protocols,
            McpServerIdentity identity,
            LongSupplier nanoTime,
            long remoteRetryCooldownNanos
    ) {
        this.protocols = protocols;
        this.identity = identity;
        this.nanoTime = nanoTime;
        this.remoteRetryCooldownNanos = remoteRetryCooldownNanos;
        var clients = new HashMap<String, McpRemoteClient>();
        for (var client : remoteClients) {
            if (clients.put(client.name(), client) != null) {
                throw new IllegalArgumentException("Duplicate remote MCP client: " + client.name());
            }
        }
        var immutableServices = Map.copyOf(services);
        var localSnapshot = createLocalSnapshot(immutableServices, Map.of(), Map.of());
        state = new AtomicReference<>(new CatalogState(
                immutableServices,
                clients,
                Map.of(),
                Map.of(),
                localSnapshot.tools(),
                localSnapshot.prompts(),
                localSnapshot));
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
    public McpCursorPage<McpToolDescriptor> listTools(String cursor) {
        if (cursor == null) {
            var current = state.get();
            var pending = current.toolContinuations()
                    .entrySet()
                    .stream()
                    .map(entry -> new PendingPage<>(entry.getKey(), entry.getValue()))
                    .toList();
            return new McpCursorPage<>(
                    List.copyOf(current.initialTools().values()),
                    registerCursor(toolCursors, pending));
        }

        var cursorState = requireCursor(toolCursors, cursor, "tools");
        var pending = new ArrayList<>(cursorState.pending());
        var current = pending.removeFirst();
        var page = current.nextPage().fetch();
        toolCursors.remove(cursor, cursorState);
        page.nextPage().ifPresent(next -> pending.add(new PendingPage<>(current.client(), next)));
        var descriptors = mergeRemoteToolPage(current.client(), page);
        return new McpCursorPage<>(descriptors, registerCursor(toolCursors, pending));
    }

    @Override
    public McpCursorPage<McpPromptDescriptor> listPrompts(String cursor) {
        if (cursor == null) {
            var current = state.get();
            var pending = current.promptContinuations()
                    .entrySet()
                    .stream()
                    .map(entry -> new PendingPage<>(entry.getKey(), entry.getValue()))
                    .toList();
            return new McpCursorPage<>(
                    List.copyOf(current.initialPrompts().values()),
                    registerCursor(promptCursors, pending));
        }

        var cursorState = requireCursor(promptCursors, cursor, "prompts");
        var pending = new ArrayList<>(cursorState.pending());
        var current = pending.removeFirst();
        var page = current.nextPage().fetch();
        promptCursors.remove(cursor, cursorState);
        page.nextPage().ifPresent(next -> pending.add(new PendingPage<>(current.client(), next)));
        var descriptors = mergeRemotePromptPage(current.client(), page);
        return new McpCursorPage<>(descriptors, registerCursor(promptCursors, pending));
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
        notificationWriters.addIfAbsent(notificationWriter);
        responseWriters.addIfAbsent(responseWriter);
        runOnce(remoteStart, () -> forEachRemoteInParallel("start", McpRemoteClient::start));
    }

    @Override
    public void initializeRemoteClients(McpProtocol protocol) {
        var request = initializeRequest(protocol);
        initializeRequest = request;
        initializeProtocol = protocol;
        forEachRemoteInParallel(
                "initialize",
                client -> loadRemoteCatalog(client, protocol));
    }

    @Override
    public void ensureRemoteCatalogLoaded(McpProtocol requestedProtocol) {
        var protocol = catalogProtocol(requestedProtocol);
        forEachRemoteInParallel(
                "refresh",
                client -> loadRemoteCatalog(client, protocol));
    }

    private McpProtocol catalogProtocol(McpProtocol requestedProtocol) {
        var initialized = initializeProtocol;
        return initialized != null && !requestedProtocol.usesStatelessMetadata()
                ? initialized
                : requestedProtocol;
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

            var initialTools = new HashMap<>(remoteTools(current.initialTools()));
            initialTools.putAll(schemaFactory.createTools(services));
            var initialPrompts = createPromptSnapshot(
                    services,
                    remotePrompts(current.initialPrompts()));

            return new CatalogState(
                    services,
                    current.remoteClients(),
                    current.toolContinuations(),
                    current.promptContinuations(),
                    initialTools,
                    initialPrompts,
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
            return new CatalogState(
                    current.services(),
                    clients,
                    current.toolContinuations(),
                    current.promptContinuations(),
                    current.initialTools(),
                    current.initialPrompts(),
                    current.snapshot());
        });

        try {
            client.start();
            var currentInitializeRequest = initializeRequest;
            if (currentInitializeRequest != null) {
                loadRemoteCatalog(client, initializeProtocol);
            } else {
                loadRemoteCatalog(client, protocols.defaultProtocol());
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

    private void initializeRemote(
            McpRemoteClient client,
            JsonRpcRequest request,
            McpProtocol protocol
    ) {
        client.initialize(
                this::writeResponse,
                notification -> onRemoteNotification(client, notification),
                request,
                protocol,
                protocols);
    }

    private void loadRemoteCatalog(McpRemoteClient client, McpProtocol requestedProtocol) {
        var key = new RemoteCatalogKey(client, requestedProtocol.id());
        var load = remoteCatalogLoads.computeIfAbsent(key, ignored -> new RemoteLoadState());
        if (load.retryAfterNanos().get() > nanoTime.getAsLong()) {
            return;
        }

        runOnce(load.operation(), () -> {
            try {
                var protocol = prepareRemoteClient(client, requestedProtocol);
                client.usingProtocol(protocol, () -> {
                    refresh(client);
                    return null;
                });
                load.retryAfterNanos().set(0);
            } catch (RuntimeException e) {
                load.retryAfterNanos().set(nanoTime.getAsLong() + remoteRetryCooldownNanos);
                throw e;
            }
        });
    }

    private McpProtocol prepareRemoteClient(
            McpRemoteClient client,
            McpProtocol requestedProtocol
    ) {
        if (requestedProtocol.usesStatelessMetadata()) {
            if (client.supportsStatelessProtocol(requestedProtocol)) {
                return requestedProtocol;
            }
            if (client.initialized()) {
                return client.negotiatedProtocol();
            }
        } else if (client.initialized()) {
            return client.negotiatedProtocol();
        }

        var initializationProtocol = requestedProtocol.supportedMethods()
                .contains(McpMethod.Standard.INITIALIZE)
                        ? requestedProtocol
                        : protocols.initializationFallbackProtocol();
        var request = initializeRequest(initializationProtocol);
        initializeRemote(client, request, initializationProtocol);
        return client.negotiatedProtocol();
    }

    private JsonRpcRequest initializeRequest(McpProtocol protocol) {
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(0))
                .method(McpMethod.Standard.INITIALIZE.wireName())
                .params(Document.of(Map.of(
                        "protocolVersion",
                        Document.of(protocol.id().identifier()),
                        "capabilities",
                        Document.of(Map.of()),
                        "clientInfo",
                        Document.of(Map.of(
                                "name",
                                Document.of(identity.name()),
                                "version",
                                Document.of(identity.version()))))))
                .build();
    }

    private void onRemoteNotification(McpRemoteClient client, JsonRpcRequest notification) {
        if (McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED.wireName().equals(notification.getMethod())) {
            scheduleRefresh(client, toolRefreshStates, this::refreshTools);
        } else if (McpMethod.Standard.NOTIFICATIONS_PROMPTS_LIST_CHANGED.wireName().equals(notification.getMethod())) {
            scheduleRefresh(client, promptRefreshStates, this::refreshPrompts);
        }
        notificationWriters.forEach(writer -> writer.accept(notification));
    }

    private void writeResponse(JsonRpcResponse response) {
        responseWriters.forEach(writer -> writer.accept(response));
    }

    private void scheduleRefresh(
            McpRemoteClient client,
            Map<McpRemoteClient, RefreshState> states,
            Consumer<McpRemoteClient> refresh
    ) {
        var state = states.computeIfAbsent(client, ignored -> new RefreshState());
        if (state.request()) {
            notificationRefreshes.submit(() -> runScheduledRefreshes(client, state, refresh));
        }
    }

    private void runScheduledRefreshes(
            McpRemoteClient client,
            RefreshState state,
            Consumer<McpRemoteClient> refresh
    ) {
        while (state.takeRequest()) {
            refresh.accept(client);
        }
    }

    private void refresh(McpRemoteClient client) {
        McpPage<ToolInfo> remoteTools = McpPage.last(List.of());
        boolean toolsLoaded = false;
        try {
            remoteTools = client.listTools();
            toolsLoaded = true;
        } catch (RuntimeException e) {
            LOG.error("Failed to refresh tools from remote MCP client: " + client.name(), e);
        }

        McpPage<PromptInfo> remotePrompts = McpPage.last(List.of());
        boolean promptsLoaded = false;
        try {
            remotePrompts = client.listPrompts();
            promptsLoaded = true;
        } catch (RuntimeException e) {
            LOG.error("Failed to refresh prompts from remote MCP client: " + client.name(), e);
        }

        mergeRemoteSnapshot(client, remoteTools, toolsLoaded, remotePrompts, promptsLoaded);
        if (!toolsLoaded && !promptsLoaded) {
            throw new McpRemoteException(
                    "Remote MCP client did not provide a tools or prompts catalog: " + client.name());
        }
    }

    private void refreshTools(McpRemoteClient client) {
        try {
            mergeRemoteSnapshot(
                    client,
                    client.listTools(),
                    true,
                    McpPage.last(List.of()),
                    false);
        } catch (RuntimeException e) {
            LOG.error("Failed to refresh tools from remote MCP client: " + client.name(), e);
        }
    }

    private void refreshPrompts(McpRemoteClient client) {
        try {
            mergeRemoteSnapshot(
                    client,
                    McpPage.last(List.of()),
                    false,
                    client.listPrompts(),
                    true);
        } catch (RuntimeException e) {
            LOG.error("Failed to refresh prompts from remote MCP client: " + client.name(), e);
        }
    }

    private void mergeRemoteSnapshot(
            McpRemoteClient client,
            McpPage<ToolInfo> remoteTools,
            boolean toolsLoaded,
            McpPage<PromptInfo> remotePrompts,
            boolean promptsLoaded
    ) {
        updateState(current -> {
            var tools = new HashMap<>(current.snapshot().tools());
            var initialTools = new HashMap<>(current.initialTools());
            var toolContinuations = new HashMap<>(current.toolContinuations());
            if (toolsLoaded) {
                initialTools.forEach((name, descriptor) -> {
                    if (descriptor.target() instanceof McpToolDescriptor.RemoteTarget remote
                            && remote.client() == client) {
                        tools.computeIfPresent(name,
                                (
                                        ignored,
                                        existing) -> existing
                                                .target() instanceof McpToolDescriptor.RemoteTarget existingRemote
                                                && existingRemote.client() == client
                                                        ? null
                                                        : existing);
                    }
                });
                for (var info : remoteTools.items()) {
                    putRemoteTool(tools,
                            new McpToolDescriptor(
                                    info,
                                    client.name(),
                                    new McpToolDescriptor.RemoteTarget(client),
                                    McpHttpBinding.headerParameters(info)));
                }
                initialTools.entrySet()
                        .removeIf(entry -> entry.getValue().target() instanceof McpToolDescriptor.RemoteTarget remote
                                && remote.client() == client);
                for (var info : remoteTools.items()) {
                    putRemoteTool(initialTools,
                            new McpToolDescriptor(
                                    info,
                                    client.name(),
                                    new McpToolDescriptor.RemoteTarget(client),
                                    McpHttpBinding.headerParameters(info)));
                }
                replaceContinuation(toolContinuations, client, remoteTools.nextPage());
            }

            var prompts = new HashMap<>(current.snapshot().prompts());
            var initialPrompts = new HashMap<>(current.initialPrompts());
            var promptContinuations = new HashMap<>(current.promptContinuations());
            if (promptsLoaded) {
                initialPrompts.forEach((name, descriptor) -> {
                    if (descriptor.remoteClient() == client) {
                        prompts.computeIfPresent(name,
                                (ignored, existing) -> existing.remoteClient() == client ? null : existing);
                    }
                });
                for (var info : remotePrompts.items()) {
                    putRemotePrompt(
                            prompts,
                            PromptLoader.normalize(info.getName()),
                            new McpPromptDescriptor(new Prompt(info, client), client));
                }
                initialPrompts.entrySet().removeIf(entry -> entry.getValue().remoteClient() == client);
                for (var info : remotePrompts.items()) {
                    putRemotePrompt(
                            initialPrompts,
                            PromptLoader.normalize(info.getName()),
                            new McpPromptDescriptor(new Prompt(info, client), client));
                }
                replaceContinuation(promptContinuations, client, remotePrompts.nextPage());
            }

            return new CatalogState(
                    current.services(),
                    current.remoteClients(),
                    toolContinuations,
                    promptContinuations,
                    initialTools,
                    initialPrompts,
                    new McpSourceSnapshot(
                            Map.copyOf(tools),
                            Map.copyOf(prompts),
                            current.snapshot().documentAdapter()));
        });
    }

    private List<McpToolDescriptor> mergeRemoteToolPage(
            McpRemoteClient client,
            McpPage<ToolInfo> page
    ) {
        var descriptors = page.items()
                .stream()
                .map(info -> new McpToolDescriptor(
                        info,
                        client.name(),
                        new McpToolDescriptor.RemoteTarget(client),
                        McpHttpBinding.headerParameters(info)))
                .toList();
        updateState(current -> {
            var tools = new HashMap<>(current.snapshot().tools());
            descriptors.forEach(descriptor -> putRemoteTool(tools, descriptor));
            return new CatalogState(
                    current.services(),
                    current.remoteClients(),
                    current.toolContinuations(),
                    current.promptContinuations(),
                    current.initialTools(),
                    current.initialPrompts(),
                    new McpSourceSnapshot(
                            Map.copyOf(tools),
                            current.snapshot().prompts(),
                            current.snapshot().documentAdapter()));
        });
        return descriptors;
    }

    private List<McpPromptDescriptor> mergeRemotePromptPage(
            McpRemoteClient client,
            McpPage<PromptInfo> page
    ) {
        var descriptors = page.items()
                .stream()
                .map(info -> new McpPromptDescriptor(new Prompt(info, client), client))
                .toList();
        updateState(current -> {
            var prompts = new HashMap<>(current.snapshot().prompts());
            descriptors.forEach(descriptor -> putRemotePrompt(
                    prompts,
                    PromptLoader.normalize(descriptor.prompt().promptInfo().getName()),
                    descriptor));
            return new CatalogState(
                    current.services(),
                    current.remoteClients(),
                    current.toolContinuations(),
                    current.promptContinuations(),
                    current.initialTools(),
                    current.initialPrompts(),
                    new McpSourceSnapshot(
                            current.snapshot().tools(),
                            Map.copyOf(prompts),
                            current.snapshot().documentAdapter()));
        });
        return descriptors;
    }

    private <T> void replaceContinuation(
            Map<McpRemoteClient, McpPage.NextPage<T>> continuations,
            McpRemoteClient client,
            Optional<McpPage.NextPage<T>> nextPage
    ) {
        if (nextPage.isPresent()) {
            continuations.put(client, nextPage.get());
        } else {
            continuations.remove(client);
        }
    }

    private void putRemoteTool(
            Map<String, McpToolDescriptor> tools,
            McpToolDescriptor candidate
    ) {
        var name = candidate.info().getName();
        var existing = tools.putIfAbsent(name, candidate);
        if (existing != null
                && existing.target() instanceof McpToolDescriptor.RemoteTarget existingRemote
                && candidate.target() instanceof McpToolDescriptor.RemoteTarget candidateRemote
                && existingRemote.client() == candidateRemote.client()) {
            tools.replace(name, existing, candidate);
            return;
        }
        if (existing != null) {
            LOG.warn(
                    "Ignoring remote MCP tool {} from {} because it is already provided by {}",
                    name,
                    candidate.serverId(),
                    existing.serverId());
        }
    }

    private void putRemotePrompt(
            Map<String, McpPromptDescriptor> prompts,
            String name,
            McpPromptDescriptor candidate
    ) {
        var existing = prompts.putIfAbsent(name, candidate);
        if (existing != null && existing.remoteClient() == candidate.remoteClient()) {
            prompts.replace(name, existing, candidate);
        }
    }

    private <T> PageCursor<T> requireCursor(
            Map<String, PageCursor<T>> cursors,
            String cursor,
            String listing
    ) {
        var state = cursors.get(cursor);
        if (state == null || state.expired(System.nanoTime())) {
            if (state != null) {
                cursors.remove(cursor, state);
            }
            throw new McpProtocolException(-32602, "Invalid or expired " + listing + " cursor");
        }
        return state;
    }

    private <T> String registerCursor(
            Map<String, PageCursor<T>> cursors,
            List<PendingPage<T>> pending
    ) {
        if (pending.isEmpty()) {
            return null;
        }
        evictExpiredAndOverflow(cursors);
        var cursor = UUID.randomUUID().toString();
        cursors.put(cursor, new PageCursor<>(pending, System.nanoTime()));
        return cursor;
    }

    private <T> void evictExpiredAndOverflow(Map<String, PageCursor<T>> cursors) {
        var now = System.nanoTime();
        cursors.entrySet().removeIf(entry -> entry.getValue().expired(now));
        while (cursors.size() >= MAX_ACTIVE_CURSORS) {
            var oldest = cursors.entrySet()
                    .stream()
                    .min(Map.Entry.comparingByValue(
                            (left, right) -> Long.compare(left.createdAtNanos(), right.createdAtNanos())))
                    .orElse(null);
            if (oldest == null || !cursors.remove(oldest.getKey(), oldest.getValue())) {
                return;
            }
        }
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
        var tools = new HashMap<>(remoteTools);
        tools.putAll(schemaFactory.createTools(services));

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
            Map<McpRemoteClient, McpPage.NextPage<ToolInfo>> toolContinuations,
            Map<McpRemoteClient, McpPage.NextPage<PromptInfo>> promptContinuations,
            Map<String, McpToolDescriptor> initialTools,
            Map<String, McpPromptDescriptor> initialPrompts,
            McpSourceSnapshot snapshot) {
        private CatalogState {
            services = Map.copyOf(services);
            remoteClients = Map.copyOf(remoteClients);
            toolContinuations = Map.copyOf(toolContinuations);
            promptContinuations = Map.copyOf(promptContinuations);
            initialTools = Map.copyOf(initialTools);
            initialPrompts = Map.copyOf(initialPrompts);
        }
    }

    private record PendingPage<T>(McpRemoteClient client, McpPage.NextPage<T> nextPage) {}

    private record RemoteCatalogKey(McpRemoteClient client, McpProtocolId protocol) {}

    private record RemoteLoadState(
            AtomicReference<CompletableFuture<Void>> operation,
            AtomicLong retryAfterNanos) {
        private RemoteLoadState() {
            this(new AtomicReference<>(), new AtomicLong());
        }
    }

    private record PageCursor<T>(List<PendingPage<T>> pending, long createdAtNanos) {
        private PageCursor {
            pending = List.copyOf(pending);
        }

        boolean expired(long now) {
            return now - createdAtNanos >= CURSOR_TTL_NANOS;
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
