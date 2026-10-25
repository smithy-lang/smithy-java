/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

/**
 * Failure while communicating with a remote MCP server.
 */
public final class McpRemoteException extends RuntimeException {
    McpRemoteException(String message) {
        super(message);
    }

    McpRemoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
