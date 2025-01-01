/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ListSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Converts the Smithy data model into Documents.
 */
public final class DocumentParser implements ShapeSerializer {

    private Document result;

    /**
     * Get the currently stored result, or null if nothing was serialized.
     *
     * @return the result.
     */
    public Document getResult() {
        return result;
    }

    /**
     * Clears the value stored in the parser, and returns the value.
     *
     * @return the result being cleared, or null if no result is stored.
     */
    public Document clearResult() {
        var value = this.result;
        this.result = null;
        return value;
    }

    private void setResult(Document result) {
        this.result = result;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        if (schema.type() == ShapeType.STRUCTURE) {
            setResult(new Documents.LazyStructure(schema, struct));
        } else {
            var parser = new StructureParser();
            struct.serializeMembers(parser);
            setResult(new Documents.StructureDocument(schema, parser.members()));
        }
    }

    /**
     * Parses a structure's members. Nested structures are parsed into LazyStructures.
     */
    static final class StructureParser extends InterceptingSerializer {
        private final DocumentParser parser = new DocumentParser();
        private final Map<String, Document> members = new LinkedHashMap<>();

        @Override
        protected ShapeSerializer before(Schema schema) {
            return parser;
        }

        @Override
        protected void after(Schema schema) {
            members.put(schema.memberName(), parser.getResult());
        }

        Map<String, Document> members() {
            return members;
        }
    }

    @Override
    public <T extends List<?>> void writeList(
            Schema schema,
            T state,
            int size,
            BiConsumer<T, ShapeSerializer> consumer
    ) {
        List<Document> elements = size == -1 ? new ArrayList<>() : new ArrayList<>(size);
        var elementParser = new DocumentParser();
        ListSerializer serializer = new ListSerializer(elementParser, position -> {
            if (position > 0) {
                elements.add(elementParser.result);
                elementParser.result = null;
            }
        });
        consumer.accept(state, serializer);
        // Note that neither here nor between values are we testing to ensure the value is not null. That's because
        // this method is used primarily with documents that have no schema, and therefore cannot specify that a
        // list is sparse. Rather than drop null values, they are retained.
        elements.add(elementParser.result);
        setResult(new Documents.ListDocument(schema, elements));
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        var keyMember = schema.mapKeyMember();
        if (keyMember.type() == ShapeType.STRING || keyMember.type() == ShapeType.ENUM) {
            var serializer = new DocumentMapSerializer(size);
            consumer.accept(state, serializer);
            setResult(new Documents.StringMapDocument(schema, serializer.entries));
        } else {
            throw new SerializationException("Unexpected map key schema: " + schema);
        }
    }

    private static final class DocumentMapSerializer implements MapSerializer {
        private final Map<String, Document> entries;

        DocumentMapSerializer(int size) {
            entries = size == -1 ? new LinkedHashMap<>() : new LinkedHashMap<>(size);
        }

        @Override
        public <U> void writeEntry(
                Schema keySchema,
                String key,
                U keyState,
                BiConsumer<U, ShapeSerializer> valueSerializer
        ) {
            DocumentParser valueParser = new DocumentParser();
            valueSerializer.accept(keyState, valueParser);
            entries.put(key, valueParser.result);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        setResult(new Documents.BooleanDocument(schema, value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        setResult(new Documents.NumberDocument(schema, value));
    }

    @Override
    public void writeString(Schema schema, String value) {
        setResult(new Documents.StringDocument(schema, value));
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        setResult(new Documents.BlobDocument(schema, value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        setResult(new Documents.TimestampDocument(schema, value));
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        value.serializeContents(this);
    }

    @Override
    public void writeNull(Schema schema) {
        setResult(null);
    }
}
