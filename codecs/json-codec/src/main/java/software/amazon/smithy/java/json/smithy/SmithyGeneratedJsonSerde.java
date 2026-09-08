/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SmithyGeneratedJsonSerde {
    private static final int MAX_SETTINGS = 16;
    private static final int MAX_SUBSETS = 16;

    // Keep selector identities stable for the registry cache, with a bound for caller-supplied keys.
    private final ConcurrentHashMap<
            MemberSubsetCodec.MemberSubset,
            RuntimeCodecBackend.MemberSelector> selectors = new ConcurrentHashMap<>();

    private final IdentityHashMap<JsonSettings, RuntimeCodecRegistry<GeneratedJsonCodec>> registries =
            new IdentityHashMap<>();
    private final ArrayDeque<JsonSettings> settingsOrder = new ArrayDeque<>();
    private volatile CachedRegistry lastRegistry;
    private volatile LastCodec lastCodec;

    public ByteBuffer serialize(SerializableStruct value, JsonSettings settings) {
        return serialize(value, settings, null);
    }

    public ByteBuffer serialize(
            SerializableStruct value,
            JsonSettings settings,
            MemberSubsetCodec.MemberSubset subset
    ) {
        if (settings.prettyPrint()) {
            rejectStrictFallback(settings, "pretty-printed JSON is not supported");
            return null;
        }
        if (!generatable(value)) {
            rejectStrictFallback(settings, "the value is not the generated class for " + value.schema().id());
            return null;
        }
        GeneratedJsonCodec codec = codec(value.schema(), settings, subset);
        if (codec == null) {
            rejectStrictFallback(settings, "no generated codec is available for " + value.schema().id());
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
            rejectStrictFallback(settings, "pretty-printed JSON is not supported");
            return false;
        }
        if (!generatable(value)) {
            rejectStrictFallback(settings, "the value is not the generated class for " + value.schema().id());
            return false;
        }
        GeneratedJsonCodec codec = codec(value.schema(), settings);
        if (codec == null) {
            rejectStrictFallback(settings, "no generated codec is available for " + value.schema().id());
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
            rejectStrictFallback(settings, "pretty-printed JSON is not supported");
            return null;
        }
        GeneratedJsonCodec codec = codec(builder.schema(), settings);
        if (codec == null) {
            rejectStrictFallback(settings, "no generated codec is available for " + builder.schema().id());
            return null;
        }
        if (!codec.acceptsBuilder(builder)) {
            rejectStrictFallback(
                    settings,
                    "the builder is not the generated builder for " + builder.schema().id());
            return null;
        }
        if (isEmptyObject(source, 0, source.length)) {
            return (T) builder.errorCorrection().build();
        }
        return (T) codec.read(source, builder, settings);
    }

    @SuppressWarnings("unchecked")
    public <T extends SerializableShape> T deserialize(
            ByteBuffer source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        if (settings.prettyPrint()) {
            rejectStrictFallback(settings, "pretty-printed JSON is not supported");
            return null;
        }
        GeneratedJsonCodec codec = codec(builder.schema(), settings);
        if (codec == null) {
            rejectStrictFallback(settings, "no generated codec is available for " + builder.schema().id());
            return null;
        }
        if (!codec.acceptsBuilder(builder)) {
            rejectStrictFallback(
                    settings,
                    "the builder is not the generated builder for " + builder.schema().id());
            return null;
        }
        readInto(codec, source, builder, settings);
        return (T) builder.errorCorrection().build();
    }

    public boolean deserialize(
            Schema schema,
            ShapeBuilder<?> builder,
            ByteBuffer source,
            JsonSettings settings,
            MemberSubsetCodec.MemberSubset subset
    ) {
        if (settings.prettyPrint()) {
            rejectStrictFallback(settings, "pretty-printed JSON is not supported");
            return false;
        }
        GeneratedJsonCodec codec = codec(schema, settings, subset);
        if (codec == null) {
            rejectStrictFallback(settings, "no generated codec is available for " + schema.id());
            return false;
        }
        if (!codec.acceptsBuilder(builder)) {
            rejectStrictFallback(
                    settings,
                    "the builder is not the generated builder for " + builder.schema().id());
            return false;
        }
        readInto(codec, source, builder, settings);
        return true;
    }

    private static void readInto(
            GeneratedJsonCodec codec,
            ByteBuffer source,
            ShapeBuilder<?> builder,
            JsonSettings settings
    ) {
        if (source.hasArray()) {
            int offset = source.arrayOffset() + source.position();
            int end = offset + source.remaining();
            byte[] bytes = source.array();
            if (!isEmptyObject(bytes, offset, end)) {
                codec.readInto(bytes, offset, end, builder, settings);
            }
            return;
        }
        ByteBuffer duplicate = source.duplicate();
        byte[] result = new byte[duplicate.remaining()];
        duplicate.get(result);
        if (!isEmptyObject(result, 0, result.length)) {
            codec.readInto(result, 0, result.length, builder, settings);
        }
    }

    private static boolean isEmptyObject(byte[] source, int offset, int end) {
        return end - offset == 2 && source[offset] == '{' && source[offset + 1] == '}';
    }

    public int scan(byte[] source, Schema schema, JsonSettings settings) {
        GeneratedJsonCodec codec = codec(schema, settings);
        if (codec == null) {
            rejectStrictFallback(settings, "no generated codec is available for " + schema.id());
            return -1;
        }
        return codec.scan(source, settings);
    }

    public synchronized void clear() {
        for (RuntimeCodecRegistry<GeneratedJsonCodec> registry : registries.values()) {
            registry.clear();
        }
        registries.clear();
        settingsOrder.clear();
        selectors.clear();
        lastRegistry = null;
        lastCodec = null;
    }

    // Generated writers cast to the schema's concrete class; schema-compatible proxies must fall back.
    private static boolean generatable(SerializableStruct value) {
        Schema schema = value.schema();
        Class<?> shapeClass = (schema.isMember() ? schema.memberTarget() : schema).shapeClass();
        return shapeClass != null && value.getClass() == shapeClass;
    }

    private static void rejectStrictFallback(JsonSettings settings, String reason) {
        if (settings.strictRuntimeCodegen()) {
            throw new IllegalStateException("Strict JSON runtime codegen cannot fall back: " + reason);
        }
    }

    private GeneratedJsonCodec codec(
            Schema schema,
            JsonSettings settings
    ) {
        return codec(schema, settings, null);
    }

    private GeneratedJsonCodec codec(
            Schema schema,
            JsonSettings settings,
            MemberSubsetCodec.MemberSubset subset
    ) {
        Schema root = schema.isMember() ? schema.memberTarget() : schema;
        LastCodec cached = lastCodec;
        if (cached != null && cached.schema.get() == root
                && cached.settings == settings
                && cached.subset == subset) {
            GeneratedJsonCodec codec = cached.codec.get();
            if (codec != null) {
                return codec;
            }
        }
        RuntimeCodecBackend.MemberSelector selector;
        if (subset == null) {
            selector = RuntimeCodecBackend.MemberSelector.all();
        } else {
            selector = selectorFor(subset);
            if (selector == null) {
                return null;
            }
        }
        GeneratedJsonCodec codec = registry(settings).get(root, settings, selector);
        lastCodec = new LastCodec(new WeakReference<>(root), settings, subset, new WeakReference<>(codec));
        return codec;
    }

    private RuntimeCodecBackend.MemberSelector selectorFor(MemberSubsetCodec.MemberSubset subset) {
        RuntimeCodecBackend.MemberSelector selector = selectors.get(subset);
        if (selector != null) {
            return selector;
        }
        return selectors.size() < MAX_SUBSETS
                ? selectors.computeIfAbsent(subset, s -> s::includes)
                : null;
    }

    private RuntimeCodecRegistry<GeneratedJsonCodec> registry(JsonSettings settings) {
        CachedRegistry cached = lastRegistry;
        if (cached != null && cached.settings == settings) {
            return cached.registry;
        }
        synchronized (this) {
            RuntimeCodecRegistry<GeneratedJsonCodec> result = registries.get(settings);
            if (result == null) {
                result = new RuntimeCodecRegistry<>(new JsonRuntimeCodegenBackend(settings));
                registries.put(settings, result);
                settingsOrder.addLast(settings);
                if (registries.size() > MAX_SETTINGS) {
                    JsonSettings evicted = settingsOrder.removeFirst();
                    RuntimeCodecRegistry<GeneratedJsonCodec> evictedRegistry = registries.remove(evicted);
                    if (evictedRegistry != null) {
                        evictedRegistry.clear();
                    }
                }
            }
            lastRegistry = new CachedRegistry(settings, result);
            return result;
        }
    }

    private record CachedRegistry(
            JsonSettings settings,
            RuntimeCodecRegistry<GeneratedJsonCodec> registry) {}

    private record LastCodec(
            WeakReference<Schema> schema,
            JsonSettings settings,
            MemberSubsetCodec.MemberSubset subset,
            WeakReference<GeneratedJsonCodec> codec) {}
}
