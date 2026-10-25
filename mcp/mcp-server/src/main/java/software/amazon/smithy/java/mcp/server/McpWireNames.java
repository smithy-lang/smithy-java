/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

final class McpWireNames {
    static final String PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    static final String CLIENT_INFO = "io.modelcontextprotocol/clientInfo";
    static final String CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    private McpWireNames() {}
}
