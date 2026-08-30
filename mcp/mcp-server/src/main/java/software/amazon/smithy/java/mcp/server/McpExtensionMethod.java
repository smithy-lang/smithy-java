/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Defines a typed custom MCP method without opening the built-in call hierarchy.
 */
@SmithyUnstableApi
public interface McpExtensionMethod<P> {
    String method();

    P decode(Document params);

    /**
     * Encodes typed parameters when an extension call is sent to another peer.
     *
     * <p>Inbound-only extensions do not need to override this method.
     */
    default Document encode(P params) {
        throw new UnsupportedOperationException("Extension does not support outbound encoding: " + method());
    }

    McpOutcome execute(McpCall.ExtensionCall<P> call, McpRequestContext context);
}
