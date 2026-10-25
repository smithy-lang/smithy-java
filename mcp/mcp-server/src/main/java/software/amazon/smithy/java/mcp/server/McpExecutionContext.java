/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Typed data exposed to execution interceptors.
 */
@SmithyUnstableApi
public record McpExecutionContext(McpCall call, McpRequestContext requestContext) {
    public McpExecutionContext {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(requestContext, "requestContext");
    }

    McpExecutionContext withCall(McpCall call) {
        return this.call == call ? this : new McpExecutionContext(call, requestContext);
    }
}
