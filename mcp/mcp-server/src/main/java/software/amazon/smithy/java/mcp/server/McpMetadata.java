/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Metadata shared by typed MCP calls.
 */
@SmithyUnstableApi
public record McpMetadata(
        ProtocolVersion protocolVersion,
        Document clientInfo,
        Document clientCapabilities,
        Map<String, Document> extensions) {
    public static final McpMetadata EMPTY = new McpMetadata(null, null, null, Map.of());

    public McpMetadata {
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }
}
