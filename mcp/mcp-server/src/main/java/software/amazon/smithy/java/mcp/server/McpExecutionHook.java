/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Hook data available at the execution level. Passed to execution-scoped hooks in
 * {@link McpServerInterceptor}.
 *
 * <p>The {@link #context()} provides a per-request key-value store for passing state
 * between hooks. For example, a telemetry interceptor can stash a start timestamp in
 * {@code readBeforeExecution} and retrieve it in {@code readAfterExecution}.
 */
@SmithyUnstableApi
public class McpExecutionHook {

    private final JsonRpcRequest request;
    private final ProtocolVersion protocolVersion;
    private final Context context;

    McpExecutionHook(JsonRpcRequest request, ProtocolVersion protocolVersion, Context context) {
        this.request = request;
        this.protocolVersion = protocolVersion;
        this.context = context;
    }

    /**
     * The JSON-RPC request being handled.
     */
    public JsonRpcRequest request() {
        return request;
    }

    /**
     * Returns a new hook with the given request, or the same hook if unchanged.
     */
    public McpExecutionHook withRequest(JsonRpcRequest request) {
        return this.request == request ? this : new McpExecutionHook(request, protocolVersion, context);
    }

    /**
     * The MCP protocol version for this request.
     */
    public ProtocolVersion protocolVersion() {
        return protocolVersion;
    }

    /**
     * Per-request context for passing state between hooks.
     */
    public Context context() {
        return context;
    }
}
