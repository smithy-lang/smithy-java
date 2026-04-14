/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.json.JsonSerdeProvider;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;

/**
 * Native JSON serde provider for smithy-java.
 *
 * <p>Can be explicitly selected via system property:
 * {@code -Dsmithy-java.json-provider=smithy}
 */
public final class SmithyJsonSerdeProvider implements JsonSerdeProvider {

    private volatile JacksonJsonSerdeProvider jacksonFallback;

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getName() {
        return "smithy";
    }

    @Override
    public ByteBuffer serialize(SerializableShape shape, JsonSettings settings) {
        if (settings.prettyPrint()) {
            return getJacksonFallback().serialize(shape, settings);
        }
        var serializer = SmithyJsonSerializer.acquire(settings);
        boolean exception = false;
        try {
            shape.serialize(serializer);
            return serializer.extractResult();
        } catch (Exception t) {
            exception = true;
            throw t;
        } finally {
            SmithyJsonSerializer.release(serializer, exception);
        }
    }

    @Override
    public ShapeSerializer newSerializer(OutputStream sink, JsonSettings settings) {
        if (settings.prettyPrint()) {
            return getJacksonFallback().newSerializer(sink, settings);
        }
        return new SmithyJsonSerializer(sink, settings);
    }

    @Override
    public ShapeDeserializer newDeserializer(byte[] source, JsonSettings settings) {
        return new SmithyJsonDeserializer(source, 0, source.length, settings);
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, JsonSettings settings) {
        if (source.hasArray()) {
            int offset = source.arrayOffset() + source.position();
            int length = source.remaining();
            return new SmithyJsonDeserializer(source.array(), offset, offset + length, settings);
        }
        // Non-array-backed ByteBuffer (rare) — copy to byte[]
        byte[] bytes = new byte[source.remaining()];
        source.duplicate().get(bytes);
        return new SmithyJsonDeserializer(bytes, 0, bytes.length, settings);
    }

    private JacksonJsonSerdeProvider getJacksonFallback() {
        if (jacksonFallback == null) {
            jacksonFallback = new JacksonJsonSerdeProvider();
        }
        return jacksonFallback;
    }
}
