/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Hook data available during tool call processing. Extends {@link McpExecutionHook} with
 * tool-specific information. Passed to tool-scoped hooks in {@link McpServerInterceptor}.
 */
@SmithyUnstableApi
public class McpToolCallHook extends McpExecutionHook {

    private final String toolName;
    private final String serverId;
    private final boolean isProxy;

    McpToolCallHook(
            JsonRpcRequest request,
            ProtocolVersion protocolVersion,
            Context context,
            String toolName,
            String serverId,
            boolean isProxy
    ) {
        super(request, protocolVersion, context);
        this.toolName = toolName;
        this.serverId = serverId;
        this.isProxy = isProxy;
    }

    /**
     * The name of the tool being invoked.
     */
    public String toolName() {
        return toolName;
    }

    /**
     * The server ID that owns this tool.
     */
    public String serverId() {
        return serverId;
    }

    /**
     * Whether this tool is dispatched to a remote proxy rather than handled locally.
     */
    public boolean isProxy() {
        return isProxy;
    }

    /**
     * Returns a new hook with the given request, or the same hook if unchanged.
     */
    @Override
    public McpToolCallHook withRequest(JsonRpcRequest request) {
        return this.request() == request
                ? this
                : new McpToolCallHook(request, protocolVersion(), context(), toolName, serverId, isProxy);
    }
}
