/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

final class CodegenJsonSerdeProvider implements JsonSerdeProvider {
    private final JsonSerdeProvider delegate;
    private final SpecializedJsonSerde codegen;

    CodegenJsonSerdeProvider(JsonSerdeProvider delegate, SpecializedJsonSerde codegen) {
        this.delegate = delegate;
        this.codegen = codegen;
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
            ByteBuffer fast = codegen.trySerialize(struct, settings);
            if (fast != null) {
                return fast;
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
        return new CodegenShapeSerializer(sink, delegate, codegen, settings);
    }

    @Override
    public <T extends SerializableShape> T deserializeShape(
            byte[] source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        T fast = codegen.tryDeserialize(source, 0, source.length, builder, settings);
        if (fast != null) {
            return fast;
        }
        return delegate.deserializeShape(source, builder, settings);
    }

    @Override
    public <T extends SerializableShape> T deserializeShape(
            ByteBuffer source,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        byte[] bytes;
        int offset, end;
        if (source.hasArray()) {
            bytes = source.array();
            offset = source.arrayOffset() + source.position();
            end = offset + source.remaining();
        } else {
            bytes = new byte[source.remaining()];
            source.duplicate().get(bytes);
            offset = 0;
            end = bytes.length;
        }
        T fast = codegen.tryDeserialize(bytes, offset, end, builder, settings);
        if (fast != null) {
            return fast;
        }
        return delegate.deserializeShape(source, builder, settings);
    }
}
