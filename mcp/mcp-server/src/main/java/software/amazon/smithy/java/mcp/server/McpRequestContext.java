/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Immutable request-scoped execution context.
 */
@SmithyUnstableApi
public record McpRequestContext(
        ProtocolVersion protocolVersion,
        McpTransportContext transport,
        Context attributes) {
    public McpRequestContext {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        transport = transport == null ? McpTransportContext.STDIO : transport;
        attributes = attributes == null ? Context.create() : attributes;
    }
}
