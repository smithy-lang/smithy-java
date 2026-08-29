/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static software.amazon.smithy.java.mcp.model.ListPromptsResult.builder;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Blocking client for a remote MCP server.
 *
 * <p>Implementations may use asynchronous I/O internally, but callers observe a single
 * blocking exchange operation suitable for execution on virtual threads.
 */
@SmithyUnstableApi
public abstract class McpRemoteClient implements AutoCloseable {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpRemoteClient.class);
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    private final AtomicReference<Consumer<JsonRpcResponse>> responseNotificationConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<JsonRpcRequest>> requestNotificationConsumer = new AtomicReference<>();
    private final AtomicReference<McpProtocol> protocol =
            new AtomicReference<>(BuiltInProtocols.protocol(ProtocolVersion.defaultVersion()));

    public List<ToolInfo> listTools() {
        var response = exchange(JsonRpcRequest.builder()
                .method(McpMethod.Standard.TOOLS_LIST.wireName())
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build());
        requireSuccess(response, "listing tools");
        return response.getResult().asShape(ListToolsResult.builder()).getTools().stream().toList();
    }

    public List<PromptInfo> listPrompts() {
        var response = exchange(JsonRpcRequest.builder()
                .method(McpMethod.Standard.PROMPTS_LIST.wireName())
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build());
        requireSuccess(response, "listing prompts");
        return response.getResult().asShape(builder()).getPrompts().stream().toList();
    }

    final void initialize(
            Consumer<JsonRpcResponse> responseNotificationConsumer,
            Consumer<JsonRpcRequest> requestNotificationConsumer,
            JsonRpcRequest initializeRequest,
            McpProtocol protocol
    ) {
        var result = Objects.requireNonNull(exchange(initializeRequest), "initialize response");
        requireSuccess(result, "initialization");

        exchange(JsonRpcRequest.builder()
                .method(McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName())
                .jsonrpc("2.0")
                .build());

        this.responseNotificationConsumer.set(responseNotificationConsumer);
        this.requestNotificationConsumer.set(requestNotificationConsumer);
        this.protocol.set(protocol);
    }

    protected final ProtocolVersion protocolVersion() {
        return protocol.get().protocolVersion();
    }

    protected final McpProtocol protocol() {
        return protocol.get();
    }

    /**
     * Performs one blocking JSON-RPC exchange. Notifications return {@code null}.
     */
    protected abstract JsonRpcResponse exchange(JsonRpcRequest request);

    /**
     * Starts resources owned by this client.
     */
    public abstract void start();

    /**
     * Stops resources owned by this client.
     */
    @Override
    public abstract void close();

    protected final <T extends SerializableStruct> T exchange(String method, ShapeBuilder<T> builder) {
        var response = exchange(JsonRpcRequest.builder()
                .method(method)
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build());
        requireSuccess(response, method);
        return response.getResult().asShape(builder);
    }

    protected final Document generateRequestId() {
        return Document.of(ID_GENERATOR.incrementAndGet());
    }

    protected final void notify(JsonRpcResponse response) {
        var consumer = responseNotificationConsumer.get();
        if (consumer != null) {
            consumer.accept(response);
        }
    }

    protected final void notify(JsonRpcRequest notification) {
        var consumer = requestNotificationConsumer.get();
        if (consumer != null) {
            LOG.debug("Forwarding notification to consumer: method={}", notification.getMethod());
            consumer.accept(notification);
        } else {
            LOG.warn("No request notification consumer set, dropping notification: method={}",
                    notification.getMethod());
        }
    }

    protected static boolean isNotification(Document doc) {
        try {
            return (doc.isType(ShapeType.STRUCTURE) || doc.isType(ShapeType.MAP))
                    && doc.getMember("id") == null
                    && doc.getMember("method") != null;
        } catch (RuntimeException e) {
            LOG.warn("Failed to determine whether MCP document is a notification", e);
            return false;
        }
    }

    private static void requireSuccess(JsonRpcResponse response, String action) {
        Objects.requireNonNull(response, action + " response");
        if (response.getError() != null) {
            throw new McpRemoteException("Remote MCP error during " + action + ": "
                    + response.getError().getMessage());
        }
    }

    public abstract String name();
}
