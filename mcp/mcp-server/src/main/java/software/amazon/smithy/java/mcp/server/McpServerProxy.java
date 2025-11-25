/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.ToolInfo;

public abstract class McpServerProxy {

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    protected Consumer<JsonRpcResponse> notificationConsumer;
    protected String protocolVersion;

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

    public void initialize(
            Consumer<JsonRpcResponse> notificationConsumer,
            JsonRpcRequest initializeRequest,
            String protocolVersion
    ) {

        var result = Objects.requireNonNull(rpc(initializeRequest).join());
        if (result.getError() != null) {
            throw new RuntimeException("Error during initialization: " + result.getError().getMessage());
        }
        this.notificationConsumer = notificationConsumer;
        this.protocolVersion = protocolVersion;
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
        notificationConsumer.accept(response);
    }

    public abstract String name();
}
