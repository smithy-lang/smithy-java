/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

final class SmithyGeneratedCborSerde {
    private static final Object CODEGEN_PROFILE = new Object();

    private final RuntimeCodecRegistry<GeneratedCborCodec> registry =
            new RuntimeCodecRegistry<>(new CborRuntimeCodegenBackend());
    private volatile LastCodec lastCodec;

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

    @SuppressWarnings("unchecked")
    <T extends SerializableShape> T deserialize(
            ByteBuffer source,
            ShapeBuilder<T> builder,
            CborSettings settings
    ) {
        GeneratedCborCodec codec = codec(builder.schema(), settings);
        return codec == null ? null : (T) codec.read(source, builder, settings);
    }

    void clear() {
        registry.clear();
        lastCodec = null;
    }

    private GeneratedCborCodec codec(
            Schema schema,
            CborSettings settings
    ) {
        Schema root = schema.isMember() ? schema.memberTarget() : schema;
        LastCodec cached = lastCodec;
        if (cached != null && cached.schema == root) {
            return cached.codec;
        }
        GeneratedCborCodec codec = registry.get(root, CODEGEN_PROFILE);
        lastCodec = new LastCodec(root, codec);
        return codec;
    }

    private record LastCodec(
            Schema schema,
            GeneratedCborCodec codec) {}
}
