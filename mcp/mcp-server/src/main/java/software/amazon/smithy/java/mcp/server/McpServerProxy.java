/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static software.amazon.smithy.java.mcp.model.ListPromptsResult.builder;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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

public abstract class McpServerProxy {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpServerProxy.class);
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    private final AtomicReference<Consumer<JsonRpcResponse>> notificationConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<JsonRpcRequest>> requestNotificationConsumer = new AtomicReference<>();
    private final AtomicReference<ProtocolVersion> protocolVersion =
            new AtomicReference<>(ProtocolVersion.defaultVersion());

    public List<ToolInfo> listTools() {
        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("tools/list")
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build();

        return rpc(request).thenApply(response -> {
            if (response.getError() != null) {
                throw new RuntimeException("Error listing tools: " + response.getError().getMessage());
            }
            return response.getResult()
                    .asShape(ListToolsResult.builder())
                    .getTools()
                    .stream()
                    .toList();
        }).join();
    }

    public List<PromptInfo> listPrompts() {
        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("prompts/list")
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build();
        return rpc(request).thenApply(response -> {
            if (response.getError() != null) {
                throw new RuntimeException("Error listing prompts: " + response.getError().getMessage());
            }
            return response.getResult()
                    .asShape(builder())
                    .getPrompts()
                    .stream()
                    .toList();
        }).join();
    }

    public void initialize(
            Consumer<JsonRpcResponse> notificationConsumer,
            Consumer<JsonRpcRequest> requestNotificationConsumer,
            JsonRpcRequest initializeRequest,
            ProtocolVersion protocolVersion
    ) {

        var result = Objects.requireNonNull(rpc(initializeRequest).join());
        if (result.getError() != null) {
            throw new RuntimeException("Error during initialization: " + result.getError().getMessage());
        }
        this.notificationConsumer.set(notificationConsumer);
        this.requestNotificationConsumer.set(requestNotificationConsumer);
        this.protocolVersion.set(protocolVersion);
    }

    protected final ProtocolVersion getProtocolVersion() {
        return protocolVersion.get();
    }

    abstract CompletableFuture<JsonRpcResponse> rpc(JsonRpcRequest request);

    abstract void start();

    abstract CompletableFuture<Void> shutdown();

    protected <T extends SerializableStruct> CompletableFuture<T> rpc(String method, ShapeBuilder<T> builder) {
        JsonRpcRequest request = JsonRpcRequest.builder()
                .method(method)
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build();

        return rpc(request).thenApply(response -> {
            if (response.getError() != null) {
                throw new RuntimeException("Error in RPC call: " + response.getError().getMessage());
            }
            return response.getResult().asShape(builder);
        });
    }

    // Generate a unique request ID for each RPC call
    protected Document generateRequestId() {
        return Document.of(ID_GENERATOR.incrementAndGet());
    }

    protected void notify(JsonRpcResponse response) {
        var nc = notificationConsumer.get();
        if (nc != null) {
            nc.accept(response);
        }
    }

    /**
     * Forwards a notification request by converting it to a response format.
     * Notifications have a method field but no id.
     */
    protected void notifyRequest(JsonRpcRequest notification) {
        var rnc = requestNotificationConsumer.get();
        if (rnc != null) {
            LOG.debug("Forwarding notification to consumer: method={}", notification.getMethod());
            rnc.accept(notification);
        } else {
            LOG.warn("No request notification consumer set, dropping notification: method={}",
                    notification.getMethod());
        }
    }

    public abstract String name();
}
