/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.DATE_TIME;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.EPOCH_SECONDS;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.HTTP_DATE;
import static software.amazon.smithy.java.mcp.server.PromptLoader.normalize;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.framework.model.ValidationException;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.java.mcp.model.CallToolResult;
import software.amazon.smithy.java.mcp.model.Capabilities;
import software.amazon.smithy.java.mcp.model.InitializeResult;
import software.amazon.smithy.java.mcp.model.JsonArraySchema;
import software.amazon.smithy.java.mcp.model.JsonDocumentSchema;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
import software.amazon.smithy.java.mcp.model.JsonOneOfSchema;
import software.amazon.smithy.java.mcp.model.JsonPrimitiveSchema;
import software.amazon.smithy.java.mcp.model.JsonPrimitiveType;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListPromptsResult;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.Prompts;
import software.amazon.smithy.java.mcp.model.ServerInfo;
import software.amazon.smithy.java.mcp.model.TextContent;
import software.amazon.smithy.java.mcp.model.ToolAnnotations;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.mcp.model.Tools;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Core MCP service that handles JSON-RPC requests and returns responses.
 * This class is responsible for processing MCP protocol logic independently
 * of transport concerns.
 */
@SmithyUnstableApi
public final class McpService {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpService.class);
    private static final Context.Key<Boolean> ASYNC_DISPATCH = Context.key("mcp.asyncDispatch");

    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private static final TraitKey<OneOfTrait> ONE_OF_TRAIT = TraitKey.get(OneOfTrait.class);

    // The tool, prompt, proxy, and service registries are held as immutable snapshots behind volatile
    // references (copy-on-write). Readers (tools/list, prompts/list, tools/call dispatch, shutdown)
    // read the current snapshot with no locking and always see a complete, consistent map. Every
    // mutation (proxy init, dynamic add, and tools/list_changed refresh) rebuilds the affected
    // snapshot under registryLock and publishes it atomically, so concurrent mutators cannot lose
    // each other's updates and readers never observe a half-updated registry. Network I/O
    // (proxy.listTools()/listPrompts()) is always performed outside the lock.
    private final Object registryLock = new Object();
    private volatile Map<String, Tool> tools;
    private volatile Map<String, Prompt> prompts;
    private volatile Map<String, McpServerProxy> proxies;
    private volatile Map<String, Service> services;
    private final String serviceName;
    private final String version;
    private final AtomicReference<JsonRpcRequest> initializeRequest = new AtomicReference<>();
    private final ToolFilter toolFilter;
    private final AtomicReference<Boolean> proxiesInitialized = new AtomicReference<>(false);
    private final McpMetricsObserver metricsObserver;
    private final SchemaIndex schemaIndex;
    private final McpServerInterceptor interceptor;
    // Set once via setNotificationWriter() and read later from the refresh/proxy paths on other
    // threads, so it is volatile for safe publication.
    private volatile Consumer<JsonRpcRequest> notificationWriter;

    // Runs tools/list_changed refreshes off the transport's reader thread. A synchronous refresh calls
    // listTools() whose response is read by that same reader thread, so doing it inline deadlocks it.
    // A single thread also serializes refreshes from different proxies with each other. It is a daemon
    // thread that lives for the process; McpService has no explicit lifecycle so it is never shut down.
    private final ExecutorService toolRefreshExecutor =
            Executors.newSingleThreadExecutor(r -> {
                var t = new Thread(r, "mcp-tools-refresh");
                t.setDaemon(true);
                return t;
            });

    McpService(
            Map<String, Service> services,
            List<McpServerProxy> proxyList,
            String name,
            String version,
            ToolFilter toolFilter,
            McpMetricsObserver metricsObserver,
            McpServerInterceptor interceptor
    ) {
        // Only services needs copying: it is supplied by the builder, which may still hold or mutate
        // it. The tools, prompts, and proxies maps are all built fresh here and never referenced
        // again, so snapshot() can wrap them without copying.
        this.services = snapshot(new LinkedHashMap<>(services));
        this.schemaIndex =
                SchemaIndex.compose(services.values().stream().map(Service::schemaIndex).toArray(SchemaIndex[]::new));
        this.tools = snapshot(createTools(services));
        this.prompts = snapshot(PromptLoader.loadPrompts(services.values()));
        this.serviceName = name;
        this.version = version;
        var proxyMap = new LinkedHashMap<String, McpServerProxy>();
        for (var proxy : proxyList) {
            proxyMap.put(proxy.name(), proxy);
        }
        this.proxies = snapshot(proxyMap);
        this.toolFilter = toolFilter;
        this.metricsObserver = metricsObserver;
        this.interceptor = interceptor;
    }

    /**
     * Handles a JSON-RPC request, invoking interceptor hooks at each stage of the pipeline.
     *
     * <p>Responses are delivered through one of two channels:
     * <ul>
     *   <li><b>Synchronous (return value):</b> For most requests, the response is returned directly.</li>
     *   <li><b>Asynchronous (callback):</b> For proxy tool calls, returns {@code null} and the callback
     *       is invoked when the proxy responds.</li>
     *   <li><b>Neither:</b> For notifications and unknown methods, returns {@code null} and the callback
     *       is never invoked.</li>
     * </ul>
     *
     * @param req The JSON-RPC request to handle
     * @param asyncResponseCallback Callback for async responses (used for proxy calls)
     * @param protocolVersion The protocol version for this request (may be null)
     * @return The response for synchronous operations, or null for async/notification operations
     */
    public JsonRpcResponse handleRequest(
            JsonRpcRequest req,
            Consumer<JsonRpcResponse> asyncResponseCallback,
            ProtocolVersion protocolVersion
    ) {
        // Zero-interceptor fast path: skip Context creation, hook allocation, and all hook invocations.
        if (interceptor == McpServerInterceptor.NOOP) {
            return handleRequestDirect(req, asyncResponseCallback, protocolVersion);
        }

        var hook = new McpExecutionHook(req, protocolVersion, Context.create());
        JsonRpcResponse response = null;
        RuntimeException caughtError = null;

        try {
            var currentReq = fireBeforeExecution(hook);
            hook = hook.withRequest(currentReq);

            // Dispatch
            validate(currentReq);
            var method = currentReq.getMethod();
            response = switch (method) {
                case "initialize" -> handleInitialize(currentReq);
                case "ping" -> handlePing(currentReq);
                default -> {
                    initializeProxies(rpcResponse -> {});
                    yield switch (method) {
                        case "prompts/list" -> handlePromptsList(currentReq);
                        case "prompts/get" -> handlePromptsGet(currentReq);
                        case "tools/list" -> handleToolsList(currentReq, protocolVersion);
                        case "tools/call" ->
                            handleToolsCall(currentReq, asyncResponseCallback, protocolVersion, hook);
                        default -> null;
                    };
                }
            };
            if (Boolean.TRUE.equals(hook.context().get(ASYNC_DISPATCH))) {
                return null;
            }
        } catch (RuntimeException e) {
            caughtError = e;
        } catch (Exception e) {
            caughtError = new RuntimeException(e);
        }

        return fireAfterExecution(hook, response, caughtError);
    }

    /**
     * Direct dispatch path used when no interceptor is configured. Avoids Context creation,
     * hook allocation, and all hook invocations.
     */
    private JsonRpcResponse handleRequestDirect(
            JsonRpcRequest req,
            Consumer<JsonRpcResponse> asyncResponseCallback,
            ProtocolVersion protocolVersion
    ) {
        try {
            validate(req);
            var method = req.getMethod();
            return switch (method) {
                case "initialize" -> handleInitialize(req);
                case "ping" -> handlePing(req);
                default -> {
                    initializeProxies(rpcResponse -> {});
                    yield switch (method) {
                        case "prompts/list" -> handlePromptsList(req);
                        case "prompts/get" -> handlePromptsGet(req);
                        case "tools/list" -> handleToolsList(req, protocolVersion);
                        case "tools/call" ->
                            handleToolsCallDirect(req, asyncResponseCallback, protocolVersion);
                        default -> null; // Notifications or unknown methods
                    };
                }
            };
        } catch (Exception e) {
            return createErrorResponse(req, e);
        }
    }

    private JsonRpcRequest fireBeforeExecution(McpExecutionHook hook) {
        interceptor.readBeforeExecution(hook);
        return interceptor.modifyBeforeExecution(hook);
    }

    private JsonRpcRequest fireBeforeToolCall(McpToolCallHook hook) {
        interceptor.readBeforeToolCall(hook);
        return interceptor.modifyBeforeToolCall(hook);
    }

    private JsonRpcResponse fireAfterExecution(
            McpExecutionHook hook,
            JsonRpcResponse response,
            RuntimeException error
    ) {
        try {
            interceptor.readAfterExecution(hook, response, error);
        } catch (RuntimeException e) {
            error = swapError("readAfterExecution", error, e);
        }
        try {
            response = interceptor.modifyAfterExecution(hook, response, error);
            error = null;
        } catch (RuntimeException e) {
            error = e;
        }
        if (error != null) {
            return createErrorResponse(hook.request(), error);
        }
        return response;
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest req) {
        if (metricsObserver != null) {
            var params = req.getParams();
            var clientInfo = params.getMember("clientInfo");
            var capabilities = params.getMember("capabilities");

            String extractedProtocolVersion = params.getMember("protocolVersion") != null
                    ? params.getMember("protocolVersion").asString()
                    : null;

            String clientName = clientInfo != null && clientInfo.getMember("name") != null
                    ? clientInfo.getMember("name").asString()
                    : null;

            String clientTitle = clientInfo != null && clientInfo.getMember("title") != null
                    ? clientInfo.getMember("title").asString()
                    : null;

            boolean rootsListChanged = capabilities != null
                    && capabilities.getMember("roots") != null
                    && capabilities.getMember("roots").getMember("listChanged") != null
                    && capabilities.getMember("roots").getMember("listChanged").asBoolean();

            boolean sampling = capabilities != null && capabilities.getMember("sampling") != null;
            boolean elicitation = capabilities != null && capabilities.getMember("elicitation") != null;

            metricsObserver.onInitialize("initialize",
                    extractedProtocolVersion,
                    rootsListChanged,
                    sampling,
                    elicitation,
                    clientName,
                    clientTitle);
        }

        this.initializeRequest.compareAndSet(null, req);

        initializeProxies(rpcResponse -> {});

        var maybeVersion = req.getParams().getMember("protocolVersion");
        String pv = null;
        if (maybeVersion != null) {
            var protocolVersion = ProtocolVersion.version(maybeVersion.asString());
            if (!(protocolVersion instanceof ProtocolVersion.UnknownVersion)) {
                pv = protocolVersion.identifier();
            }
        }

        var builder = InitializeResult.builder();
        if (pv != null) {
            builder.protocolVersion(pv);
        }

        var result = builder
                .capabilities(Capabilities.builder()
                        .tools(Tools.builder().listChanged(true).build())
                        .prompts(Prompts.builder().listChanged(true).build())
                        .build())
                .serverInfo(ServerInfo.builder()
                        .name(serviceName)
                        .version(version)
                        .build())
                .build();

        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handlePing(JsonRpcRequest req) {
        return JsonRpcResponse.builder()
                .id(req.getId())
                .result(Document.of(Map.of()))
                .jsonrpc("2.0")
                .build();
    }

    private JsonRpcResponse handlePromptsList(JsonRpcRequest req) {
        var promptValues = prompts.values();
        var promptInfos = new ArrayList<PromptInfo>(promptValues.size());
        for (var prompt : promptValues) {
            promptInfos.add(prompt.promptInfo());
        }
        var result = ListPromptsResult.builder()
                .prompts(promptInfos)
                .build();
        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handlePromptsGet(JsonRpcRequest req) {
        var promptName = req.getParams().getMember("name").asString();
        var promptArguments = req.getParams().getMember("arguments");

        var prompt = prompts.get(normalize(promptName));

        if (prompt == null) {
            throw new RuntimeException("Prompt not found: " + promptName);
        }

        var result = prompt.getPromptResult(promptArguments, req.getId());
        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handleToolsList(JsonRpcRequest req, ProtocolVersion protocolVersion) {
        var toolValues = tools.values();
        var toolInfos = new ArrayList<ToolInfo>(toolValues.size());
        for (var tool : toolValues) {
            if (toolFilter.allowTool(tool.serverId(), tool.toolInfo().getName())) {
                toolInfos.add(extractToolInfo(tool, protocolVersion));
            }
        }
        var result = ListToolsResult.builder()
                .tools(toolInfos)
                .build();
        return createSuccessResponse(req.getId(), result);
    }

    private JsonRpcResponse handleToolsCall(
            JsonRpcRequest req,
            Consumer<JsonRpcResponse> asyncResponseCallback,
            ProtocolVersion protocolVersion,
            McpExecutionHook executionHook
    ) {
        if (metricsObserver != null) {
            String toolName = req.getParams().getMember("name") != null
                    ? req.getParams().getMember("name").asString()
                    : null;
            metricsObserver.onToolCall("tools/call", toolName);
        }

        var operationName = req.getParams().getMember("name").asString();
        var tool = tools.get(operationName);

        if (tool == null) {
            return createErrorResponse(req, "No such tool: " + operationName);
        }

        var toolHook = new McpToolCallHook(
                req,
                protocolVersion,
                executionHook.context(),
                operationName,
                tool.serverId(),
                tool.proxy() != null);

        ToolResult result;
        try {
            var currentReq = fireBeforeToolCall(toolHook);
            toolHook = toolHook.withRequest(currentReq);

            if (tool.proxy() != null) {
                return dispatchProxy(tool, currentReq, toolHook, executionHook, asyncResponseCallback);
            }

            result = dispatchLocal(tool, currentReq, protocolVersion);
        } catch (RuntimeException e) {
            result = ToolResult.failure(e);
        }

        return fireAfterToolCall(toolHook, result.response(), result.error());
    }

    private JsonRpcResponse dispatchProxy(
            Tool tool,
            JsonRpcRequest currentReq,
            McpToolCallHook toolHook,
            McpExecutionHook executionHook,
            Consumer<JsonRpcResponse> asyncResponseCallback
    ) {
        JsonRpcRequest proxyRequest = JsonRpcRequest.builder()
                .id(currentReq.getId())
                .method(currentReq.getMethod())
                .params(currentReq.getParams())
                .jsonrpc(currentReq.getJsonrpc())
                .build();

        executionHook.context().put(ASYNC_DISPATCH, true);

        var finalToolHook = toolHook;
        tool.proxy().rpc(proxyRequest).thenAccept(response -> {
            var finalResponse = fireAfterToolCall(finalToolHook, response, null);
            finalResponse = fireAfterExecution(executionHook, finalResponse, null);
            asyncResponseCallback.accept(finalResponse);
        }).exceptionally(ex -> {
            var proxyError = new RuntimeException("Proxy error: " + ex.getMessage(), ex);
            var errorResponse = fireAfterToolCall(finalToolHook, null, proxyError);
            if (errorResponse == null) {
                errorResponse = createErrorResponse(finalToolHook.request(), proxyError);
            }
            errorResponse = fireAfterExecution(executionHook, errorResponse, null);
            asyncResponseCallback.accept(errorResponse);
            return null;
        });

        return null;
    }

    private ToolResult dispatchLocal(Tool tool, JsonRpcRequest req, ProtocolVersion protocolVersion) {
        try {
            var operation = tool.operation();
            var argumentsDoc = req.getParams().getMember("arguments");
            var adaptedDoc = adaptDocument(argumentsDoc, operation.getApiOperation().inputSchema());
            var input = adaptedDoc.asShape(operation.getApiOperation().inputBuilder());
            var output = operation.function().apply(input, null);
            var result = formatStructuredContent(tool, (SerializableShape) output, protocolVersion);
            return ToolResult.success(createSuccessResponse(req.getId(), result));
        } catch (RuntimeException e) {
            return ToolResult.failure(e);
        }
    }

    /**
     * Direct tool dispatch used when no interceptor is configured. No hooks are invoked.
     */
    private JsonRpcResponse handleToolsCallDirect(
            JsonRpcRequest req,
            Consumer<JsonRpcResponse> asyncResponseCallback,
            ProtocolVersion protocolVersion
    ) {
        if (metricsObserver != null) {
            String toolName = req.getParams().getMember("name") != null
                    ? req.getParams().getMember("name").asString()
                    : null;
            metricsObserver.onToolCall("tools/call", toolName);
        }

        var operationName = req.getParams().getMember("name").asString();
        var tool = tools.get(operationName);

        if (tool == null) {
            return createErrorResponse(req, "No such tool: " + operationName);
        }

        if (tool.proxy() != null) {
            JsonRpcRequest proxyRequest = JsonRpcRequest.builder()
                    .id(req.getId())
                    .method(req.getMethod())
                    .params(req.getParams())
                    .jsonrpc(req.getJsonrpc())
                    .build();

            tool.proxy()
                    .rpc(proxyRequest)
                    .thenAccept(asyncResponseCallback)
                    .exceptionally(ex -> {
                        LOG.error("Error from proxy RPC", ex);
                        asyncResponseCallback.accept(
                                createErrorResponse(req, new RuntimeException("Proxy error: " + ex.getMessage(), ex)));
                        return null;
                    });
            return null;
        } else {
            var operation = tool.operation();
            var argumentsDoc = req.getParams().getMember("arguments");
            var adaptedDoc = adaptDocument(argumentsDoc, operation.getApiOperation().inputSchema());
            var input = adaptedDoc.asShape(operation.getApiOperation().inputBuilder());
            var output = operation.function().apply(input, null);
            var result = formatStructuredContent(tool, (SerializableShape) output, protocolVersion);
            return createSuccessResponse(req.getId(), result);
        }
    }

    private JsonRpcResponse fireAfterToolCall(
            McpToolCallHook hook,
            JsonRpcResponse response,
            RuntimeException error
    ) {
        try {
            interceptor.readAfterToolCall(hook, response, error);
        } catch (RuntimeException e) {
            error = swapError("readAfterToolCall", error, e);
        }
        try {
            response = interceptor.modifyAfterToolCall(hook, response, error);
            error = null;
        } catch (RuntimeException e) {
            error = e;
        }
        if (error != null) {
            return createErrorResponse(hook.request(), error);
        }
        return response;
    }

    private static RuntimeException swapError(String hook, RuntimeException oldE, RuntimeException newE) {
        if (oldE != null && oldE != newE) {
            LOG.trace("Replacing error after {}: {} -> {}",
                    hook,
                    oldE.getClass().getName(),
                    newE.getClass().getName());
        }
        return newE;
    }

    /**
     * Sets the notification writer for forwarding notifications from proxies.
     */
    public void setNotificationWriter(Consumer<JsonRpcRequest> notificationWriter) {
        this.notificationWriter = notificationWriter;
    }

    /**
     * Creates a notification writer for a specific proxy that handles cache invalidation
     * for only that proxy's tools.
     */
    private Consumer<JsonRpcRequest> createProxyNotificationWriter(
            McpServerProxy proxy,
            Consumer<JsonRpcRequest> baseNotificationWriter
    ) {
        return notification -> {
            if ("notifications/tools/list_changed".equals(notification.getMethod())) {
                LOG.debug("Received tools/list_changed notification from proxy: {}", proxy.name());
                // Refresh on a separate thread. This notification is delivered on the proxy's transport
                // reader thread, and refreshProxyTools() calls listTools() whose response is read by that
                // same thread, so doing it inline would deadlock the reader.
                toolRefreshExecutor.execute(() -> refreshProxyTools(proxy));
            }
            // Forward the notification
            if (baseNotificationWriter != null) {
                baseNotificationWriter.accept(notification);
            }
        };
    }

    /**
     * Re-fetches a proxy's tools after a {@code tools/list_changed} notification and swaps them into the
     * registry. Runs off the transport reader thread (see caller). The network fetch happens outside
     * {@code registryLock}; only the in-memory snapshot swap is locked. Fetches first so a failed or
     * slow refresh never wipes the current tools, then adds the new set before pruning this proxy's
     * stale entries, so a concurrent {@code tools/list} never observes a gap (at worst a brief superset).
     */
    void refreshProxyTools(McpServerProxy proxy) {
        List<ToolInfo> proxyTools;
        try {
            proxyTools = proxy.listTools();
        } catch (Exception e) {
            LOG.error("Failed to re-fetch tools from proxy: {}", proxy.name(), e);
            return;
        }
        // Fast path: the proxy reports no tools and has none currently registered, so there is
        // nothing to add and nothing to prune. Reads the current snapshot lock-free and avoids the
        // set allocation, map copy, and lock entirely. (If a tool for this proxy is added
        // concurrently right after this check, that add publishes it and a later refresh reconciles.)
        if (proxyTools.isEmpty() && !hasToolsFor(proxy)) {
            return;
        }
        synchronized (registryLock) {
            Set<String> newNames = new HashSet<>();
            // LinkedHashMap so a refresh keeps the existing order of every other tool: re-put entries
            // stay in place and genuinely new tools append, rather than the whole listing reshuffling
            // to hash order on each tools/list_changed.
            var next = new LinkedHashMap<>(tools);
            for (var toolInfo : proxyTools) {
                newNames.add(toolInfo.getName());
                next.put(toolInfo.getName(), new Tool(toolInfo, proxy.name(), proxy));
            }
            next.entrySet()
                    .removeIf(entry -> entry.getValue().proxy() == proxy && !newNames.contains(entry.getKey()));
            tools = snapshot(next);
        }
    }

    /** Whether any currently registered tool belongs to the given proxy. Reads the current snapshot. */
    private boolean hasToolsFor(McpServerProxy proxy) {
        for (var tool : tools.values()) {
            if (tool.proxy() == proxy) {
                return true;
            }
        }
        return false;
    }

    /**
     * Publishes a registry snapshot by wrapping the given map unmodifiable. The caller hands off
     * ownership: the argument must be a freshly built map that is never mutated or retained after
     * this call, since it becomes the live snapshot without being copied. Callers build these as
     * {@link LinkedHashMap}s so {@code tools/list} and {@code prompts/list} return a stable,
     * deterministic insertion order across refreshes and dynamic additions.
     */
    private static <K, V> Map<K, V> snapshot(Map<K, V> ownedMap) {
        return Collections.unmodifiableMap(ownedMap);
    }

    /**
     * Starts proxies without initializing them.
     */
    public void startProxies() {
        for (McpServerProxy proxy : proxies.values()) {
            try {
                proxy.start();
            } catch (Exception e) {
                LOG.error("Failed to start proxy: " + proxy.name(), e);
            }
        }
    }

    /**
     * Initializes proxies with the actual initialize request.
     */
    public void initializeProxies(Consumer<JsonRpcResponse> responseWriter) {
        if (proxiesInitialized.compareAndSet(false, true)) {
            JsonRpcRequest initRequest = initializeRequest.get();
            var protocolVersion = ProtocolVersion.defaultVersion();
            if (initRequest != null) {
                var maybeVersion = initRequest.getParams().getMember("protocolVersion");
                if (maybeVersion != null) {
                    var pv = ProtocolVersion.version(maybeVersion.asString());
                    if (!(pv instanceof ProtocolVersion.UnknownVersion)) {
                        protocolVersion = pv;
                    }
                }
            }

            for (McpServerProxy proxy : proxies.values()) {
                if (initRequest != null) {
                    var proxyNotificationWriter = createProxyNotificationWriter(proxy, notificationWriter);
                    proxy.initialize(responseWriter, proxyNotificationWriter, initRequest, protocolVersion);
                }
                // Isolate each proxy: a failure fetching one proxy's tools or prompts must not abort
                // discovery for the rest.
                registerProxyListing(proxy);
            }
        }
    }

    /**
     * Fetches a proxy's tools and prompts (outside {@code registryLock}) and merges them into the
     * registries under the lock. A failure fetching either list is logged and skipped so it cannot
     * abort discovery for other proxies.
     */
    private void registerProxyListing(McpServerProxy proxy) {
        List<ToolInfo> proxyTools = null;
        try {
            proxyTools = proxy.listTools();
        } catch (Exception e) {
            LOG.error("Failed to fetch tools from proxy: {}", proxy.name(), e);
        }

        List<PromptInfo> proxyPrompts = null;
        try {
            proxyPrompts = proxy.listPrompts();
        } catch (Exception e) {
            LOG.error("Failed to fetch prompts from proxy: {}", proxy.name(), e);
        }

        if (proxyTools == null && proxyPrompts == null) {
            return;
        }

        synchronized (registryLock) {
            if (proxyTools != null) {
                var nextTools = new LinkedHashMap<>(tools);
                for (var toolInfo : proxyTools) {
                    nextTools.put(toolInfo.getName(), new Tool(toolInfo, proxy.name(), proxy));
                }
                tools = snapshot(nextTools);
            }
            if (proxyPrompts != null) {
                var nextPrompts = new LinkedHashMap<>(prompts);
                for (var promptInfo : proxyPrompts) {
                    var normalizedName = PromptLoader.normalize(promptInfo.getName());
                    nextPrompts.putIfAbsent(normalizedName, new Prompt(promptInfo, proxy));
                }
                prompts = snapshot(nextPrompts);
            }
        }
    }

    /**
     * Gets the current initialize request if one has been received.
     */
    public JsonRpcRequest getInitializeRequest() {
        return initializeRequest.get();
    }

    /**
     * Adds a new service and updates the tools map.
     */
    public void addNewService(String id, Service service) {
        var newTools = createTools(Map.of(id, service));
        synchronized (registryLock) {
            var nextServices = new LinkedHashMap<>(services);
            nextServices.put(id, service);
            services = snapshot(nextServices);

            var nextTools = new LinkedHashMap<>(tools);
            nextTools.putAll(newTools);
            tools = snapshot(nextTools);
        }
    }

    public void addNewProxy(
            McpServerProxy mcpServerProxy,
            Consumer<JsonRpcResponse> responseWriter
    ) {
        synchronized (registryLock) {
            var nextProxies = new LinkedHashMap<>(proxies);
            nextProxies.put(mcpServerProxy.name(), mcpServerProxy);
            proxies = snapshot(nextProxies);
        }

        mcpServerProxy.start();

        // Fetches tools/prompts (network I/O) outside the lock, then swaps under it.
        registerProxyListing(mcpServerProxy);
    }

    /**
     * Checks if a service or proxy with the given ID exists.
     */
    public boolean containsMcpServer(String id) {
        return services.containsKey(id) || proxies.containsKey(id);
    }

    /**
     * Returns an immutable snapshot of the registered proxies at the time of the call. Subsequent
     * additions via {@link #addNewProxy} are not reflected in a previously returned snapshot.
     */
    public Map<String, McpServerProxy> getProxies() {
        return proxies;
    }

    private boolean supportsOutputSchema(ProtocolVersion protocolVersion) {
        return protocolVersion != null && protocolVersion.compareTo(ProtocolVersion.v2025_06_18.INSTANCE) >= 0;
    }

    private boolean supportsAnnotations(ProtocolVersion protocolVersion) {
        return protocolVersion != null && protocolVersion.compareTo(ProtocolVersion.v2025_03_26.INSTANCE) >= 0;
    }

    private CallToolResult formatStructuredContent(
            Tool tool,
            SerializableShape output,
            ProtocolVersion protocolVersion
    ) {
        var adaptedOutput = adaptOutputDocument(Document.of(output), tool.operation().getApiOperation().outputSchema());
        var result = CallToolResult.builder()
                .content(List.of(TextContent.builder()
                        .text(CODEC.serializeToString(adaptedOutput))
                        .build()));

        if (supportsOutputSchema(protocolVersion)) {
            result.structuredContent(adaptedOutput);
        }

        return result.build();
    }

    private ToolInfo extractToolInfo(Tool tool, ProtocolVersion protocolVersion) {
        var toolInfo = tool.toolInfo();
        boolean stripOutput = !supportsOutputSchema(protocolVersion) && toolInfo.getOutputSchema() != null;
        boolean stripAnnotations = !supportsAnnotations(protocolVersion) && toolInfo.getAnnotations() != null;
        if (!stripOutput && !stripAnnotations) {
            return toolInfo;
        }
        var builder = toolInfo.toBuilder();
        if (stripOutput) {
            builder.outputSchema(null);
        }
        if (stripAnnotations) {
            builder.annotations(null);
        }
        return builder.build();
    }

    private void validate(JsonRpcRequest req) {
        Document id = req.getId();
        boolean isRequest = !req.getMethod().startsWith("notifications/");
        if (isRequest) {
            if (id == null) {
                throw ValidationException.builder()
                        .withoutStackTrace()
                        .message("Requests are expected to have ids")
                        .build();
            } else if (!(id.isType(ShapeType.INTEGER) || id.isType(ShapeType.STRING))) {
                throw ValidationException.builder()
                        .withoutStackTrace()
                        .message("Request id is of invalid type " + id.type().name())
                        .build();
            }
        }
    }

    private JsonRpcResponse createSuccessResponse(Document id, SerializableShape value) {
        return JsonRpcResponse.builder()
                .id(id)
                .result(Document.of(value))
                .jsonrpc("2.0")
                .build();
    }

    private JsonRpcResponse createErrorResponse(JsonRpcRequest req, Exception exception) {
        return createErrorResponse(req, exception, true); //TODO change the default to false.
    }

    private JsonRpcResponse createErrorResponse(JsonRpcRequest req, Throwable exception, boolean sendStackTrace) {
        String s;
        exception = unwrapException(exception);
        if (sendStackTrace) {
            try (var sw = new StringWriter();
                    var pw = new PrintWriter(sw)) {
                exception.printStackTrace(pw);
                s = sw.toString().replace("\n", "| ");
            } catch (Exception e) {
                LOG.error("Error encoding response", e);
                throw new RuntimeException(e);
            }
        } else {
            s = exception.getMessage();
        }
        return createErrorResponse(req, s);
    }

    private Throwable unwrapException(Throwable exception) {
        return switch (exception) {
            case CompletionException ce when ce.getCause() != null -> ce.getCause();
            case ExecutionException ee when ee.getCause() != null -> ee.getCause();
            default -> exception;
        };
    }

    private JsonRpcResponse createErrorResponse(JsonRpcRequest req, String s) {
        var error = JsonRpcErrorResponse.builder()
                .code(500)
                .message(s)
                .build();
        return JsonRpcResponse.builder()
                .id(req.getId())
                .error(error)
                .jsonrpc("2.0")
                .build();
    }

    private Map<String, Tool> createTools(Map<String, Service> services) {
        var tools = new LinkedHashMap<String, Tool>();
        for (var entry : services.entrySet()) {
            var id = entry.getKey();
            var service = entry.getValue();
            var serviceName = service.schema().id().getName();
            var cache = new HashMap<ShapeId, SerializableShape>();
            for (var operation : service.getAllOperations()) {
                var operationName = operation.name();
                Schema schema = operation.getApiOperation().schema();
                var toolInfo = ToolInfo.builder()
                        .name(operationName)
                        .description(createDescription(serviceName,
                                operationName,
                                schema))
                        .inputSchema(createJsonObjectSchema(
                                operation.getApiOperation().inputSchema(),
                                operation.getApiOperation().inputSchema(),
                                new HashSet<>(),
                                cache))
                        .outputSchema(createJsonObjectSchema(
                                operation.getApiOperation().outputSchema(),
                                operation.getApiOperation().outputSchema(),
                                new HashSet<>(),
                                cache))
                        .annotations(createAnnotations(schema))
                        .build();
                tools.put(operationName, new Tool(toolInfo, id, operation));
            }
        }
        return tools;
    }

    private ToolAnnotations createAnnotations(Schema operationSchema) {
        boolean isReadOnly = operationSchema.hasTrait(TraitKey.READ_ONLY_TRAIT);
        boolean isIdempotent = operationSchema.hasTrait(TraitKey.IDEMPOTENT_TRAIT);
        if (!isReadOnly && !isIdempotent) {
            return null;
        }
        var builder = ToolAnnotations.builder();
        if (isReadOnly) {
            builder.readOnlyHint(true);
        }
        if (isIdempotent) {
            builder.idempotentHint(true);
        }
        return builder.build();
    }

    private JsonObjectSchema createJsonObjectSchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var targetId = target.id();

        var cached = cache.get(targetId);
        if (cached != null) {
            return (JsonObjectSchema) withDescription(cached, memberDescription(member));
        }

        if (!visited.add(targetId)) {
            // if we're in a recursive cycle, just say "type": "object" and bail
            return JsonObjectSchema.builder().build();
        }

        var properties = new HashMap<String, Document>();
        var requiredProperties = new ArrayList<String>();
        for (var m : target.members()) {
            var name = m.memberName();
            if (m.hasTrait(TraitKey.REQUIRED_TRAIT)) {
                requiredProperties.add(name);
            }

            var jsonSchema = createMemberSchema(m, visited, cache);

            properties.put(name, Document.of(jsonSchema));
        }

        visited.remove(targetId);

        // Cache without description so it can be reused with different member descriptions
        var result = JsonObjectSchema.builder()
                .properties(properties)
                .required(requiredProperties)
                .build();
        cache.put(targetId, result);

        return (JsonObjectSchema) withDescription(result, memberDescription(member));
    }

    private JsonArraySchema createJsonArraySchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var listMember = target.listMember();
        var items = createMemberSchema(listMember, visited, cache);

        // For sparse lists, allow null items using anyOf
        Document itemsSchema;
        if (target.hasTrait(TraitKey.SPARSE_TRAIT)) {
            var nullSchema = Map.of("type", Document.of("null"));
            itemsSchema = Document.of(Map.of(
                    "anyOf",
                    Document.of(List.of(Document.of(items), Document.of(nullSchema)))));
        } else {
            itemsSchema = Document.of(items);
        }

        return JsonArraySchema.builder()
                .description(memberDescription(member))
                .items(itemsSchema)
                .build();
    }

    private JsonPrimitiveSchema createJsonPrimitiveSchema(Schema member) {
        var type = switch (member.type()) {
            case BYTE, SHORT, INTEGER, INT_ENUM, LONG, FLOAT, DOUBLE -> JsonPrimitiveType.NUMBER;
            case ENUM, BLOB, STRING, BIG_DECIMAL, BIG_INTEGER, TIMESTAMP -> JsonPrimitiveType.STRING;
            case BOOLEAN -> JsonPrimitiveType.BOOLEAN;
            default -> throw new RuntimeException(member + " is not a primitive type");
        };

        var builder = JsonPrimitiveSchema.builder()
                .type(type)
                .description(memberDescription(member));

        // Add format annotation for timestamps per JSON Schema spec
        if (member.type() == ShapeType.TIMESTAMP) {
            builder.format("date-time");
        }

        List<Document> enumValues = switch (member.type()) {
            case ENUM, STRING -> member.stringEnumValues().stream().map(Document::of).toList();
            case INT_ENUM -> member.intEnumValues().stream().map(Document::of).toList();
            default -> List.of();
        };

        if (!enumValues.isEmpty()) {
            builder.enumValues(enumValues);
        }

        return builder.build();
    }

    private static final List<String> DOCUMENT_TYPES = List.of(
            "string",
            "number",
            "boolean",
            "object",
            "array",
            "null");

    private JsonDocumentSchema createJsonDocumentSchema(Schema member) {
        return JsonDocumentSchema.builder()
                .type(DOCUMENT_TYPES)
                .description(memberDescription(member))
                .build();
    }

    private SerializableShape createJsonDocumentSchema(
            Schema member,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var targetSchema = member.isMember() ? member.memberTarget() : member;
        var oneOfTrait = targetSchema.getTrait(ONE_OF_TRAIT);

        if (oneOfTrait != null) {
            return createJsonOneOfSchema(oneOfTrait, member, visited, cache);
        } else {
            return createJsonDocumentSchema(member);
        }
    }

    private SerializableShape createJsonOneOfSchema(
            OneOfTrait oneOfTrait,
            Schema documentMember,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var targetId = (documentMember.isMember() ? documentMember.memberTarget() : documentMember).id();

        var cached = cache.get(targetId);
        if (cached != null) {
            return withDescription(cached, memberDescription(documentMember));
        }

        if (!visited.add(targetId)) {
            return JsonObjectSchema.builder().build();
        }

        var oneOfVariants = new ArrayList<Document>();

        for (var memberDef : oneOfTrait.getMembers()) {
            var memberName = memberDef.getName();
            var targetShapeId = memberDef.getTarget();

            var targetSchema = schemaIndex.getSchema(targetShapeId);
            var memberSchema = createJsonObjectSchema(targetSchema, targetSchema, visited, cache);

            oneOfVariants.add(createUnionVariant(memberName, memberSchema));
        }

        visited.remove(targetId);

        var result = JsonOneOfSchema.builder()
                .oneOf(oneOfVariants)
                .build();
        cache.put(targetId, result);

        return withDescription(result, memberDescription(documentMember));
    }

    private SerializableShape createJsonUnionSchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var targetId = target.id();

        var cached = cache.get(targetId);
        if (cached != null) {
            return withDescription(cached, memberDescription(member));
        }

        if (!visited.add(targetId)) {
            return JsonObjectSchema.builder().build();
        }

        var variants = new ArrayList<Document>();

        for (var m : target.members()) {
            var memberName = m.memberName();
            var memberSchema = createMemberSchema(m, visited, cache);

            variants.add(createUnionVariant(memberName, memberSchema));
        }

        visited.remove(targetId);

        var result = JsonOneOfSchema.builder()
                .oneOf(variants)
                .build();
        cache.put(targetId, result);

        return withDescription(result, memberDescription(member));
    }

    private static Document createUnionVariant(String memberName, SerializableShape memberSchema) {
        var wrapperSchema = JsonObjectSchema.builder()
                .properties(Map.of(memberName, Document.of(memberSchema)))
                .required(List.of(memberName))
                .additionalProperties(Document.of(false))
                .build();
        return Document.of(wrapperSchema);
    }

    private SerializableShape createMemberSchema(
            Schema member,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        return switch (member.type()) {
            case LIST, SET -> createJsonArraySchema(member, member.memberTarget(), visited, cache);
            case MAP -> createJsonMapSchema(member, member.memberTarget(), visited, cache);
            case STRUCTURE -> createJsonObjectSchema(member, member.memberTarget(), visited, cache);
            case UNION -> createJsonUnionSchema(member, member.memberTarget(), visited, cache);
            case DOCUMENT -> createJsonDocumentSchema(member, visited, cache);
            default -> createJsonPrimitiveSchema(member);
        };
    }

    private JsonObjectSchema createJsonMapSchema(
            Schema member,
            Schema target,
            Set<ShapeId> visited,
            Map<ShapeId, SerializableShape> cache
    ) {
        var mapValueMember = target.mapValueMember();
        var valueSchema = createMemberSchema(mapValueMember, visited, cache);

        // For sparse maps, allow null values using anyOf
        Document additionalPropertiesSchema;
        if (target.hasTrait(TraitKey.SPARSE_TRAIT)) {
            var nullSchema = Map.of("type", Document.of("null"));
            additionalPropertiesSchema = Document.of(Map.of(
                    "anyOf",
                    Document.of(List.of(Document.of(valueSchema), Document.of(nullSchema)))));
        } else {
            additionalPropertiesSchema = Document.of(valueSchema);
        }

        return JsonObjectSchema.builder()
                .description(memberDescription(member))
                .additionalProperties(additionalPropertiesSchema)
                .build();
    }

    private static String memberDescription(Schema schema) {
        String description = null;
        // Use getDirectTrait for members to avoid inheriting the target's documentation trait
        // (getTrait on a member merges member + target traits, which would cause doubling)
        var trait = schema.isMember()
                ? schema.getDirectTrait(TraitKey.DOCUMENTATION_TRAIT)
                : schema.getTrait(TraitKey.DOCUMENTATION_TRAIT);
        if (trait != null) {
            description = trait.getValue();
        }
        if (schema.isMember()) {
            var memberDescription = memberDescription(schema.memberTarget());
            if (description != null && memberDescription != null) {
                description = appendSentences(description, memberDescription);
            } else if (memberDescription != null) {
                description = memberDescription;
            }
        }
        return description;
    }

    private static String createDescription(
            String serviceName,
            String operationName,
            Schema schema
    ) {
        var documentationTrait = schema.getTrait(TraitKey.DOCUMENTATION_TRAIT);
        if (documentationTrait != null) {
            return documentationTrait.getValue();
        } else {
            return "This tool invokes %s API of %s.".formatted(operationName, serviceName);
        }
    }

    private record Tool(
            ToolInfo toolInfo,
            String serverId,
            Operation operation,
            McpServerProxy proxy,
            boolean requiredAdapting) {

        Tool(ToolInfo toolInfo, String serverId, Operation operation) {
            this(toolInfo, serverId, operation, null, false);
        }

        Tool(ToolInfo toolInfo, String serverId, McpServerProxy proxy) {
            this(toolInfo, serverId, null, proxy, false);
        }
    }

    private record ToolResult(JsonRpcResponse response, RuntimeException error) {
        static ToolResult success(JsonRpcResponse response) {
            return new ToolResult(response, null);
        }

        static ToolResult failure(RuntimeException error) {
            return new ToolResult(null, error);
        }
    }

    private static String appendSentences(String first, String second) {
        first = first.trim();
        if (!first.endsWith(".")) {
            first = first + ". ";
        }
        return first + second;
    }

    private static SerializableShape withDescription(SerializableShape schema, String description) {
        if (description == null) {
            return schema;
        }
        if (schema instanceof JsonObjectSchema s) {
            return s.toBuilder().description(description).build();
        }
        if (schema instanceof JsonOneOfSchema s) {
            return s.toBuilder().description(description).build();
        }
        return schema;
    }

    private Document adaptDocument(Document doc, Schema schema) {
        if (doc == null) {
            return null;
        }
        var fromType = doc.type();
        var toType = schema.type();
        return switch (toType) {
            case BIG_DECIMAL -> switch (fromType) {
                case STRING -> Document.of(new BigDecimal(doc.asString()));
                case BIG_INTEGER -> doc;
                default -> badType(fromType, toType);
            };
            case BIG_INTEGER -> switch (fromType) {
                case STRING -> Document.of(new BigInteger(doc.asString()));
                case BIG_INTEGER -> doc;
                default -> badType(fromType, toType);
            };
            case BLOB -> switch (fromType) {
                case STRING -> Document.of(Base64.getDecoder().decode(doc.asString()));
                case BLOB -> doc;
                default -> badType(fromType, toType);
            };
            case TIMESTAMP -> adaptTimestamp(doc);
            case STRUCTURE -> {
                var convertedMembers = new HashMap<String, Document>();
                var members = schema.members();
                for (var member : members) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        convertedMembers.put(memberName, adaptDocument(memberDoc, member));
                    }
                }
                yield Document.of(convertedMembers);
            }
            case UNION -> {
                var convertedMembers = new HashMap<String, Document>();

                // Find which member is set and adapt it
                // Input is in wrapper format: {"circle": {...}}
                for (var member : schema.members()) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        convertedMembers.put(memberName, adaptDocument(memberDoc, member));
                        break;
                    }
                }
                yield Document.of(convertedMembers);
            }
            case LIST, SET -> {
                var listMember = schema.listMember();
                var convertedList = new ArrayList<Document>();
                for (var item : doc.asList()) {
                    convertedList.add(adaptDocument(item, listMember));
                }
                yield Document.of(convertedList);
            }
            case MAP -> {
                var mapValue = schema.mapValueMember();
                var convertedMap = new HashMap<String, Document>();
                for (var entry : doc.asStringMap().entrySet()) {
                    convertedMap.put(entry.getKey(), adaptDocument(entry.getValue(), mapValue));
                }
                yield Document.of(convertedMap);
            }
            case DOCUMENT -> adaptDocumentWithOneOf(doc, schema);
            default -> doc;
        };
    }

    private Document adaptDocumentWithOneOf(Document doc, Schema schema) {
        var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
        var oneOfTrait = targetSchema.getTrait(ONE_OF_TRAIT);

        if (oneOfTrait != null) {
            // MCP sends wrapper format: {"circle": {"radius": 5}}
            // Need to convert to discriminated format: {"__type": "smithy.example#Circle", "radius": 5}
            var discriminator = oneOfTrait.getDiscriminator();

            // Find which member is set in the wrapper
            for (var memberDef : oneOfTrait.getMembers()) {
                var memberName = memberDef.getName();
                var memberDoc = doc.getMember(memberName);
                if (memberDoc != null) {
                    // Build the flat object with discriminator
                    var flatMembers = new HashMap<String, Document>();
                    var memberId = memberDef.getTarget();
                    flatMembers.put(discriminator, Document.of(memberId.toString()));
                    // Copy all fields from the inner object
                    var memberSchema = schemaIndex.getSchema(memberId);
                    flatMembers.putAll(adaptDocument(memberDoc, memberSchema).asStringMap());
                    return Document.of(flatMembers);
                }
            }
            // Fallback - return as-is if can't determine type
        }
        return doc;
    }

    private static Document badType(ShapeType from, ShapeType to) {
        throw new RuntimeException("Cannot convert from " + from + " to " + to);
    }

    /**
     *  This is primarily for more robustness against AI hallucinations.
     */
    private static Document adaptTimestamp(Document doc) {
        // If already a timestamp, format as date-time string
        if (doc.isType(ShapeType.TIMESTAMP)) {
            return Document.of(DATE_TIME.writeString(doc.asTimestamp()));
        }
        // If input is a string, try DATE_TIME first, fallback to HTTP_DATE
        if (doc.isType(ShapeType.STRING)) {
            var str = doc.asString();
            try {
                return Document.of(DATE_TIME.readFromString(str, false));
            } catch (Exception e) {
                // Fallback to HTTP_DATE format
                return Document.of(HTTP_DATE.readFromString(str, false));
            }
        }
        // If input is a number, use epoch seconds
        return Document.of(EPOCH_SECONDS.readFromNumber(doc.asNumber()));
    }

    private Document adaptOutputDocument(Document doc, Schema schema) {
        if (doc == null) {
            return null;
        }
        var toType = schema.type();
        return switch (toType) {
            case BIG_DECIMAL -> Document.of(doc.asBigDecimal().toString());
            case BIG_INTEGER -> Document.of(doc.asBigInteger().toString());
            case BLOB -> Document.of(Base64.getEncoder().encodeToString(ByteBufferUtils.getBytes(doc.asBlob())));
            // Use adaptTimestamp() instead of asTimestamp() because oneOf union members are
            // deserialized as untyped Documents (no schema available). Timestamps in these
            // documents remain as strings or numbers rather than being converted to Timestamp Documents.
            case TIMESTAMP -> adaptTimestamp(doc);
            case STRUCTURE -> {
                var convertedMembers = new HashMap<String, Document>();
                for (var member : schema.members()) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        convertedMembers.put(memberName, adaptOutputDocument(memberDoc, member));
                    }
                }
                yield Document.of(convertedMembers);
            }
            case UNION -> {
                // Regular union - already in wrapper format: {"circle": {...}}
                for (var member : schema.members()) {
                    var memberName = member.memberName();
                    var memberDoc = doc.getMember(memberName);
                    if (memberDoc != null) {
                        var adaptedMemberDoc = adaptOutputDocument(memberDoc, member);
                        yield Document.of(Map.of(memberName, adaptedMemberDoc));
                    }
                }
                yield Document.of(Map.of());
            }
            case LIST, SET -> {
                var listMember = schema.listMember();
                var convertedList = new ArrayList<Document>();
                for (var item : doc.asList()) {
                    convertedList.add(adaptOutputDocument(item, listMember));
                }
                yield Document.of(convertedList);
            }
            case MAP -> {
                var mapValue = schema.mapValueMember();
                var convertedMap = new HashMap<String, Document>();
                for (var entry : doc.asStringMap().entrySet()) {
                    convertedMap.put(entry.getKey(), adaptOutputDocument(entry.getValue(), mapValue));
                }
                yield Document.of(convertedMap);
            }
            case DOCUMENT -> {
                var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
                var oneOfTrait = targetSchema.getTrait(ONE_OF_TRAIT);

                if (oneOfTrait != null) {
                    // External service returns: {"__type": "smithy.example#Circle", "radius": 5}
                    // Need to convert to MCP wrapper format: {"circle": {"radius": 5}}
                    var discriminator = oneOfTrait.getDiscriminator();
                    var discriminatorValue = doc.getMember(discriminator);

                    if (discriminatorValue != null) {
                        var shapeId = ShapeId.from(discriminatorValue.asString());
                        // Find the matching member definition
                        for (var memberDef : oneOfTrait.getMembers()) {
                            if (memberDef.getTarget().equals(shapeId)) {
                                var memberName = memberDef.getName();
                                var memberSchema = schemaIndex.getSchema(shapeId);
                                // Build the inner object without the discriminator field
                                var innerMembers = new HashMap<>(adaptOutputDocument(doc, memberSchema).asStringMap());
                                innerMembers.remove(discriminator);
                                // Return wrapper format
                                yield Document.of(Map.of(memberName, Document.of(innerMembers)));
                            }
                        }
                    }
                }
                yield doc;
            }
            default -> doc;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, Service> services = new HashMap<>();
        private List<McpServerProxy> proxyList = new ArrayList<>();
        private McpServerInterceptor interceptor = McpServerInterceptor.NOOP;
        private String name = "mcp-server";
        private String version = "1.0.0";
        private ToolFilter toolFilter = (serverId, toolName) -> true;
        private McpMetricsObserver metricsObserver;

        public Builder services(Map<String, Service> services) {
            this.services = services;
            return this;
        }

        public Builder proxyList(List<McpServerProxy> proxyList) {
            this.proxyList = proxyList;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder toolFilter(ToolFilter toolFilter) {
            this.toolFilter = toolFilter;
            return this;
        }

        public Builder metricsObserver(McpMetricsObserver metricsObserver) {
            this.metricsObserver = metricsObserver;
            return this;
        }

        /**
         * Sets the server interceptor. Use {@link McpServerInterceptor#chain(List)} to compose
         * multiple interceptors into one.
         *
         * @see McpServerInterceptor for hook descriptions and the execution lifecycle
         */
        public Builder interceptor(McpServerInterceptor interceptor) {
            this.interceptor = Objects.requireNonNull(interceptor, "interceptor");
            return this;
        }

        public McpService build() {
            return new McpService(services, proxyList, name, version, toolFilter, metricsObserver, interceptor);
        }
    }
}
