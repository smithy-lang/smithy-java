/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

/**
 * Internal control signal used only when a selected protocol does not define a method.
 */
final class McpUnsupportedMethodException extends RuntimeException {
    McpUnsupportedMethodException(McpMethod method, McpProtocolId protocolId) {
        super(method.wireName() + " is not supported by MCP " + protocolId.identifier());
    }
}
