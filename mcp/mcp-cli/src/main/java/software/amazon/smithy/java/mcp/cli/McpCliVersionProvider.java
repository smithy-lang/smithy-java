/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import software.amazon.smithy.utils.IoUtils;

public interface McpCliVersionProvider {

    String getVersion();

    int priority();

    static McpCliVersionProvider getDefault() {
        return new McpCliVersionProvider() {
            @Override
            public String getVersion() {
                return IoUtils.readUtf8Resource(VersionProvider.class, "VERSION").trim();
            }

            @Override
            public int priority() {
                return Integer.MAX_VALUE;
            }
        };
    }
}
