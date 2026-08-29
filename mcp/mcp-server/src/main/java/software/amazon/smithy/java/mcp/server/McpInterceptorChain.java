/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;

final class McpInterceptorChain implements McpInterceptor {
    private final List<McpInterceptor> interceptors;

    McpInterceptorChain(List<McpInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Override
    public void readBeforeExecution(McpExecutionContext context) {
        interceptors.forEach(interceptor -> interceptor.readBeforeExecution(context));
    }

    @Override
    public McpCall modifyBeforeExecution(McpExecutionContext context) {
        var call = context.call();
        for (var interceptor : interceptors) {
            call = interceptor.modifyBeforeExecution(context.withCall(call));
        }
        return call;
    }

    @Override
    public void readAfterExecution(McpExecutionContext context, McpOutcome outcome, RuntimeException error) {
        interceptors.forEach(interceptor -> interceptor.readAfterExecution(context, outcome, error));
    }

    @Override
    public McpOutcome modifyAfterExecution(
            McpExecutionContext context,
            McpOutcome outcome,
            RuntimeException error
    ) {
        var current = outcome;
        var currentError = error;
        for (var interceptor : interceptors) {
            current = interceptor.modifyAfterExecution(context, current, currentError);
            currentError = null;
        }
        return current;
    }

    @Override
    public void readBeforeToolCall(McpToolExecutionContext context) {
        interceptors.forEach(interceptor -> interceptor.readBeforeToolCall(context));
    }

    @Override
    public McpCall.CallTool modifyBeforeToolCall(McpToolExecutionContext context) {
        var call = context.call();
        for (var interceptor : interceptors) {
            call = interceptor.modifyBeforeToolCall(context.withCall(call));
        }
        return call;
    }

    @Override
    public void readAfterToolCall(
            McpToolExecutionContext context,
            McpOutcome outcome,
            RuntimeException error
    ) {
        interceptors.forEach(interceptor -> interceptor.readAfterToolCall(context, outcome, error));
    }

    @Override
    public McpOutcome modifyAfterToolCall(
            McpToolExecutionContext context,
            McpOutcome outcome,
            RuntimeException error
    ) {
        var current = outcome;
        var currentError = error;
        for (var interceptor : interceptors) {
            current = interceptor.modifyAfterToolCall(context, current, currentError);
            currentError = null;
        }
        return current;
    }
}
