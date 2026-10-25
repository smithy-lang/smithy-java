/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Wire and projection features enabled by an MCP protocol.
 */
@SmithyUnstableApi
public record McpProtocolFeatures(
        boolean outputSchema,
        boolean annotations,
        boolean statelessMetadata,
        boolean httpMethodHeaders,
        boolean statelessResults) {

    public static final McpProtocolFeatures NONE =
            new McpProtocolFeatures(false, false, false, false, false);
}
