/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

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
     * Called when tool/call request is received.
     */
    void onToolCall(
            String method,
            String toolName
    );
}
