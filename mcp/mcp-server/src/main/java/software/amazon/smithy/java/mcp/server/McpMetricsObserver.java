/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Observer for collecting metrics on MCP client requests.
 * Works directly with JSON-RPC structures to remain protocol-agnostic.
 */
@SmithyUnstableApi
public interface McpMetricsObserver {

    /**
     * Called when a client request is received for any method.
     */
    void onClientRequest(
            String method,
            String extractedProtocolVersion,
            boolean rootsListChanged,
            boolean sampling,
            boolean elicitation,
            String clientName,
            String clientTitle
    );
}
