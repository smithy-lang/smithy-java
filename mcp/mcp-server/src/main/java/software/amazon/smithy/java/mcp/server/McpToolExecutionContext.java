/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Typed data exposed around tool execution.
 */
@SmithyUnstableApi
public record McpToolExecutionContext(
        McpCall.CallTool call,
        McpRequestContext requestContext,
        String serverId,
        boolean remote) {
    public McpToolExecutionContext {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(requestContext, "requestContext");
        Objects.requireNonNull(serverId, "serverId");
    }

    McpToolExecutionContext withCall(McpCall.CallTool call) {
        return this.call == call ? this : new McpToolExecutionContext(call, requestContext, serverId, remote);
    }
}
