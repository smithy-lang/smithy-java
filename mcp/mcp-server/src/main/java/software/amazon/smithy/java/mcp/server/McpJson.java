/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;

final class McpJson {
    static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private McpJson() {}
}
