/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonSettings;

interface GeneratedJsonCodec {
    void write(SerializableShape value, JsonCodegenWriter writer);

    boolean acceptsBuilder(ShapeBuilder<?> builder);

    void readInto(
            byte[] source,
            int offset,
            int end,
            ShapeBuilder<?> builder,
            JsonSettings settings
    );

    default SerializableShape read(byte[] source, ShapeBuilder<?> builder, JsonSettings settings) {
        readInto(source, 0, source.length, builder, settings);
        return builder.errorCorrection().build();
    }

    int scan(byte[] source, JsonSettings settings);

    default void writeMapValue(int mapId, Object value, JsonCodegenWriter writer) {
        throw new UnsupportedOperationException("Codec has no map members");
    }
}
