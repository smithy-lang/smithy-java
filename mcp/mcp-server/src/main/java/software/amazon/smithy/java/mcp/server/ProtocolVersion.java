/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A protocol version claimed by an MCP peer.
 *
 * <p>Built-in versions are represented by {@link KnownProtocolVersion}; other wire
 * values are preserved in {@link UnknownProtocolVersion} and may resolve to a
 * registered {@link ExtensionMcpProtocol}.
 */
@SmithyUnstableApi
public sealed interface ProtocolVersion permits KnownProtocolVersion, UnknownProtocolVersion {

    /**
     * Returns the wire identifier of this version.
     */
    String identifier();

    /**
     * Parses a wire protocol version.
     */
    static ProtocolVersion parse(String identifier) {
        if (identifier == null) {
            return defaultVersion();
        }
        var known = KnownProtocolVersion.fromIdentifier(identifier);
        return known == null ? new UnknownProtocolVersion(identifier) : known;
    }

    /**
     * The compatibility version used when a pre-2025-06-18 HTTP client omits the
     * protocol-version header.
     */
    static KnownProtocolVersion defaultVersion() {
        return KnownProtocolVersion.V2025_03_26;
    }
}
