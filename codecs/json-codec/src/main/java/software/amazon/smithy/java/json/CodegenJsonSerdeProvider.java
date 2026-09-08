/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.json.smithy.SmithyGeneratedJsonSerde;

final class CodegenJsonSerdeProvider implements JsonSerdeProvider {
    private final JsonSerdeProvider delegate;
    private final SmithyGeneratedJsonSerde generated = new SmithyGeneratedJsonSerde();

    CodegenJsonSerdeProvider(JsonSerdeProvider delegate) {
        this.delegate = delegate;
    }

    JsonSerdeProvider delegate() {
        return delegate;
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
        } else if (settings.strictRuntimeCodegen()) {
            throw new IllegalStateException(
                    "Strict JSON runtime codegen requires a structure or union root, found "
                            + shape.getClass().getName());
        }
        return delegate.serialize(shape, settings);
    }

    ByteBuffer serializeMemberSubset(
            SerializableStruct struct,
            MemberSubsetCodec.MemberSubset subset,
            JsonSettings settings
    ) {
        return generated.serialize(struct, settings, subset);
    }

    boolean deserializeMemberSubset(
            Schema schema,
            ShapeBuilder<?> builder,
            ByteBuffer source,
            MemberSubsetCodec.MemberSubset subset,
            JsonSettings settings
    ) {
        return generated.deserialize(schema, builder, source, settings, subset);
    }

    boolean deserializeInto(
            Schema schema,
            ShapeBuilder<?> builder,
            ByteBuffer source,
            JsonSettings settings
    ) {
        return generated.deserialize(schema, builder, source, settings, null);
    }

    @Override
    public ShapeDeserializer newDeserializer(byte[] source, JsonSettings settings) {
        rejectStrictRootlessDeserializer(settings);
        return delegate.newDeserializer(source, settings);
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, JsonSettings settings) {
        rejectStrictRootlessDeserializer(settings);
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
        T result = generated.deserialize(source, builder, settings);
        if (result != null) {
            return result;
        }
        return genericDeserialize(source, builder, settings);
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

    private <T extends SerializableShape> T genericDeserialize(
            ByteBuffer source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        try (ShapeDeserializer deserializer = delegate.newDeserializer(source, settings)) {
            return builder.deserialize(deserializer).errorCorrection().build();
        }
    }

    private static void rejectStrictRootlessDeserializer(JsonSettings settings) {
        if (settings.strictRuntimeCodegen()) {
            throw new IllegalStateException(
                    "Strict JSON runtime codegen requires deserializeShape with a concrete builder; "
                            + "createDeserializer would use dispatch serde");
        }
    }
}
