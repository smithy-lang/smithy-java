/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;

record McpCursorPage<T>(List<T> items, String nextCursor) {
    McpCursorPage {
        items = List.copyOf(items);
    }
}
