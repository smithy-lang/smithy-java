/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;

final class ShapeTranscoderTest {

    private static final Schema SOURCE_CHILD = Schema.structureBuilder(ShapeId.from("example.source#Child"))
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema SOURCE_LIST = Schema.listBuilder(ShapeId.from("example.source#Values"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
    private static final Schema SOURCE_MAP = Schema.mapBuilder(ShapeId.from("example.source#Tags"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema SOURCE = Schema.structureBuilder(ShapeId.from("example.source#Envelope"))
            .putMember("ignored", PreludeSchemas.STRING)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("number", PreludeSchemas.INTEGER)
            .putMember("child", SOURCE_CHILD)
            .putMember("values", SOURCE_LIST)
            .putMember("tags", SOURCE_MAP)
            .putMember("document", PreludeSchemas.DOCUMENT)
            .putMember("blob", PreludeSchemas.BLOB)
            .putMember("timestamp", PreludeSchemas.TIMESTAMP)
            .putMember("stream", PreludeSchemas.BLOB)
            .build();

    private static final Schema TARGET_CHILD = Schema.structureBuilder(ShapeId.from("example.target#Child"))
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema TARGET_LIST = Schema.listBuilder(ShapeId.from("example.target#Values"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
    private static final Schema TARGET_MAP = Schema.mapBuilder(ShapeId.from("example.target#Tags"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema TARGET = Schema.structureBuilder(ShapeId.from("example.target#Envelope"))
            .putMember("child", TARGET_CHILD)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("number", PreludeSchemas.LONG)
            .putMember("tags", TARGET_MAP)
            .putMember("values", TARGET_LIST)
            .putMember("document", PreludeSchemas.DOCUMENT)
            .putMember("blob", PreludeSchemas.BLOB)
            .putMember("timestamp", PreludeSchemas.TIMESTAMP)
            .putMember("stream", PreludeSchemas.BLOB)
            .putMember("targetOnly", PreludeSchemas.STRING)
            .build();
    private static final Schema EMPTY_SOURCE = Schema.structureBuilder(ShapeId.from("example.source#Empty")).build();
    private static final Schema REQUIRED_TARGET = Schema.structureBuilder(ShapeId.from("example.target#Required"))
            .putMember("required", PreludeSchemas.STRING)
            .build();

    @Test
    void convertsDistinctGeneratedShapeGraphsWithoutReadingMembers() {
        var document = Document.of(Map.of("key", Document.of("value")));
        var stream = DataStream.ofString("streaming");
        var timestamp = Instant.parse("2025-03-10T12:30:00Z");
        var source = new SourceEnvelope(
                "name",
                42,
                new SourceChild("nested"),
                List.of("one", "two"),
                mapWithNullValue(),
                document,
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                timestamp,
                stream);

        var result = ShapeTranscoder.convert(source, new TargetBuilder());

        assertEquals("name", result.name());
        assertEquals(42L, result.number());
        assertEquals(new TargetChild("nested"), result.child());
        assertEquals(List.of("one", "two"), result.values());
        assertEquals(mapWithNullValue(), result.tags());
        assertSame(document, result.document());
        assertEquals(ByteBuffer.wrap(new byte[] {1, 2, 3}), result.blob());
        assertEquals(timestamp, result.timestamp());
        assertSame(stream, result.stream());
        assertNull(result.targetOnly());
    }

    @Test
    void reusesATranscoderAcrossConversions() {
        var transcoder = new ShapeTranscoder();

        var first = transcoder.transcode(source("first"), new TargetBuilder());
        var second = transcoder.transcode(source("second"), new TargetBuilder());

        assertEquals("first", first.name());
        assertEquals("second", second.name());
    }

    @Test
    void strictlyConvertsMatchingShapeTypes() {
        var source = (SerializableShape) serializer -> serializer.writeString(PreludeSchemas.STRING, "value");

        var result = ShapeTranscoder.convertStrict(
                source,
                new ScalarBuilder(
                        PreludeSchemas.STRING,
                        deserializer -> deserializer.readString(PreludeSchemas.STRING)));

        assertEquals("value", result.value());
    }

    @Test
    void strictConversionAllowsLosslessNumericConversions() {
        var longValue = 9_007_199_254_740_993L;
        assertEquals(
                BigInteger.valueOf(longValue),
                transcodeScalarStrict(
                        serializer -> serializer.writeLong(PreludeSchemas.LONG, longValue),
                        PreludeSchemas.BIG_INTEGER,
                        deserializer -> deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER)));
        assertEquals(
                42L,
                transcodeScalarStrict(
                        serializer -> serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, BigInteger.valueOf(42)),
                        PreludeSchemas.LONG,
                        deserializer -> deserializer.readLong(PreludeSchemas.LONG)));
        assertEquals(
                1.5d,
                transcodeScalarStrict(
                        serializer -> serializer.writeFloat(PreludeSchemas.FLOAT, 1.5f),
                        PreludeSchemas.DOUBLE,
                        deserializer -> deserializer.readDouble(PreludeSchemas.DOUBLE)));
    }

    @Test
    void strictConversionRejectsLossyNumericConversions() {
        var exception = assertThrows(
                SerializationException.class,
                () -> ShapeTranscoder.convertStrict(
                        serializer -> serializer.writeLong(PreludeSchemas.LONG, 9_007_199_254_740_993L),
                        new ScalarBuilder(
                                PreludeSchemas.DOUBLE,
                                deserializer -> deserializer.readDouble(PreludeSchemas.DOUBLE))));

        assertEquals(
                "Strict conversion cannot losslessly convert long to double",
                exception.getMessage());
        assertThrows(
                SerializationException.class,
                () -> ShapeTranscoder.convertStrict(
                        serializer -> serializer.writeBigInteger(
                                PreludeSchemas.BIG_INTEGER,
                                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                        new ScalarBuilder(
                                PreludeSchemas.LONG,
                                deserializer -> deserializer.readLong(PreludeSchemas.LONG))));
        assertThrows(
                SerializationException.class,
                () -> ShapeTranscoder.convertStrict(
                        serializer -> serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, new BigDecimal("1.5")),
                        new ScalarBuilder(
                                PreludeSchemas.BIG_INTEGER,
                                deserializer -> deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER))));
        assertThrows(
                SerializationException.class,
                () -> ShapeTranscoder.convertStrict(
                        serializer -> serializer.writeDouble(PreludeSchemas.DOUBLE, 0.1d),
                        new ScalarBuilder(
                                PreludeSchemas.FLOAT,
                                deserializer -> deserializer.readFloat(PreludeSchemas.FLOAT))));
    }

    @Test
    void strictConversionRejectsUnrelatedShapeTypes() {
        assertThrows(
                SerializationException.class,
                () -> ShapeTranscoder.convertStrict(
                        serializer -> serializer.writeString(PreludeSchemas.STRING, "42"),
                        new ScalarBuilder(
                                PreludeSchemas.INTEGER,
                                deserializer -> deserializer.readInteger(PreludeSchemas.INTEGER))));
    }

    @Test
    void strictConversionChecksTypesForSchemaLessReaders() {
        var documentSource =
                (SerializableShape) serializer -> serializer.writeDocument(PreludeSchemas.DOCUMENT, Document.of("v"));
        var nullSource = (SerializableShape) serializer -> serializer.writeNull(PreludeSchemas.STRING);

        assertThrows(
                SerializationException.class,
                () -> ShapeTranscoder.convertStrict(
                        documentSource,
                        new ScalarBuilder(PreludeSchemas.STRING, ShapeDeserializer::readDocument)));
        assertThrows(
                SerializationException.class,
                () -> ShapeTranscoder.convertStrict(
                        nullSource,
                        new ScalarBuilder(PreludeSchemas.LONG, ShapeDeserializer::readNull)));
    }

    @Test
    void strictConversionDropsUnknownSourceMembers() {
        var result = ShapeTranscoder.convertStrict(source("value"), new TargetBuilder());

        assertEquals("value", result.name());
        assertNull(result.targetOnly());
    }

    @Test
    void strictConversionDoesNotCorrectMissingRequiredTargetMembers() {
        assertEquals("corrected", ShapeTranscoder.convert(new EmptySource(), new RequiredBuilder()).value());
        assertThrows(
                IllegalStateException.class,
                () -> ShapeTranscoder.convertStrict(new EmptySource(), new RequiredBuilder()));
    }

    @Test
    void errorCorrectsByDefaultButStrictConversionBuildsDirectly() {
        var source = (SerializableShape) serializer -> serializer.writeString(PreludeSchemas.STRING, "value");

        assertEquals("corrected", ShapeTranscoder.convert(source, new CorrectingBuilder()).value());
        assertEquals("value", ShapeTranscoder.convertStrict(source, new CorrectingBuilder()).value());
    }

    @Test
    void switchesModesWhenReusedAfterStrictFailure() {
        var transcoder = new ShapeTranscoder();
        var longSource = (SerializableShape) serializer -> serializer.writeLong(PreludeSchemas.LONG, 256);

        assertThrows(
                SerializationException.class,
                () -> transcoder.transcodeStrict(
                        longSource,
                        new ScalarBuilder(
                                PreludeSchemas.BYTE,
                                deserializer -> deserializer.readByte(PreludeSchemas.BYTE))));

        var coerced = transcoder.transcode(
                longSource,
                new ScalarBuilder(
                        PreludeSchemas.BYTE,
                        deserializer -> deserializer.readByte(PreludeSchemas.BYTE)));
        assertEquals((byte) 0, coerced.value());

        var strict = transcoder.transcodeStrict(
                longSource,
                new ScalarBuilder(
                        PreludeSchemas.BIG_INTEGER,
                        deserializer -> deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER)));
        assertEquals(BigInteger.valueOf(256), strict.value());
    }

    @Test
    void sharesMemberMappingsThroughTheSourceSchema() {
        var first = ShapeTranscoderSchemaExtensions.mapping(SOURCE, TARGET);
        var second = ShapeTranscoderSchemaExtensions.mapping(SOURCE, TARGET);

        assertSame(first, second);
        assertSame(TARGET.member("name"), first.targetMember(SOURCE.member("name")));
        assertNotNull(SOURCE.getExtension(ShapeTranscoderSchemaExtensions.KEY));
        assertNull(PreludeSchemas.STRING.getExtension(ShapeTranscoderSchemaExtensions.KEY));
    }

    @Test
    void safelyCreatesAndUsesMappingsConcurrently() throws Exception {
        var source = Schema.structureBuilder(ShapeId.from("concurrent.source#Struct"))
                .putMember("value", PreludeSchemas.STRING)
                .build();
        var target = Schema.structureBuilder(ShapeId.from("concurrent.target#Struct"))
                .putMember("value", PreludeSchemas.STRING)
                .build();
        var sourceMember = source.member("value");
        var targetMember = target.member("value");
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<Schema>>();
            for (var i = 0; i < 64; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return ShapeTranscoderSchemaExtensions.mapping(source, target).targetMember(sourceMember);
                }));
            }
            start.countDown();
            for (var result : results) {
                assertSame(targetMember, result.get());
            }
        }

        assertSame(
                ShapeTranscoderSchemaExtensions.mapping(source, target),
                ShapeTranscoderSchemaExtensions.mapping(source, target));
    }

