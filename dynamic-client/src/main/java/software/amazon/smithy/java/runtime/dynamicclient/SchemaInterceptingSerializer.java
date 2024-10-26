/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.dynamicclient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

final class SchemaInterceptingSerializer implements ShapeSerializer {

    private final Schema delegateSchema;
    private final ShapeSerializer delegateSerializer;

    SchemaInterceptingSerializer(Schema delegateSchema, ShapeSerializer delegateSerializer) {
        this.delegateSchema = delegateSchema;
        this.delegateSerializer = delegateSerializer;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        delegateSerializer
            .writeStruct(delegateSchema, new WrappedDocument(schema, Document.createTyped(struct)));
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        delegateSerializer.writeList(delegateSchema, listState, size, (s, ls) -> {
            consumer.accept(s, new SchemaInterceptingSerializer(delegateSchema.listMember(), ls));
        });
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        var delegateKeySchema = delegateSchema.mapKeyMember();
        var delegateValueSchema = delegateSchema.mapValueMember();
        delegateSerializer.writeMap(delegateSchema, mapState, size, (s, ms) -> {
            consumer.accept(s, new MapSerializer() {
                @Override
                public <V> void writeEntry(
                    Schema keySchema,
                    String key,
                    V state,
                    BiConsumer<V, ShapeSerializer> valueSerializer
                ) {
                    ms.writeEntry(delegateKeySchema, key, state, (s, ms) -> {
                        valueSerializer.accept(s, new SchemaInterceptingSerializer(delegateValueSchema, ms));
                    });
                }
            });
        });
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        delegateSerializer.writeBoolean(delegateSchema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        delegateSerializer.writeByte(delegateSchema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        delegateSerializer.writeShort(delegateSchema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        delegateSerializer.writeInteger(delegateSchema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        delegateSerializer.writeLong(delegateSchema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        delegateSerializer.writeFloat(delegateSchema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        delegateSerializer.writeDouble(delegateSchema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        delegateSerializer.writeBigInteger(delegateSchema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        delegateSerializer.writeBigDecimal(delegateSchema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        delegateSerializer.writeString(delegateSchema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        delegateSerializer.writeBlob(delegateSchema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        delegateSerializer.writeTimestamp(delegateSchema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        // Note that we ignore the provided schema and check the delegate schema to see if it's actually a document.
        if (delegateSchema.type() == ShapeType.DOCUMENT) {
            // Write the document since it's actually modeled as a document.
            delegateSerializer.writeDocument(delegateSchema, value);
        } else {
            // It's not a document: serialize the contents of the document and skip writing the document wrapper.
            value.serializeContents(this);
        }
    }

    @Override
    public void writeNull(Schema schema) {
        delegateSerializer.writeNull(delegateSchema);
    }
}
