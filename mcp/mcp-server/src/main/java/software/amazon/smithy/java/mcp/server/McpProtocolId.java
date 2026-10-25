/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Typed registry key for an MCP protocol version.
 */
@SmithyUnstableApi
public record McpProtocolId(String identifier) {
    public McpProtocolId {
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("MCP protocol identifier must not be blank");
        }
    }

    public static McpProtocolId of(String identifier) {
        return new McpProtocolId(identifier);
    }
}
