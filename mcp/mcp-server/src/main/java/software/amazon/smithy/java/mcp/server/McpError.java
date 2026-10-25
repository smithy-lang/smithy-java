/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Objects;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A semantic MCP error independent of a transport encoding.
 */
@SmithyUnstableApi
public record McpError(int code, String message, Document data) {
    public McpError {
        Objects.requireNonNull(message, "message");
    }
}