    @Test
    void remainsCorrectWhenTheBoundedMappingCacheEvictsEntries() {
        var source = Schema.structureBuilder(ShapeId.from("eviction.source#Struct"))
                .putMember("value", PreludeSchemas.STRING)
                .build();
        var sourceMember = source.member("value");
        var targets = new ArrayList<Schema>();

        for (var i = 0; i < 64; i++) {
            var target = Schema.structureBuilder(ShapeId.from("eviction.target#Struct" + i))
                    .putMember("value", PreludeSchemas.STRING)
                    .build();
            targets.add(target);
            assertSame(
                    target.member("value"),
                    ShapeTranscoderSchemaExtensions.mapping(source, target).targetMember(sourceMember));
        }

        for (var target : targets) {
            assertSame(
                    target.member("value"),
                    ShapeTranscoderSchemaExtensions.mapping(source, target).targetMember(sourceMember));
        }
    }

    @Test
    void forwardsScalarValues() {
        assertEquals(
                true,
                transcodeScalar(
                        serializer -> serializer.writeBoolean(PreludeSchemas.BOOLEAN, true),
                        PreludeSchemas.BOOLEAN,
                        deserializer -> deserializer.readBoolean(PreludeSchemas.BOOLEAN)));
        assertEquals(
                (byte) 1,
                transcodeScalar(
                        serializer -> serializer.writeByte(PreludeSchemas.BYTE, (byte) 1),
                        PreludeSchemas.BYTE,
                        deserializer -> deserializer.readByte(PreludeSchemas.BYTE)));
        assertEquals(
                (short) 2,
                transcodeScalar(
                        serializer -> serializer.writeShort(PreludeSchemas.SHORT, (short) 2),
                        PreludeSchemas.SHORT,
                        deserializer -> deserializer.readShort(PreludeSchemas.SHORT)));
        assertEquals(
                3L,
                transcodeScalar(
                        serializer -> serializer.writeLong(PreludeSchemas.LONG, 3),
                        PreludeSchemas.LONG,
                        deserializer -> deserializer.readLong(PreludeSchemas.LONG)));
        assertEquals(
                4.5f,
                transcodeScalar(
                        serializer -> serializer.writeFloat(PreludeSchemas.FLOAT, 4.5f),
                        PreludeSchemas.FLOAT,
                        deserializer -> deserializer.readFloat(PreludeSchemas.FLOAT)));
        assertEquals(
                5.5d,
                transcodeScalar(
                        serializer -> serializer.writeDouble(PreludeSchemas.DOUBLE, 5.5d),
                        PreludeSchemas.DOUBLE,
                        deserializer -> deserializer.readDouble(PreludeSchemas.DOUBLE)));

        var bigInteger = new BigInteger("12345678901234567890");
        assertSame(
                bigInteger,
                transcodeScalar(
                        serializer -> serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, bigInteger),
                        PreludeSchemas.BIG_INTEGER,
                        deserializer -> deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER)));

        var bigDecimal = new BigDecimal("1234567890.123456789");
        assertSame(
                bigDecimal,
                transcodeScalar(
                        serializer -> serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, bigDecimal),
                        PreludeSchemas.BIG_DECIMAL,
                        deserializer -> deserializer.readBigDecimal(PreludeSchemas.BIG_DECIMAL)));

        EventStream<SourceChild> eventStream = EventStream.newWriter();
        assertSame(
                eventStream,
                transcodeScalar(
                        serializer -> serializer.writeEventStream(PreludeSchemas.DOCUMENT, eventStream),
                        PreludeSchemas.DOCUMENT,
                        deserializer -> deserializer.readEventStream(PreludeSchemas.DOCUMENT)));

        assertNull(transcodeScalar(
                serializer -> serializer.writeNull(PreludeSchemas.STRING),
                PreludeSchemas.STRING,
                ShapeDeserializer::readNull));
    }

    @Test
    void preservesPrecisionWhenCoercingArbitraryPrecisionNumbers() {
        var longValue = 9_007_199_254_740_993L;
        assertEquals(
                BigDecimal.valueOf(longValue),
                transcodeScalar(
                        serializer -> serializer.writeLong(PreludeSchemas.LONG, longValue),
                        PreludeSchemas.BIG_DECIMAL,
                        deserializer -> deserializer.readBigDecimal(PreludeSchemas.BIG_DECIMAL)));

        var bigInteger = new BigInteger("123456789012345678901234567890");
        assertEquals(
                new BigDecimal(bigInteger),
                transcodeScalar(
                        serializer -> serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, bigInteger),
                        PreludeSchemas.BIG_DECIMAL,
                        deserializer -> deserializer.readBigDecimal(PreludeSchemas.BIG_DECIMAL)));

        var bigDecimal = new BigDecimal("123456789012345678901234567890.987654321");
        assertEquals(
                bigDecimal.toBigInteger(),
                transcodeScalar(
                        serializer -> serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, bigDecimal),
                        PreludeSchemas.BIG_INTEGER,
                        deserializer -> deserializer.readBigInteger(PreludeSchemas.BIG_INTEGER)));
    }

    @Test
    void canBeReusedAfterFailedConversion() {
        var transcoder = new ShapeTranscoder();
        var stringSource = (SerializableShape) serializer -> serializer.writeString(PreludeSchemas.STRING, "value");

        assertThrows(
                SerializationException.class,
                () -> transcoder.transcode(
                        stringSource,
                        new ScalarBuilder(
                                PreludeSchemas.BOOLEAN,
                                deserializer -> deserializer.readBoolean(PreludeSchemas.BOOLEAN))));

        var result = transcoder.transcode(
                stringSource,
                new ScalarBuilder(
                        PreludeSchemas.STRING,
                        deserializer -> deserializer.readString(PreludeSchemas.STRING)));
        assertEquals("value", result.value());
    }

    private static Object transcodeScalar(
            SerializableShape source,
            Schema targetSchema,
            Function<ShapeDeserializer, Object> reader
    ) {
        return ShapeTranscoder.convert(source, new ScalarBuilder(targetSchema, reader)).value();
    }

    private static Object transcodeScalarStrict(
            SerializableShape source,
            Schema targetSchema,
            Function<ShapeDeserializer, Object> reader
    ) {
        return ShapeTranscoder.convertStrict(source, new ScalarBuilder(targetSchema, reader)).value();
    }

    private static SourceEnvelope source(String name) {
        return new SourceEnvelope(
                name,
                1,
                new SourceChild("child"),
                List.of(),
                Map.of(),
                Document.of("document"),
                ByteBuffer.allocate(0),
                Instant.EPOCH,
                DataStream.ofString(""));
    }

    private static Map<String, String> mapWithNullValue() {
        var result = new LinkedHashMap<String, String>();
        result.put("present", "value");
        result.put("missing", null);
        return result;
    }

    private record SourceChild(String value) implements SerializableStruct {
        @Override
        public Schema schema() {
            return SOURCE_CHILD;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE_CHILD.member("value"), value);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new AssertionError("ShapeTranscoder must not call getMemberValue");
        }
    }

    private record SourceEnvelope(
            String name,
            int number,
            SourceChild child,
            List<String> values,
            Map<String, String> tags,
            Document document,
            ByteBuffer blob,
            Instant timestamp,
            DataStream stream) implements SerializableStruct {
        @Override
        public Schema schema() {
            return SOURCE;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE.member("ignored"), "ignored");
            serializer.writeString(SOURCE.member("name"), name);
            serializer.writeInteger(SOURCE.member("number"), number);
            serializer.writeStruct(SOURCE.member("child"), child);
            serializer.writeList(SOURCE.member("values"), values, values.size(), SourceEnvelope::writeValues);
            serializer.writeMap(SOURCE.member("tags"), tags, tags.size(), SourceEnvelope::writeTags);
            serializer.writeDocument(SOURCE.member("document"), document);
            serializer.writeBlob(SOURCE.member("blob"), blob);
            serializer.writeTimestamp(SOURCE.member("timestamp"), timestamp);
            serializer.writeDataStream(SOURCE.member("stream"), stream);
        }

        private static void writeValues(List<String> values, ShapeSerializer serializer) {
            for (var value : values) {
                if (value == null) {
                    serializer.writeNull(SOURCE_LIST.listMember());
                } else {
                    serializer.writeString(SOURCE_LIST.listMember(), value);
                }
            }
        }

        private static void writeTags(Map<String, String> tags, MapSerializer serializer) {
            for (var entry : tags.entrySet()) {
                serializer.writeEntry(
                        SOURCE_MAP.mapKeyMember(),
                        entry.getKey(),
                        entry.getValue(),
                        SourceEnvelope::writeTag);
            }
        }

        private static void writeTag(String value, ShapeSerializer serializer) {
            if (value == null) {
                serializer.writeNull(SOURCE_MAP.mapValueMember());
            } else {
                serializer.writeString(SOURCE_MAP.mapValueMember(), value);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new AssertionError("ShapeTranscoder must not call getMemberValue");
        }
    }

    private record TargetChild(String value) {}

    private record ScalarResult(Object value) implements SerializableShape {
        @Override
        public void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptySource implements SerializableStruct {
        @Override
        public Schema schema() {
            return EMPTY_SOURCE;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new IllegalArgumentException("Empty source has no members");
        }
    }

    private record RequiredResult(String value) implements SerializableShape {
        @Override
        public void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RequiredBuilder implements ShapeBuilder<RequiredResult> {
        private String value;

        @Override
        public RequiredResult build() {
            if (value == null) {
                throw new IllegalStateException("required member is missing");
            }
            return new RequiredResult(value);
        }

        @Override
        public ShapeBuilder<RequiredResult> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(
                    REQUIRED_TARGET,
                    this,
                    (builder, member, value) -> builder.value = value.readString(member));
            return this;
        }

        @Override
        public ShapeBuilder<RequiredResult> errorCorrection() {
            value = "corrected";
            return this;
        }

        @Override
        public Schema schema() {
            return REQUIRED_TARGET;
        }
    }

    private static final class ScalarBuilder implements ShapeBuilder<ScalarResult> {
        private final Schema schema;
        private final Function<ShapeDeserializer, Object> reader;
        private Object value;

        private ScalarBuilder(Schema schema, Function<ShapeDeserializer, Object> reader) {
            this.schema = schema;
            this.reader = reader;
        }

        @Override
        public ScalarResult build() {
            return new ScalarResult(value);
        }

        @Override
        public ShapeBuilder<ScalarResult> deserialize(ShapeDeserializer decoder) {
            value = reader.apply(decoder);
            return this;
        }

        @Override
        public Schema schema() {
            return schema;
        }
    }

    private static final class CorrectingBuilder implements ShapeBuilder<ScalarResult> {
        private String value;

        @Override
        public ScalarResult build() {
            return new ScalarResult(value);
        }

        @Override
        public ShapeBuilder<ScalarResult> deserialize(ShapeDeserializer decoder) {
            value = decoder.readString(PreludeSchemas.STRING);
            return this;
        }

        @Override
        public ShapeBuilder<ScalarResult> errorCorrection() {
            value = "corrected";
            return this;
        }

        @Override
        public Schema schema() {
            return PreludeSchemas.STRING;
        }
    }

    private record TargetEnvelope(
            String name,
            long number,
            TargetChild child,
            List<String> values,
            Map<String, String> tags,
            Document document,
            ByteBuffer blob,
            Instant timestamp,
            DataStream stream,
            String targetOnly) implements SerializableShape {
        @Override
        public void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TargetBuilder implements ShapeBuilder<TargetEnvelope> {
        private String name;
        private long number;
        private TargetChild child;
        private List<String> values;
        private Map<String, String> tags;
        private Document document;
        private ByteBuffer blob;
        private Instant timestamp;
        private DataStream stream;
        private String targetOnly;

        @Override
        public TargetEnvelope build() {
            return new TargetEnvelope(
                    name,
                    number,
                    child,
                    values,
                    tags,
                    document,
                    blob,
                    timestamp,
                    stream,
                    targetOnly);
        }

        @Override
        public ShapeBuilder<TargetEnvelope> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(TARGET, this, (builder, member, value) -> {
                switch (member.memberName()) {
                    case "name" -> builder.name = value.readString(member);
                    case "number" -> builder.number = value.readLong(member);
                    case "child" -> builder.child = readChild(value);
                    case "values" -> builder.values = readValues(value);
                    case "tags" -> builder.tags = readTags(value);
                    case "document" -> builder.document = value.readDocument();
                    case "blob" -> builder.blob = value.readBlob(member);
                    case "timestamp" -> builder.timestamp = value.readTimestamp(member);
                    case "stream" -> builder.stream = value.readDataStream(member);
                    default -> throw new IllegalArgumentException("Unexpected member " + member);
                }
            });
            return this;
        }

        private static TargetChild readChild(ShapeDeserializer deserializer) {
            var value = new String[1];
            deserializer.readStruct(TARGET_CHILD, value, (state, member, child) -> {
                state[0] = child.readString(member);
            });
            return new TargetChild(value[0]);
        }

        private static List<String> readValues(ShapeDeserializer deserializer) {
            var result = new ArrayList<String>();
            deserializer.readList(TARGET_LIST, result, (values, value) -> {
                values.add(value.isNull() ? value.readNull() : value.readString(TARGET_LIST.listMember()));
            });
            return result;
        }

        private static Map<String, String> readTags(ShapeDeserializer deserializer) {
            var result = new LinkedHashMap<String, String>();
            deserializer.readStringMap(TARGET_MAP, result, (tags, key, value) -> {
                tags.put(key, value.isNull() ? value.readNull() : value.readString(TARGET_MAP.mapValueMember()));
            });
            return result;
        }

        @Override
        public Schema schema() {
            return TARGET;
        }
    }
}
