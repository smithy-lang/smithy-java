/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import java.util.Optional;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * One page returned by a remote MCP listing operation.
 *
 * <p>The continuation is deliberately executable rather than exposing the remote
 * server's opaque cursor. Calling it performs exactly one request for the next page.
 */
@SmithyUnstableApi
public record McpPage<T>(List<T> items, Optional<NextPage<T>> nextPage) {
    public McpPage {
        items = List.copyOf(items);
    }

    public static <T> McpPage<T> last(List<T> items) {
        return new McpPage<>(items, Optional.empty());
    }

    public static <T> McpPage<T> continued(List<T> items, NextPage<T> nextPage) {
        return new McpPage<>(items, Optional.of(nextPage));
    }

    /**
     * Retrieves exactly one subsequent page.
     */
    @FunctionalInterface
    public interface NextPage<T> {
        McpPage<T> fetch();
    }
}
