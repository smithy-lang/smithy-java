/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenDiagnostics;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Internal entry point for generated native JSON codecs.
 */
@SmithyInternalApi
public final class SmithyGeneratedJsonSerde {
    private static final Object FAILED = new Object();
    private static final int MAX_PUBLISHED = 256;

    private final ConcurrentHashMap<Class<?>, Object> published = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Class<?>> publicationOrder = new ConcurrentLinkedQueue<>();
    private volatile JsonSettings settings;
    private volatile RuntimeCodecRegistry<GeneratedJsonCodec> registry;

    public ByteBuffer serialize(SerializableStruct value, JsonSettings settings) {
        if (settings.prettyPrint()) {
            return null;
        }
        GeneratedJsonCodec codec = codec(value.schema(), settings);
        if (codec == null) {
            return null;
        }
        JsonCodegenWriter writer = JsonCodegenWriter.acquire(settings);
        try {
            codec.write(value, writer);
            return writer.detach();
        } finally {
            JsonCodegenWriter.release(writer);
        }
    }

    public boolean serializeTo(
            SerializableStruct value,
            OutputStream sink,
            JsonSettings settings
    ) {
        if (settings.prettyPrint()) {
            return false;
        }
        GeneratedJsonCodec codec = codec(value.schema(), settings);
        if (codec == null) {
            return false;
        }
        JsonCodegenWriter writer = JsonCodegenWriter.acquire(settings);
        try {
            writer.sink(sink);
            codec.write(value, writer);
            writer.flush();
            return true;
        } finally {
            JsonCodegenWriter.release(writer);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends SerializableShape> T deserialize(
            byte[] source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        if (settings.prettyPrint()) {
            return null;
        }
        GeneratedJsonCodec codec = codec(builder.schema(), settings);
        if (codec == null) {
            return null;
        }
        return (T) codec.read(source, builder, settings);
    }

    public RuntimeCodegenDiagnostics.Snapshot diagnostics(JsonSettings settings) {
        RuntimeCodecRegistry<GeneratedJsonCodec> registry = this.registry;
        return registry == null
                ? new RuntimeCodegenDiagnostics().snapshot()
                : registry.diagnostics().snapshot();
    }

    public int scan(byte[] source, Schema schema, JsonSettings settings) {
        GeneratedJsonCodec codec = codec(schema, settings);
        return codec == null ? -1 : codec.scan(source, settings);
    }

    public void clear() {
        RuntimeCodecRegistry<GeneratedJsonCodec> registry = this.registry;
        if (registry != null) {
            registry.clear();
        }
        published.clear();
        publicationOrder.clear();
    }

    private GeneratedJsonCodec codec(
            Schema schema,
            JsonSettings settings
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
            return (GeneratedJsonCodec) cached;
        }
        GeneratedJsonCodec generated = registry(settings).get(schema, settings);
        Object result = generated == null ? FAILED : generated;
        Object raced = published.putIfAbsent(shapeClass, result);
        if (raced == null) {
            publicationOrder.offer(shapeClass);
            evictPublished();
        }
        Object published = raced == null ? result : raced;
        return published == FAILED ? null : (GeneratedJsonCodec) published;
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

    private RuntimeCodecRegistry<GeneratedJsonCodec> registry(JsonSettings settings) {
        RuntimeCodecRegistry<GeneratedJsonCodec> result = registry;
        if (result != null) {
            if (this.settings != settings) {
                throw new IllegalStateException("Generated JSON serde cannot be shared across settings");
            }
            return result;
        }
        synchronized (this) {
            if (registry == null) {
                this.settings = settings;
                registry = new RuntimeCodecRegistry<>(new JsonRuntimeCodegenBackend(settings));
            }
            return registry;
        }
    }
}
