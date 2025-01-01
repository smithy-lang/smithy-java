/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.shapes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ListSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

final class SdkJsonDocumentParser implements ShapeSerializer {

    static final JsonSettings REST_JSON = JsonSettings.builder().useJsonName(true).useTimestampFormat(true).build();
    static final JsonSettings JSON = JsonSettings.builder().build();

    /**
     * Holds the converters found on the classpath via SPI.
     */
    static final Map<ShapeId, DocumentConverter> CONVERTERS = new HashMap<>();

    static {
        for (var impl : ServiceLoader.load(DocumentConverter.class)) {
            CONVERTERS.put(impl.protocol(), impl);
        }
    }

    private software.amazon.awssdk.core.document.Document result;
    private final JsonSettings settings;

    SdkJsonDocumentParser(JsonSettings settings) {
        this.settings = settings;
    }

    static software.amazon.awssdk.core.document.Document writeJsonDocument(
            Document document,
            JsonSettings settings
    ) {
        if (document == null) {
            return software.amazon.awssdk.core.document.Document.fromNull();
        } else {
            var parser = new SdkJsonDocumentParser(settings);
            document.serializeContents(parser);
            return parser.getResult();
        }
    }

    software.amazon.awssdk.core.document.Document getResult() {
        return result;
    }

    private void setResult(software.amazon.awssdk.core.document.Document result) {
        this.result = result;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        var parser = new StructureParser(settings);
        struct.serializeMembers(parser);
        setResult(software.amazon.awssdk.core.document.Document.fromMap(parser.members));
    }

    static final class StructureParser extends InterceptingSerializer {
        private final SdkJsonDocumentParser parser;
        private final Map<String, software.amazon.awssdk.core.document.Document> members = new LinkedHashMap<>();

        StructureParser(JsonSettings settings) {
            this.parser = new SdkJsonDocumentParser(settings);
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            return parser;
        }

        @Override
        protected void after(Schema schema) {
            // Use JSON name here to ensure the SDK serializes it correctly.
            var name = parser.settings.fieldMapper().memberToField(schema);
            members.put(name, parser.getResult());
        }
    }

    @Override
    public <T extends List<?>> void writeList(
            Schema schema,
            T state,
            int size,
            BiConsumer<T, ShapeSerializer> consumer
    ) {
        List<software.amazon.awssdk.core.document.Document> elements = size == -1
                ? new ArrayList<>()
                : new ArrayList<>(size);
        var elementParser = new SdkJsonDocumentParser(settings);
        ListSerializer serializer = new ListSerializer(elementParser, position -> {
            if (position > 0) {
                elements.add(elementParser.result);
                elementParser.result = null;
            }
        });
        consumer.accept(state, serializer);
        elements.add(elementParser.result);
        setResult(software.amazon.awssdk.core.document.Document.fromList(elements));
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        var keyMember = schema.mapKeyMember();
        if (keyMember.type() == ShapeType.STRING || keyMember.type() == ShapeType.ENUM) {
            var serializer = new DocumentMapSerializer(settings, size);
            consumer.accept(state, serializer);
            setResult(software.amazon.awssdk.core.document.Document.fromMap(serializer.entries));
        } else {
            throw new SerializationException("Unexpected map key schema: " + schema);
        }
    }

    private static final class DocumentMapSerializer implements MapSerializer {
        private final JsonSettings settings;
        private final Map<String, software.amazon.awssdk.core.document.Document> entries;

        DocumentMapSerializer(JsonSettings settings, int size) {
            this.settings = settings;
            entries = size == -1 ? new LinkedHashMap<>() : new LinkedHashMap<>(size);
        }

        @Override
        public <U> void writeEntry(
                Schema keySchema,
                String key,
                U keyState,
                BiConsumer<U, ShapeSerializer> valueSerializer
        ) {
            var valueParser = new SdkJsonDocumentParser(settings);
            valueSerializer.accept(keyState, valueParser);
            entries.put(key, valueParser.result);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        setResult(software.amazon.awssdk.core.document.Document.fromBoolean(value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        setResult(software.amazon.awssdk.core.document.Document.fromNumber(value));
    }

    @Override
    public void writeString(Schema schema, String value) {
        setResult(software.amazon.awssdk.core.document.Document.fromString(value));
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        writeString(schema, ByteBufferUtils.base64Encode(value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        var format = settings.timestampResolver().resolve(schema);
        // This will perform actually writing a string, number, etc.
        format.writeToSerializer(schema, value, this);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        value.serializeContents(this);
    }

    @Override
    public void writeNull(Schema schema) {
        setResult(software.amazon.awssdk.core.document.Document.fromNull());
    }
}
