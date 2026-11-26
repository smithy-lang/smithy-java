/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;

final class McpJson {
    static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    /**
     * Returns the wire name for a member schema, respecting @jsonName trait.
     *
     * @param member The member schema to get the wire name for.
     * @return The wire name as it appears on the JSON wire.
     */
    static String wireName(Schema member) {
        return CODEC.settings().fieldMapper().memberToField(member);
    }

    private McpJson() {}
}
