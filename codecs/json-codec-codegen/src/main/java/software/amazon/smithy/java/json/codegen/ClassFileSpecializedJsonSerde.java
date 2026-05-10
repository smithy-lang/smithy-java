/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.codegen.rt.GeneratedStructDeserializer;
import software.amazon.smithy.java.codegen.rt.GeneratedStructSerializer;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.codegen.rt.WriterContext;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.json.SpecializedJsonSerde;

public final class ClassFileSpecializedJsonSerde implements SpecializedJsonSerde {

    private final SpecializedCodecRegistry registry;

    public ClassFileSpecializedJsonSerde() {
        this.registry = new SpecializedCodecRegistry(new ClassFileJsonCodecProfile());
    }

    @Override
    public ByteBuffer trySerialize(SerializableStruct struct, JsonSettings settings) {
        if (settings.prettyPrint() || settings.useJsonName()) {
            return null;
        }
        GeneratedStructSerializer ser = registry.getSerializer(struct.schema(), struct.getClass());
        if (ser == null) {
            return null;
        }
        WriterContext ctx = WriterContext.acquire(registry);
        try {
            ser.serialize(struct, ctx);
            return ByteBuffer.wrap(ctx.toByteArray());
        } finally {
            WriterContext.release(ctx);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends SerializableShape> T tryDeserialize(
            byte[] buf,
            int offset,
            int end,
            ShapeBuilder<T> builder,
            JsonSettings settings
    ) {
        if (settings.useJsonName()) {
            return null;
        }
        Schema schema = builder.schema();
        GeneratedStructDeserializer de = registry.getDeserializer(schema, schema.shapeClass());
        if (de == null) {
            return null;
        }
        JsonReaderContext ctx = JsonReaderContext.acquire(buf, offset, end, registry);
        try {
            return (T) de.deserialize(ctx, builder);
        } finally {
            JsonReaderContext.release(ctx);
        }
    }

    @Override
    public void warmup(Schema schema, Class<?> shapeClass) {
        registry.warmup(schema, shapeClass);
    }
}
