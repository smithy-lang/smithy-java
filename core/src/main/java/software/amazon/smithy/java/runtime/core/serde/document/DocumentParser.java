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
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.StructSerializer;

final class DocumentParser implements ShapeSerializer {

    private Document result;

    Document getResult() {
        return Objects.requireNonNull(result, "result was not set by the shape serializer");
    }

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        result = null;
        Map<String, Document> members = new LinkedHashMap<>();
        return new StructSerializer() {
            @Override
            public void endStruct() {
                result = Document.ofStruct(members);
            }

            @Override
            public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
                String memberName = member.memberName();
                DocumentParser parser = new DocumentParser();
                memberWriter.accept(parser);
                var result = parser.result;
                members.put(memberName, result);
            }
        };
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        List<Document> elements = new ArrayList<>();
        var elementParser = new DocumentParser();
        ListSerializer serializer = new ListSerializer(elementParser, position -> {
            if (position > 0) {
                elements.add(elementParser.result);
                elementParser.result = null;
            }
        });
        consumer.accept(serializer);
        if (elementParser.result != null) {
            elements.add(elementParser.result);
        }
        result = Document.of(elements);
    }

    @Override
    public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        Map<Document, Document> entries = new LinkedHashMap<>();
        consumer.accept(new MapSerializer() {
            @Override
            public void entry(String key, Consumer<ShapeSerializer> valueSerializer) {
                DocumentParser p = new DocumentParser();
                valueSerializer.accept(p);
                entries.put(Document.of(key), p.result);
            }

            @Override
            public void entry(int key, Consumer<ShapeSerializer> valueSerializer) {
                DocumentParser p = new DocumentParser();
                valueSerializer.accept(p);
                entries.put(Document.of(key), p.result);
            }

            @Override
            public void entry(long key, Consumer<ShapeSerializer> valueSerializer) {
                DocumentParser p = new DocumentParser();
                valueSerializer.accept(p);
                entries.put(Document.of(key), p.result);
            }
        });
        result = Document.ofMap(entries);
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        result = Document.of(value);
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        result = Document.of(value);
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        result = Document.of(value);
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        result = Document.of(value);
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        result = Document.of(value);
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        result = Document.of(value);
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        result = Document.of(value);
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        result = Document.of(value);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        result = Document.of(value);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        result = Document.of(value);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        result = Document.of(value);
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        result = Document.of(value);
    }

    @Override
    public void flush() {}
}
