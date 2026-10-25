/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * An MCP protocol-level error with a JSON-RPC error code and optional data.
 */
@SmithyUnstableApi
public final class McpProtocolException extends RuntimeException {
    private final int code;
    private final Document data;

    public McpProtocolException(int code, String message) {
        this(code, message, null);
    }

    public McpProtocolException(int code, String message, Document data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int code() {
        return code;
    }

    public Document data() {
        return data;
    }
}
