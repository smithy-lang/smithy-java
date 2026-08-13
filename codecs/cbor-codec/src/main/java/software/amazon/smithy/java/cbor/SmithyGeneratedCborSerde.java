/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenDiagnostics;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

final class SmithyGeneratedCborSerde {
    private static final Object FAILED = new Object();
    private static final int MAX_PUBLISHED = 256;

    private final RuntimeCodecRegistry<GeneratedCborCodec> registry =
            new RuntimeCodecRegistry<>(new CborRuntimeCodegenBackend());
    private final ConcurrentHashMap<Class<?>, Object> published = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Class<?>> publicationOrder = new ConcurrentLinkedQueue<>();

    ByteBuffer serialize(SerializableStruct value, CborSettings settings) {
        GeneratedCborCodec codec = codec(value.schema(), settings);
        if (codec == null) {
            return null;
        }
        CborSerializer writer = CborSerializer.acquire();
        try {
            codec.write(value, writer);
            return writer.extractResult();
        } finally {
            CborSerializer.release(writer, false);
        }
    }

    @SuppressWarnings("unchecked")
    <T extends SerializableShape> T deserialize(
            byte[] source,
            ShapeBuilder<T> builder,
            CborSettings settings
    ) {
        GeneratedCborCodec codec = codec(builder.schema(), settings);
        return codec == null ? null : (T) codec.read(source, builder, settings);
    }

    RuntimeCodegenDiagnostics.Snapshot diagnostics() {
        return registry.diagnostics().snapshot();
    }

    void clear() {
        registry.clear();
        published.clear();
        publicationOrder.clear();
    }

    private GeneratedCborCodec codec(
            Schema schema,
            CborSettings settings
    ) {
        Class<?> shapeClass = schema.shapeClass();
        if (shapeClass == null) {
            return null;
        }
        Object cached = published.get(shapeClass);
        if (cached != null) {
            if (cached == FAILED) {
                registry.diagnostics().fallback();
                return null;
            }
            return (GeneratedCborCodec) cached;
        }
        GeneratedCborCodec generated = registry.get(schema, settings);
        Object result = generated == null ? FAILED : generated;
        Object raced = published.putIfAbsent(shapeClass, result);
        if (raced == null) {
            publicationOrder.offer(shapeClass);
            evictPublished();
        }
        Object published = raced == null ? result : raced;
        return published == FAILED ? null : (GeneratedCborCodec) published;
    }

    private void evictPublished() {
        while (published.size() > MAX_PUBLISHED) {
            Class<?> evicted = publicationOrder.poll();
            if (evicted == null) {
                return;
            }
            published.remove(evicted);
        }
    }
}
