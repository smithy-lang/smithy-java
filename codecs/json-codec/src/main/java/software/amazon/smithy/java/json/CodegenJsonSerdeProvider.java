/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenDiagnostics;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.json.smithy.SmithyGeneratedJsonSerde;

final class CodegenJsonSerdeProvider implements JsonSerdeProvider {
    private final JsonSerdeProvider delegate;
    private final SmithyGeneratedJsonSerde generated = new SmithyGeneratedJsonSerde();

    CodegenJsonSerdeProvider(JsonSerdeProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getPriority() {
        return delegate.getPriority();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public ByteBuffer serialize(SerializableShape shape, JsonSettings settings) {
        if (shape instanceof SerializableStruct struct) {
            ByteBuffer result = generated.serialize(struct, settings);
            if (result != null) {
                return result;
            }
        }
        return delegate.serialize(shape, settings);
    }

    @Override
    public ShapeDeserializer newDeserializer(byte[] source, JsonSettings settings) {
        return delegate.newDeserializer(source, settings);
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, JsonSettings settings) {
        return delegate.newDeserializer(source, settings);
    }

    @Override
    public ShapeSerializer newSerializer(OutputStream sink, JsonSettings settings) {
        return new CodegenShapeSerializer(sink, delegate, generated, settings);
    }

    <T extends SerializableShape> T deserialize(
            byte[] source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        T result = generated.deserialize(source, builder, settings);
        if (result != null) {
            return result;
        }
        return genericDeserialize(source, builder, settings);
    }

    <T extends SerializableShape> T deserialize(
            ByteBuffer source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        byte[] bytes;
        if (source.hasArray()) {
            int offset = source.arrayOffset() + source.position();
            int length = source.remaining();
            if (offset == 0 && length == source.array().length) {
                bytes = source.array();
            } else {
                bytes = new byte[length];
                System.arraycopy(source.array(), offset, bytes, 0, length);
            }
        } else {
            bytes = new byte[source.remaining()];
            source.duplicate().get(bytes);
        }
        return deserialize(bytes, builder, settings);
    }

    private <T extends SerializableShape> T genericDeserialize(
            byte[] source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        try (ShapeDeserializer deserializer = delegate.newDeserializer(source, settings)) {
            return builder.deserialize(deserializer).errorCorrection().build();
        }
    }

    RuntimeCodegenDiagnostics.Snapshot diagnostics(JsonSettings settings) {
        return generated.diagnostics(settings);
    }
}
