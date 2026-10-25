/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Visibility of a cached MCP result.
 */
@SmithyUnstableApi
public enum McpCacheScope {
    PRIVATE("private"),
    PUBLIC("public");

    private final String wireValue;

    McpCacheScope(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
