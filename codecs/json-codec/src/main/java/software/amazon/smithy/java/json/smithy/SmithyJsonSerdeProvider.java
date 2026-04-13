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
 * High-performance native JSON serde provider for smithy-java.
 *
 * <p>Writes and parses JSON bytes directly without Jackson on the hot path.
 * Registered via SPI with priority 20 (higher than Jackson's 10), so it is
 * selected by default. Jackson is used as a fallback for pretty-printing.
 *
 * <p>Can be explicitly selected via system property:
 * {@code -Dsmithy-java.json-provider=smithy}
 *
 * <p>Jackson can be forced via: {@code -Dsmithy-java.json-provider=jackson}
 */
public final class SmithyJsonSerdeProvider implements JsonSerdeProvider {

    // Lazy-init Jackson fallback for prettyPrint
    private volatile JacksonJsonSerdeProvider jacksonFallback;

    @Override
    public int getPriority() {
        // Below Jackson (10) until we have more confidence from CI fuzzing and production use.
        // Select explicitly via: -Dsmithy-java.json-provider=smithy
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
        // Direct path: acquire a pooled serializer, serialize, extract result, release.
        // Avoids 7 object allocations (~296 bytes) per call on the common path.
        var serializer = SmithyJsonSerializer.acquire(settings);
        shape.serialize(serializer);
        var result = serializer.extractResult();
        SmithyJsonSerializer.release(serializer);
        return result;
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
