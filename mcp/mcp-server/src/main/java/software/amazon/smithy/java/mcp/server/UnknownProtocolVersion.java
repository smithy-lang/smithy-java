/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A protocol version not built into the core library.
 *
 * <p>The engine may still support this version through a registered
 * {@link ExtensionMcpProtocol}.
 */
@SmithyUnstableApi
public record UnknownProtocolVersion(String identifier) implements ProtocolVersion {
    public UnknownProtocolVersion {
        Objects.requireNonNull(identifier, "identifier");
    }
}
