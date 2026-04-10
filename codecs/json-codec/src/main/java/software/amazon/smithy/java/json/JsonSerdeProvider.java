/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.io.ByteBufferOutputStream;

public interface JsonSerdeProvider {

    int getPriority();

    String getName();

    ShapeDeserializer newDeserializer(byte[] source, JsonSettings settings);

    ShapeDeserializer newDeserializer(ByteBuffer source, JsonSettings settings);

    ShapeSerializer newSerializer(OutputStream sink, JsonSettings settings);

    /**
     * Serializes a shape directly to a ByteBuffer.
     *
     * <p>The default implementation uses a ByteBufferOutputStream, but providers
     * can override this to avoid the intermediate buffer copy.
     */
    default ByteBuffer serialize(SerializableShape shape, JsonSettings settings) {
        var baos = new ByteBufferOutputStream(256);
        try (var serializer = newSerializer(baos, settings)) {
            shape.serialize(serializer);
        }
        return baos.toByteBuffer();
    }

}
