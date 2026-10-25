/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Cache metadata attached to a cacheable MCP result.
 *
 * @param ttlMs cache lifetime in milliseconds.
 * @param scope cache visibility.
 */
@SmithyUnstableApi
public record McpCacheHint(long ttlMs, McpCacheScope scope) {
    public static final McpCacheHint NO_CACHE = new McpCacheHint(0, McpCacheScope.PRIVATE);

    public McpCacheHint {
        if (ttlMs < 0) {
            throw new IllegalArgumentException("MCP cache TTL must not be negative");
        }
        Objects.requireNonNull(scope, "scope");
    }
}
