/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * The operation reported to client interceptors for each JSON-RPC exchange made by {@link HttpMcpClient}.
 */
final class McpHttpOperation implements ApiOperation<JsonRpcRequest, JsonRpcResponse> {

    static final McpHttpOperation INSTANCE = new McpHttpOperation();

    private static final Schema SCHEMA = Schema.createOperation(ShapeId.from("smithy.mcp#JsonRpc"));
    private static final ApiService SERVICE = () -> Schema.createService(ShapeId.from("smithy.mcp#McpHttp"));

    private McpHttpOperation() {}

    @Override
    public ShapeBuilder<JsonRpcRequest> inputBuilder() {
        return JsonRpcRequest.builder();
    }

    @Override
    public ShapeBuilder<JsonRpcResponse> outputBuilder() {
        return JsonRpcResponse.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return JsonRpcRequest.$SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return JsonRpcResponse.$SCHEMA;
    }

    @Override
    public TypeRegistry errorRegistry() {
        return TypeRegistry.empty();
    }

    @Override
    public List<ShapeId> effectiveAuthSchemes() {
        return List.of();
    }

    @Override
    public List<Schema> errorSchemas() {
        return List.of();
    }

    @Override
    public ApiService service() {
        return SERVICE;
    }
}
