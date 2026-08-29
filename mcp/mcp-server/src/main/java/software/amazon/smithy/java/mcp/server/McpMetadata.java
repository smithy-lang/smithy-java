/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
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

    static McpMetadata forProtocol(McpProtocol protocol) {
        return protocol.usesStatelessMetadata()
                ? new McpMetadata(
                        protocol.protocolVersion(),
                        null,
                        Document.of(Map.of()),
                        Map.of())
                : EMPTY;
    }

    Document applyTo(Document params) {
        if (this == EMPTY
                || (protocolVersion == null
                        && clientInfo == null
                        && clientCapabilities == null
                        && extensions.isEmpty())) {
            return params;
        }

        var values = params == null
                ? new HashMap<String, Document>()
                : new HashMap<>(params.asStringMap());
        var meta = new HashMap<>(extensions);
        if (protocolVersion != null) {
            meta.put(McpWireNames.PROTOCOL_VERSION, Document.of(protocolVersion.identifier()));
        }
        if (clientInfo != null) {
            meta.put(McpWireNames.CLIENT_INFO, clientInfo);
        }
        if (clientCapabilities != null) {
            meta.put(McpWireNames.CLIENT_CAPABILITIES, clientCapabilities);
        }
        if (!meta.isEmpty()) {
            values.put("_meta", Document.of(meta));
        }
        return Document.of(values);
    }
}
