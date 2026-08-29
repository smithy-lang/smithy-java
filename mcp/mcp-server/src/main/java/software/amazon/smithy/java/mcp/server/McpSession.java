/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.concurrent.locks.ReentrantLock;

final class McpSession {
    private final ReentrantLock negotiationLock = new ReentrantLock();
    private final McpProtocolRegistry protocols;
    private ProtocolVersion version;

    McpSession(McpProtocolRegistry protocols) {
        this.protocols = protocols;
        version = protocols.defaultProtocol().protocolVersion();
    }

    ProtocolVersion negotiate(McpCall call, ProtocolVersion transportClaim) {
        negotiationLock.lock();
        try {
            var claimed = call.metadata().protocolVersion();
            if (claimed != null) {
                version = protocols.require(claimed).protocolVersion();
                return version;
            }
            if (call instanceof McpCall.Initialize initialize) {
                var requested = initialize.requestedVersion();
                var requestedProtocol = protocols.find(requested);
                version = requestedProtocol != null && !requestedProtocol.usesStatelessMetadata()
                        ? requestedProtocol.protocolVersion()
                        : protocols.initializationFallbackProtocol().protocolVersion();
                return version;
            }
            if (transportClaim != null) {
                version = protocols.require(transportClaim).protocolVersion();
            }
            return version;
        } finally {
            negotiationLock.unlock();
        }
    }
}
