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

final class DefaultCborSerdeProvider implements CborSerdeProvider {
    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getName() {
        return "cbor";
    }

    @Override
    public ShapeDeserializer newDeserializer(byte[] source, CborSettings settings) {
        return new CborDeserializer(source, settings);
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, CborSettings settings) {
        return new CborDeserializer(source, settings);
    }

    @Override
    public ShapeSerializer newSerializer(OutputStream sink, CborSettings settings) {
        return new CborSerializer(sink);
    }

    @Override
    public ByteBuffer serialize(SerializableShape shape, CborSettings settings) {
        var serializer = CborSerializer.acquire();
        boolean exception = false;
        try {
            shape.serialize(serializer);
            return serializer.extractResult();
        } catch (Exception t) {
            exception = true;
            throw t;
        } finally {
            CborSerializer.release(serializer, exception);
        }
    }
}
