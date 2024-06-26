/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Converts the Smithy data model into Documents.
 */
final class DocumentParser implements ShapeSerializer {

    private Document result;
    private boolean wroteSomething = false;

    Document getResult() {
        if (!wroteSomething) {
            throw new SerializationException(
                "Unable to create a document from ShapeSerializer that serialized nothing"
            );
        }
        return result;
    }

    private void setResult(Document result) {
        wroteSomething = true;
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
    public <T> void writeList(Schema schema, T state, BiConsumer<T, ShapeSerializer> consumer) {
        List<Document> elements = new ArrayList<>();
        var elementParser = new DocumentParser();
        ListSerializer serializer = new ListSerializer(elementParser, position -> {
            if (position > 0) {
                elements.add(elementParser.result);
                elementParser.result = null;
            }
        });
        consumer.accept(state, serializer);
        if (elementParser.result != null) {
            elements.add(elementParser.result);
        }
        setResult(new Documents.ListDocument(schema, elements));
    }

    @Override
    public <T> void writeMap(Schema schema, T state, BiConsumer<T, MapSerializer> consumer) {
        var keyMember = schema.member("key");
        if (keyMember == null) {
            throw new SerializationException(
                "Cannot create a map from a schema that does not define a map key: " + schema
            );
        } else if (keyMember.type() == ShapeType.STRING || keyMember.type() == ShapeType.ENUM) {
            var serializer = new DocumentMapSerializer();
            consumer.accept(state, serializer);
            setResult(new Documents.StringMapDocument(schema, serializer.entries));
        } else {
            throw new SerializationException("Unexpected map key schema: " + schema);
        }
    }

    private static final class DocumentMapSerializer implements MapSerializer {
        private final Map<String, Document> entries = new LinkedHashMap<>();

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
        setResult(new Documents.ByteDocument(schema, value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        setResult(new Documents.ShortDocument(schema, value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        setResult(new Documents.IntegerDocument(schema, value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        setResult(new Documents.LongDocument(schema, value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        setResult(new Documents.FloatDocument(schema, value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        setResult(new Documents.DoubleDocument(schema, value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        setResult(new Documents.BigIntegerDocument(schema, value));
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        setResult(new Documents.BigDecimalDocument(schema, value));
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
