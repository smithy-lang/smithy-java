/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Identity advertised by an MCP server.
 */
@SmithyUnstableApi
public record McpServerIdentity(String name, String version) {
    public McpServerIdentity {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
    }
}
