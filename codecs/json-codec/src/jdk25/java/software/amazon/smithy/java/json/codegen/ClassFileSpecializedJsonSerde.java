/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.codegen.rt.GeneratedStructDeserializer;
import software.amazon.smithy.java.codegen.rt.GeneratedStructSerializer;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.json.SpecializedJsonSerde;
import software.amazon.smithy.java.json.smithy.JsonWriterContext;

public final class ClassFileSpecializedJsonSerde implements SpecializedJsonSerde {

    private final SpecializedCodecRegistry registry;

    public ClassFileSpecializedJsonSerde() {
        this.registry = new SpecializedCodecRegistry(new ClassFileJsonCodecProfile());
    }

    @Override
    public ByteBuffer trySerialize(SerializableStruct struct, JsonSettings settings) {
        if (settings.prettyPrint()) {
            return null;
        }
        GeneratedStructSerializer ser = registry.getSerializer(struct.schema(), struct.getClass());
        if (ser == null) {
            return null;
        }
        JsonWriterContext ctx = JsonWriterContext.acquire(registry);
        ctx.useJsonName = settings.useJsonName();
        try {
            ser.serialize(struct, ctx);
            return ByteBuffer.wrap(ctx.toByteArray());
        } finally {
            JsonWriterContext.release(ctx);
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
        Schema schema = builder.schema();
        Class<?> shapeClass = schema.shapeClass();
        if (shapeClass == null) {
            return null;
        }
        GeneratedStructDeserializer de = registry.getDeserializer(schema, shapeClass);
        if (de == null) {
            return null;
        }
        JsonReaderContext ctx = JsonReaderContext.acquire(buf, offset, end, registry);
        ctx.useJsonName = settings.useJsonName();
        ctx.jsonSettings = settings;
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
