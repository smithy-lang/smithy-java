/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.time.Duration;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Observer for collecting metrics on MCP client requests.
 */
@SmithyUnstableApi
public interface McpMetricsObserver {

    /**
     * Called when initialize request is received.
     */
    void onInitialize(
            String method,
            String extractedProtocolVersion,
            boolean rootsListChanged,
            boolean sampling,
            boolean elicitation,
            String clientName,
            String clientTitle
    );

    /**
     * Called when tool/call request is received. Fires at the start of the call,
     * before execution begins. Paired with {@link #onToolCallComplete} which fires after.
     *
     * @param method   the JSON-RPC method name (e.g. {@code "tools/call"})
     * @param toolName the name of the tool being invoked, or {@code null} if not available
     */
    void onToolCall(
            String method,
            String toolName
    );

    /**
     * Called after a tool call completes, whether it succeeded or failed.
     * Every {@link #onToolCall} is guaranteed a matching {@code onToolCallComplete}.
     *
     * @param method   the JSON-RPC method name (e.g. {@code "tools/call"})
     * @param toolName the name of the tool that was invoked, or {@code null} if not available
     * @param serverId the MCP server that handled the call (e.g. {@code "aws-lambda-mcp"}),
     *                 or {@code null} if the tool was not found
     * @param latency  wall-clock time from request receipt to response
     * @param success  {@code true} if the tool call returned a successful result,
     *                 {@code false} if it returned an error or threw an exception
     * @param isProxy  {@code true} if the call was forwarded to a proxy (StdioProxy or HttpMcpProxy),
     *                 {@code false} if it was handled in-process by a local Smithy service
     */
    default void onToolCallComplete(
            String method,
            String toolName,
            String serverId,
            Duration latency,
            boolean success,
            boolean isProxy
    ) {}

    /**
     * Called when a tool call results in an error. Always preceded by
     * {@link #onToolCallComplete} with {@code success=false} for the same call.
     *
     * @param method       the JSON-RPC method name (e.g. {@code "tools/call"})
     * @param toolName     the name of the tool that failed, or {@code null} if not available
     * @param serverId     the MCP server that handled the call, or {@code null} if the tool was not found
     * @param errorMessage a description of the error
     */
    default void onToolCallError(
            String method,
            String toolName,
            String serverId,
            String errorMessage
    ) {}

    /**
     * Called when a {@code tools/list} request is received.
     *
     * @param method    the JSON-RPC method name (e.g. {@code "tools/list"})
     * @param toolCount the number of tools returned after filtering
     */
    default void onToolsList(
            String method,
            int toolCount
    ) {}
}
