/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

interface GeneratedCborCodec {
    void write(SerializableShape value, CborSerializer writer);

    SerializableShape read(byte[] source, ShapeBuilder<?> builder, CborSettings settings);

    SerializableShape read(ByteBuffer source, ShapeBuilder<?> builder, CborSettings settings);

    default void writeMapValue(int mapId, Object value, CborSerializer writer) {
        throw new UnsupportedOperationException("Codec has no map members");
    }
}
