/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Typed interceptor for MCP execution.
 *
 * <p>Calls and outcomes are immutable; modifying hooks return replacement values.
 */
@SmithyUnstableApi
public interface McpInterceptor {
    McpInterceptor NOOP = new McpInterceptor() {};

    static McpInterceptor chain(McpInterceptor... interceptors) {
        return chain(List.of(interceptors));
    }

    static McpInterceptor chain(List<McpInterceptor> interceptors) {
        return switch (interceptors.size()) {
            case 0 -> NOOP;
            case 1 -> interceptors.getFirst();
            default -> new McpInterceptorChain(List.copyOf(interceptors));
        };
    }

    default void readBeforeExecution(McpExecutionContext context) {}

    default McpCall modifyBeforeExecution(McpExecutionContext context) {
        return context.call();
    }

    default void readAfterExecution(
            McpExecutionContext context,
            McpOutcome outcome,
            RuntimeException error
    ) {}

    default McpOutcome modifyAfterExecution(
            McpExecutionContext context,
            McpOutcome outcome,
            RuntimeException error
    ) {
        if (error != null) {
            throw error;
        }
        return outcome;
    }

    default void readBeforeToolCall(McpToolExecutionContext context) {}

    default McpCall.CallTool modifyBeforeToolCall(McpToolExecutionContext context) {
        return context.call();
    }

    default void readAfterToolCall(
            McpToolExecutionContext context,
            McpOutcome outcome,
            RuntimeException error
    ) {}

    default McpOutcome modifyAfterToolCall(
            McpToolExecutionContext context,
            McpOutcome outcome,
            RuntimeException error
    ) {
        if (error != null) {
            throw error;
        }
        return outcome;
    }
}
