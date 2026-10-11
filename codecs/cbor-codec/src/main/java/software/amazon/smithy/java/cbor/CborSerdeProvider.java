/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.io.datastream.DataStream;

public interface CborSerdeProvider {
    int getPriority();

    String getName();

    ShapeDeserializer newDeserializer(byte[] source, CborSettings settings);

    ShapeDeserializer newDeserializer(ByteBuffer source, CborSettings settings);

    ShapeSerializer newSerializer(OutputStream sink, CborSettings settings);

    ByteBuffer serialize(SerializableShape shape, CborSettings settings);

    default DataStream serializeToDataStream(SerializableShape shape, CborSettings settings, String contentType) {
        return DataStream.ofByteBuffer(serialize(shape, settings), contentType);
    }
}
