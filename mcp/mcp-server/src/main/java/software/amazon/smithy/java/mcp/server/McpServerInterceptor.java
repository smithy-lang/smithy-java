/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Interceptor for MCP server request processing. Interceptors inject code into the
 * {@link McpService} request execution pipeline via hooks at specific stages.
 *
 * <p>Hooks are either "read" hooks (observe in-flight data) or "modify" hooks (transform
 * in-flight data). All hooks have default no-op implementations; override only the hooks
 * you need.
 *
 * <h2>Execution lifecycle</h2>
 *
 * <p>For every request:
 * <ol>
 *   <li>{@link #readBeforeExecution} — observe the incoming request</li>
 *   <li>{@link #modifyBeforeExecution} — optionally transform the request</li>
 *   <li>For {@code tools/call} requests only:
 *     <ol>
 *       <li>{@link #readBeforeToolCall} — observe before tool dispatch</li>
 *       <li>{@link #modifyBeforeToolCall} — optionally transform the request</li>
 *       <li>Tool dispatch (local or proxy)</li>
 *       <li>{@link #readAfterToolCall} — observe the tool result</li>
 *       <li>{@link #modifyAfterToolCall} — optionally transform the response</li>
 *     </ol>
 *   </li>
 *   <li>{@link #readAfterExecution} — observe the final result (ALWAYS fires)</li>
 *   <li>{@link #modifyAfterExecution} — optionally transform the final response</li>
 * </ol>
 *
 * <h2>Error handling</h2>
 *
 * <p>Any hook may throw a {@link RuntimeException}. When a hook throws, remaining hooks
 * in that stage are skipped, and execution jumps to the after-execution hooks with the
 * error. The {@code readAfterExecution} and {@code modifyAfterExecution} hooks ALWAYS fire,
 * ensuring cleanup and telemetry logic runs regardless of errors.
 *
 * <h2>Async tool calls</h2>
 *
 * <p>For proxy tool calls, the after-tool-call and after-execution hooks fire on the
 * thread that receives the proxy response, not the original request thread. Hook
 * implementations must be thread-safe.
 *
 * <h2>Example: telemetry</h2>
 * <pre>{@code
 * public class TelemetryInterceptor implements McpServerInterceptor {
 *     private static final Context.Key<Long> START = Context.key("start");
 *
 *     @Override
 *     public void readBeforeExecution(McpExecutionHook hook) {
 *         hook.context().put(START, System.nanoTime());
 *     }
 *
 *     @Override
 *     public void readAfterExecution(McpExecutionHook hook,
 *             JsonRpcResponse response, RuntimeException error) {
 *         long duration = System.nanoTime() - hook.context().get(START);
 *         emitMetrics(hook.request().getMethod(), duration, error == null);
 *     }
 * }
 * }</pre>
 *
 * <h2>Example: access control</h2>
 * <pre>{@code
 * public class AccessControlInterceptor implements McpServerInterceptor {
 *     @Override
 *     public void readBeforeToolCall(McpToolCallHook hook) {
 *         if (isBlocked(hook.toolName(), hook.serverId())) {
 *             throw new RuntimeException("Access denied: " + hook.toolName());
 *         }
 *     }
 * }
 * }</pre>
 */
@SmithyUnstableApi
public interface McpServerInterceptor {

    /**
     * An interceptor that does nothing.
     */
    McpServerInterceptor NOOP = new McpServerInterceptor() {};

    /**
     * Combines multiple interceptors into a single interceptor that invokes each one
     * in order. Hooks are called sequentially on each interceptor in list order.
     *
     * @param interceptors The interceptors to compose.
     * @return A single interceptor that delegates to all provided interceptors.
     */
    static McpServerInterceptor chain(List<McpServerInterceptor> interceptors) {
        return switch (interceptors.size()) {
            case 0 -> NOOP;
            case 1 -> interceptors.get(0);
            default -> new McpServerInterceptorChain(List.copyOf(interceptors));
        };
    }

    /**
     * Combines multiple interceptors into a single interceptor that invokes each one
     * in order. Convenience overload of {@link #chain(List)}.
     *
     * @param interceptors The interceptors to compose.
     * @return A single interceptor that delegates to all provided interceptors.
     */
    static McpServerInterceptor chain(McpServerInterceptor... interceptors) {
        return chain(List.of(interceptors));
    }

    // --- Execution-level hooks (fire for all requests) ---

    /**
     * Called when a request is received, before any dispatch logic.
     *
     * @param hook Execution hook data containing the request, protocol version, and context.
     */
    default void readBeforeExecution(McpExecutionHook hook) {}

    /**
     * Called before dispatch. Can return a modified request.
     *
     * @param hook Execution hook data.
     * @return The request to dispatch, or {@code hook.request()} to pass through unmodified.
     */
    default JsonRpcRequest modifyBeforeExecution(McpExecutionHook hook) {
        return hook.request();
    }

    /**
     * Called when execution completes. ALWAYS fires, even if an earlier hook threw.
     *
     * @param hook Execution hook data.
     * @param response The response, or {@code null} for notifications and async proxy calls
     *        still in flight.
     * @param error The error if one occurred, or {@code null} on success.
     */
    default void readAfterExecution(McpExecutionHook hook, JsonRpcResponse response, RuntimeException error) {}

    /**
     * Called when execution completes. Can modify the response or handle errors.
     * ALWAYS fires, even if an earlier hook threw.
     *
     * @param hook Execution hook data.
     * @param response The response, or {@code null} for notifications.
     * @param error The error if one occurred, or {@code null} on success.
     * @return The final response.
     * @throws RuntimeException to propagate or replace the error.
     */
    default JsonRpcResponse modifyAfterExecution(
            McpExecutionHook hook,
            JsonRpcResponse response,
            RuntimeException error
    ) {
        if (error != null) {
            throw error;
        }
        return response;
    }

    // --- Tool-level hooks (fire only for tools/call) ---

    /**
     * Called before a tool is invoked.
     *
     * @param hook Tool call hook data containing tool name, server ID, and proxy status.
     */
    default void readBeforeToolCall(McpToolCallHook hook) {}

    /**
     * Called before a tool is invoked. Can return a modified request.
     *
     * @param hook Tool call hook data.
     * @return The request to use for tool invocation, or {@code hook.request()} to pass
     *         through unmodified.
     */
    default JsonRpcRequest modifyBeforeToolCall(McpToolCallHook hook) {
        return hook.request();
    }

    /**
     * Called after a tool completes. For proxy tools, this fires on the callback thread.
     *
     * @param hook Tool call hook data.
     * @param response The tool call response.
     * @param error The error if one occurred, or {@code null} on success.
     */
    default void readAfterToolCall(McpToolCallHook hook, JsonRpcResponse response, RuntimeException error) {}

    /**
     * Called after a tool completes. Can modify the response or handle errors.
     * For proxy tools, this fires on the callback thread.
     *
     * @param hook Tool call hook data.
     * @param response The tool call response.
     * @param error The error if one occurred, or {@code null} on success.
     * @return The response to return.
     * @throws RuntimeException to propagate or replace the error.
     */
    default JsonRpcResponse modifyAfterToolCall(
            McpToolCallHook hook,
            JsonRpcResponse response,
            RuntimeException error
    ) {
        if (error != null) {
            throw error;
        }
        return response;
    }
}
