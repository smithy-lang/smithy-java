/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
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
            throw new SdkSerdeException("Unable to create a document from ShapeSerializer that serialized nothing");
        }
        return result;
    }

    private void setResult(Document result) {
        wroteSomething = true;
        this.result = result;
    }

    @Override
    public void writeStruct(SdkSchema schema, SerializableStruct struct) {
        if (schema.type() != ShapeType.STRUCTURE && schema.type() != ShapeType.UNION) {
            throw new SdkSerdeException("Expected a structure or union for this document, but found " + schema);
        }

        Map<String, Document> members = new LinkedHashMap<>();
        struct.serializeMembers(new StructureParser(members));

        setResult(new Documents.StructureDocument(schema, members));
    }

    private static final class StructureParser extends InterceptingSerializer {
        private final DocumentParser parser = new DocumentParser();
        private final Map<String, Document> members;

        private StructureParser(Map<String, Document> members) {
            this.members = members;
        }

        @Override
        protected ShapeSerializer before(SdkSchema schema) {
            return parser;
        }

        @Override
        protected void after(SdkSchema schema) {
            members.put(schema.memberName(), parser.getResult());
        }
    }

    @Override
    public <T> void writeList(SdkSchema schema, T state, BiConsumer<T, ShapeSerializer> consumer) {
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
    public <T> void writeMap(SdkSchema schema, T state, BiConsumer<T, MapSerializer> consumer) {
        var keyMember = schema.member("key");
        if (keyMember == null) {
            throw new SdkSerdeException("Cannot create a map from a schema that does not define a map key: " + schema);
        } else if (keyMember.type() == ShapeType.STRING || keyMember.type() == ShapeType.ENUM) {
            var serializer = new DocumentMapSerializer();
            consumer.accept(state, serializer);
            setResult(new Documents.StringMapDocument(schema, serializer.entries));
        } else {
            throw new SdkSerdeException("Unexpected map key schema: " + schema);
        }
    }

    private static final class DocumentMapSerializer implements MapSerializer {
        private final Map<String, Document> entries = new LinkedHashMap<>();

        @Override
        public <U> void writeEntry(
            SdkSchema keySchema,
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
    public void writeBoolean(SdkSchema schema, boolean value) {
        setResult(new Documents.BooleanDocument(schema, value));
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        setResult(new Documents.ByteDocument(schema, value));
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        setResult(new Documents.ShortDocument(schema, value));
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        setResult(new Documents.IntegerDocument(schema, value));
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        setResult(new Documents.LongDocument(schema, value));
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        setResult(new Documents.FloatDocument(schema, value));
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        setResult(new Documents.DoubleDocument(schema, value));
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        setResult(new Documents.BigIntegerDocument(schema, value));
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        setResult(new Documents.BigDecimalDocument(schema, value));
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        setResult(new Documents.StringDocument(schema, value));
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        setResult(new Documents.BlobDocument(schema, value));
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        setResult(new Documents.TimestampDocument(schema, value));
    }

    @Override
    public void writeDocument(SdkSchema schema, Document value) {
        value.serializeContents(this);
    }

    @Override
    public void writeNull(SdkSchema schema) {
        setResult(null);
    }
}
