/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Composes multiple {@link McpServerInterceptor} instances into a single interceptor
 * that delegates to each one in order.
 */
@SmithyUnstableApi
final class McpServerInterceptorChain implements McpServerInterceptor {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(McpServerInterceptorChain.class);
    private final McpServerInterceptor[] interceptors;

    McpServerInterceptorChain(List<McpServerInterceptor> interceptors) {
        this.interceptors = interceptors.toArray(McpServerInterceptor[]::new);
    }

    @Override
    public void readBeforeExecution(McpExecutionHook hook) {
        RuntimeException error = null;
        for (var interceptor : interceptors) {
            try {
                interceptor.readBeforeExecution(hook);
            } catch (RuntimeException e) {
                error = swapError("readBeforeExecution", error, e);
            }
        }
        if (error != null) {
            throw error;
        }
    }

    @Override
    public JsonRpcRequest modifyBeforeExecution(McpExecutionHook hook) {
        var current = hook;
        for (var interceptor : interceptors) {
            var req = interceptor.modifyBeforeExecution(current);
            current = current.withRequest(req);
        }
        return current.request();
    }

    @Override
    public void readAfterExecution(McpExecutionHook hook, JsonRpcResponse response, RuntimeException error) {
        for (var interceptor : interceptors) {
            try {
                interceptor.readAfterExecution(hook, response, error);
            } catch (RuntimeException e) {
                error = swapError("readAfterExecution", error, e);
            }
        }
        // Always throw the error even if it's the original error.
        if (error != null) {
            throw error;
        }
    }

    @Override
    public JsonRpcResponse modifyAfterExecution(
            McpExecutionHook hook,
            JsonRpcResponse response,
            RuntimeException error
    ) {
        for (var interceptor : interceptors) {
            response = interceptor.modifyAfterExecution(hook, response, error);
            error = null;
        }
        return response;
    }

    @Override
    public void readBeforeToolCall(McpToolCallHook hook) {
        RuntimeException error = null;
        for (var interceptor : interceptors) {
            try {
                interceptor.readBeforeToolCall(hook);
            } catch (RuntimeException e) {
                error = swapError("readBeforeToolCall", error, e);
            }
        }
        if (error != null) {
            throw error;
        }
    }

    @Override
    public JsonRpcRequest modifyBeforeToolCall(McpToolCallHook hook) {
        var current = hook;
        for (var interceptor : interceptors) {
            var req = interceptor.modifyBeforeToolCall(current);
            current = current.withRequest(req);
        }
        return current.request();
    }

    @Override
    public void readAfterToolCall(McpToolCallHook hook, JsonRpcResponse response, RuntimeException error) {
        for (var interceptor : interceptors) {
            try {
                interceptor.readAfterToolCall(hook, response, error);
            } catch (RuntimeException e) {
                error = swapError("readAfterToolCall", error, e);
            }
        }
        // Always throw the error even if it's the original error.
        if (error != null) {
            throw error;
        }
    }

    @Override
    public JsonRpcResponse modifyAfterToolCall(McpToolCallHook hook, JsonRpcResponse response, RuntimeException error) {
        for (var interceptor : interceptors) {
            response = interceptor.modifyAfterToolCall(hook, response, error);
            error = null;
        }
        return response;
    }

    private static RuntimeException swapError(String hook, RuntimeException oldE, RuntimeException newE) {
        if (oldE != null && oldE != newE) {
            LOGGER.trace("Replacing error after {}: {}", hook, newE.getClass().getName(), newE.getMessage());
        }
        return newE;
    }
}
