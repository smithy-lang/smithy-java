/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.cbor.bench.model.AcronymStruct;
import software.amazon.smithy.java.cbor.bench.model.BenchError;
import software.amazon.smithy.java.cbor.bench.model.BenchUnion;
import software.amazon.smithy.java.cbor.bench.model.Color;
import software.amazon.smithy.java.cbor.bench.model.ComplexStruct;
import software.amazon.smithy.java.cbor.bench.model.InnerStruct;
import software.amazon.smithy.java.cbor.bench.model.NestedStruct;
import software.amazon.smithy.java.cbor.bench.model.Priority;
import software.amazon.smithy.java.cbor.bench.model.RecursiveStruct;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;

public final class RuntimeCborCodegenTest {
    @BeforeAll
    static void requireRuntimeCodegen() {
        org.junit.jupiter.api.Assumptions.assumeTrue(RuntimeCodegenFeature.available());
    }

    @Test
    void directlyRoundTripsScalarStructure() {
        var serde = new SmithyGeneratedCborSerde();
        var value = ScalarStruct.builder()
                .name("test-\u00e9")
                .count(42)
                .enabled(true)
                .score(1.5)
                .build();

        ByteBuffer encoded = serde.serialize(value, CborSettings.defaultSettings());
        assertThat(encoded).isNotNull();
        byte[] bytes = new byte[encoded.remaining()];
        encoded.duplicate().get(bytes);
        ByteBuffer dispatch = new DefaultCborSerdeProvider().serialize(value, CborSettings.defaultSettings());
        byte[] dispatchBytes = new byte[dispatch.remaining()];
        dispatch.get(dispatchBytes);

        assertThat(bytes).isEqualTo(dispatchBytes);
        assertThat(serde.deserialize(bytes, ScalarStruct.builder(), CborSettings.defaultSettings()))
                .isEqualTo(value);
    }

    @Test
    void integratesOnlyWhenFeatureGateIsEnabled() {
        var codec = Rpcv2CborCodec.builder().runtimeCodegen(true).build();
        var value = ScalarStruct.builder().name("value").count(7).build();
        ByteBuffer encoded = codec.serialize(value);
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);

        assertThat(codec.deserializeShape(bytes, ScalarStruct.builder())).isEqualTo(value);
    }

    @Test
    void deserializesByteBufferSlicesWithoutChangingTheirPosition() {
        var codec = Rpcv2CborCodec.builder().runtimeCodegen(true).build();
        var value = ScalarStruct.builder().name("value").count(7).build();
        byte[] encoded = remainingBytes(codec.serialize(value));
        byte[] framed = new byte[encoded.length + 4];
        System.arraycopy(encoded, 0, framed, 2, encoded.length);

        ByteBuffer arrayBacked = ByteBuffer.wrap(framed);
        arrayBacked.position(2).limit(2 + encoded.length);
        assertThat(codec.deserializeShape(arrayBacked, ScalarStruct.builder())).isEqualTo(value);
        assertThat(arrayBacked.position()).isEqualTo(2);

        ByteBuffer direct = ByteBuffer.allocateDirect(framed.length);
        direct.put(framed).position(2).limit(2 + encoded.length);
        assertThat(codec.deserializeShape(direct, ScalarStruct.builder())).isEqualTo(value);
        assertThat(direct.position()).isEqualTo(2);
    }

    @Test
    void directlyMatchesIndefiniteLengthFieldNames() {
        var generated = new SmithyGeneratedCborSerde();
        byte[] payload = {
                (byte) 0xa1,
                0x7f,
                0x62,
                'n',
                'a',
                0x62,
                'm',
                'e',
                (byte) 0xff,
                0x65,
                'v',
                'a',
                'l',
                'u',
                'e'
        };

        ScalarStruct result = generated.deserialize(
                payload,
                ScalarStruct.builder(),
                CborSettings.defaultSettings());

        assertThat(result.getName()).isEqualTo("value");
        assertThat(result).isEqualTo(dispatch(payload, ScalarStruct.builder()));
    }

    @Test
    void roundTripsAggregateStructure() {
        var serde = new SmithyGeneratedCborSerde();
        var value = AggregateStruct.builder().values(List.of("a", "b")).build();
        ByteBuffer encoded = serde.serialize(value, CborSettings.defaultSettings());
        assertThat(encoded).isNotNull();
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);

        assertThat(serde.deserialize(bytes, AggregateStruct.builder(), CborSettings.defaultSettings()))
                .usingRecursiveComparison()
                .isEqualTo(value);
    }

    @Test
    void generatedAndDispatchHaveComplexGraphParity() {
        var inner = InnerStruct.builder().value("inner").numbers(List.of(1, 2, 3)).build();
        var nested = NestedStruct.builder().field1("nested").field2(2).inner(inner).build();
        var sparseMap = new HashMap<String, String>();
        sparseMap.put("present", "value");
        sparseMap.put("null", null);
        var value = ComplexStruct.builder()
                .id("id")
                .count(1)
                .enabled(true)
                .ratio(1.5)
                .score(2.5f)
                .bigCount(99)
                .optionalString("optional")
                .optionalInt(7)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .payload(ByteBuffer.wrap(new byte[] {0, 1, -1}))
                .tags(List.of("a", "b"))
                .intList(List.of(1, 2))
                .metadata(Map.of("key", "value"))
                .intMap(Map.of("n", 3))
                .nested(nested)
                .optionalNested(nested)
                .structList(List.of(nested, nested))
                .structMap(Map.of("nested", nested))
                .choice(new BenchUnion.StructValueMember(nested))
                .color(Color.GREEN)
                .colorList(List.of(Color.RED, Color.BLUE))
                .sparseStrings(Arrays.asList("a", null, "b"))
                .sparseMap(sparseMap)
                .bigIntValue(new BigInteger("12345678901234567890"))
                .bigDecValue(new BigDecimal("1234.50"))
                .freeformData(Document.of(Map.of("key", Document.of("value"))))
                .build();
        var settings = CborSettings.defaultSettings();
        var generated = new SmithyGeneratedCborSerde();

        ByteBuffer generatedBuffer = generated.serialize(value, settings);
        assertThat(generatedBuffer).isNotNull();
        byte[] generatedBytes = remainingBytes(generatedBuffer);
        byte[] dispatchBytes = remainingBytes(new DefaultCborSerdeProvider().serialize(value, settings));

        assertThat(generated.deserialize(dispatchBytes, ComplexStruct.builder(), settings)).isEqualTo(value);
        assertThat(Rpcv2CborCodec.builder()
                .runtimeCodegen(false)
                .build()
                .deserializeShape(generatedBytes, ComplexStruct.builder()))
                .isEqualTo(value);
    }

    @Test
    void hasParityOnMembersWhoseAccessorsFoldAcronyms() {
        var value = AcronymStruct.builder()
                .acl("private")
                .ssekmsKeyId("key-id")
                .grantReadacP("grantee")
                .eTag("\"etag\"")
                .checksumcrC32("crc")
                .bucketKeyEnabled(true)
                .tags(List.of("a", "b"))
                .build();
        var settings = CborSettings.defaultSettings();
        var generated = new SmithyGeneratedCborSerde();

        byte[] generatedBytes = remainingBytes(generated.serialize(value, settings));
        byte[] dispatchBytes = remainingBytes(new DefaultCborSerdeProvider().serialize(value, settings));

        assertThat(generatedBytes).isEqualTo(dispatchBytes);
        assertThat(generated.deserialize(dispatchBytes, AcronymStruct.builder(), settings)).isEqualTo(value);
        assertThat(Rpcv2CborCodec.builder()
                .runtimeCodegen(false)
                .build()
                .deserializeShape(generatedBytes, AcronymStruct.builder()))
                .isEqualTo(value);
    }

    @Test
    void roundTripsRecursiveStructuresAndEveryUnionVariant() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();
        var recursive = RecursiveStruct.builder()
                .value("root")
                .child(RecursiveStruct.builder().value("leaf").build())
                .build();

        assertGeneratedRoundTrip(generated, recursive, RecursiveStruct::builder, settings);
        assertGeneratedRoundTrip(
                generated,
                new BenchUnion.StringValueMember("value"),
                BenchUnion::builder,
                settings);
        assertGeneratedRoundTrip(
                generated,
                new BenchUnion.IntValueMember(42),
                BenchUnion::builder,
                settings);
        assertGeneratedRoundTrip(
                generated,
                new BenchUnion.StructValueMember(
                        NestedStruct.builder().field1("nested").field2(2).build()),
                BenchUnion::builder,
                settings);
    }

    @Test
    void readsNestedUnionsEncodedWithStructureSchemas() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();
        var value = StructureUnionEnvelope.builder()
                .value(new StructureEncodedValue.SMember("value"))
                .build();

        assertGeneratedRoundTrip(generated, value, StructureUnionEnvelope::builder, settings);
    }

    @Test
    void preservesUnknownRootUnionMembers() {
        var generated = new SmithyGeneratedCborSerde();
        byte[] payload = encodeDocument(Document.of(Map.of("future", Document.of("value"))));

        BenchUnion result = generated.deserialize(
                payload,
                BenchUnion.builder(),
                CborSettings.defaultSettings());

        assertThat(result).isInstanceOf(BenchUnion.$Unknown.class);
        assertThat((String) result.getValue()).isEqualTo("future");
    }

    @Test
    void treatsNullUnionMembersAsAbsent() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();
        var expected = new BenchUnion.IntValueMember(42);

        for (byte[] union : List.of(
                cborMap("stringValue", null, "intValue", 42),
                cborMap("intValue", 42, "stringValue", null),
                cborMap("structValue", null, "intValue", 42, "future", null))) {
            assertThat(generated.deserialize(union, BenchUnion.builder(), settings)).isEqualTo(expected);
            assertThat(generated.deserialize(union, BenchUnion.builder(), settings))
                    .isEqualTo(dispatch(union, BenchUnion.builder()));

            byte[] nested = wrapChoice(union);
            assertThat(generated.deserialize(nested, ComplexStruct.builder(), settings).getChoice())
                    .isEqualTo(expected);
            assertThat(generated.deserialize(nested, ComplexStruct.builder(), settings).getChoice())
                    .isEqualTo(dispatch(nested, ComplexStruct.builder()).getChoice());
        }
    }

    @Test
    void rejectsUnionsCarryingNoMemberOrMoreThanOne() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();

        for (byte[] union : List.of(
                cborMap(),
                cborMap("stringValue", null),
                cborMap("future", null),
                cborMap("stringValue", "a", "intValue", 1),
                cborMap("stringValue", "a", "future", 1),
                cborMap("future", 1, "stringValue", "a"))) {
            assertThatThrownBy(() -> generated.deserialize(union, BenchUnion.builder(), settings))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> dispatch(union, BenchUnion.builder()))
                    .isInstanceOf(RuntimeException.class);

            byte[] nested = wrapChoice(union);
            assertThatThrownBy(() -> generated.deserialize(nested, ComplexStruct.builder(), settings))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> dispatch(nested, ComplexStruct.builder()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void preservesUnknownUnionMembersAtMemberPositions() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();
        byte[] payload = wrapChoice(cborMap("future", cborMap("nested", 1)));

        BenchUnion choice = generated.deserialize(payload, ComplexStruct.builder(), settings).getChoice();

        assertThat(choice).isInstanceOf(BenchUnion.$Unknown.class);
        assertThat((String) choice.getValue()).isEqualTo("future");
        assertThat(choice).isEqualTo(dispatch(payload, ComplexStruct.builder()).getChoice());
    }

    @Test
    void treatsNullMembersAsAbsentLikeDispatch() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();
        byte[] payload = cborMap(
                "id",
                "id",
                "count",
                1,
                "enabled",
                null,
                "ratio",
                null,
                "score",
                null,
                "bigCount",
                null,
                "optionalInt",
                null,
                "future",
                null,
                "nested",
                cborMap("field1", "n", "field2", 2));

        ComplexStruct result = generated.deserialize(payload, ComplexStruct.builder(), settings);

        assertThat(result).isEqualTo(dispatch(payload, ComplexStruct.builder()));
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getBigCount()).isZero();
        assertThat(result.getOptionalInt()).isNull();
    }

    @Test
    void hasIntEnumParityAtEverySite() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();
        var nested = NestedStruct.builder().field1("n").field2(2).build();

        for (Priority priority : Priority.values()) {
            var value = ComplexStruct.builder()
                    .id("id")
                    .count(1)
                    .nested(nested)
                    .priority(priority)
                    .priorityList(List.of(priority, Priority.ZERO))
                    .priorityMap(Map.of("p", priority))
                    .build();

            byte[] generatedBytes = remainingBytes(generated.serialize(value, settings));
            byte[] dispatchBytes = remainingBytes(new DefaultCborSerdeProvider().serialize(value, settings));

            assertThat(generated.deserialize(dispatchBytes, ComplexStruct.builder(), settings)).isEqualTo(value);
            assertThat(dispatch(generatedBytes, ComplexStruct.builder())).isEqualTo(value);
            assertThat(generated.deserialize(generatedBytes, ComplexStruct.builder(), settings)).isEqualTo(value);
        }
    }

    @Test
    void readsUnknownIntEnumValuesLikeDispatch() {
        var generated = new SmithyGeneratedCborSerde();
        var settings = CborSettings.defaultSettings();
        byte[] payload = cborMap(
                "id",
                "id",
                "count",
                1,
                "nested",
                cborMap("field1", "n", "field2", 2),
                "priority",
                77,
                "priorityList",
                cborArray(77, -77),
                "priorityMap",
                cborMap("p", 77));

        ComplexStruct result = generated.deserialize(payload, ComplexStruct.builder(), settings);

        assertThat(result).isEqualTo(dispatch(payload, ComplexStruct.builder()));
        assertThat(result.getPriority()).isEqualTo(Priority.unknown(77));
        assertThat(result.getPriorityList()).containsExactly(Priority.unknown(77), Priority.unknown(-77));
        assertThat(result.getPriorityMap()).containsEntry("p", Priority.unknown(77));
    }

    @Test
    void refusesToGenerateSerdeForErrorShapes() {
        withProperty("smithy-java.runtime-codegen.cbor", "enabled", () -> {
            var generated = new SmithyGeneratedCborSerde();
            var settings = CborSettings.defaultSettings();
            var value = BenchError.builder().message("boom").code(7).build();
            byte[] payload = remainingBytes(new DefaultCborSerdeProvider().serialize(value, settings));

            assertThat(generated.serialize(value, settings)).isNull();
            assertThat(generated.deserialize(payload, BenchError.builder(), settings)).isNull();

            BenchError result = Rpcv2CborCodec.builder()
                    .runtimeCodegen(true)
                    .build()
                    .deserializeShape(payload, BenchError.builder());
            assertThat(result.getMessage()).isEqualTo("boom");
            assertThat(result.getCode()).isEqualTo(7);
            assertThat(result.deserialized()).isTrue();
        });
    }

    private static <T extends SerializableShape> T dispatch(byte[] payload, ShapeBuilder<T> builder) {
        return Rpcv2CborCodec.builder().runtimeCodegen(false).build().deserializeShape(payload, builder);
    }

    private static byte[] wrapChoice(byte[] union) {
        return cborMap(
                "id",
                "id",
                "count",
                1,
                "nested",
                cborMap("field1", "n", "field2", 2),
                "choice",
                union);
    }

    private static byte[] cborMap(Object... pairs) {
        var out = new ByteArrayOutputStream();
        writeHeader(out, 0xA0, pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            writeValue(out, pairs[i]);
            writeValue(out, pairs[i + 1]);
        }
        return out.toByteArray();
    }

    private static byte[] cborArray(Object... items) {
        var out = new ByteArrayOutputStream();
        writeHeader(out, 0x80, items.length);
        for (Object item : items) {
            writeValue(out, item);
        }
        return out.toByteArray();
    }

    private static void writeValue(ByteArrayOutputStream out, Object value) {
        if (value == null) {
            out.write(0xF6);
        } else if (value instanceof byte[] encoded) {
            out.writeBytes(encoded);
        } else if (value instanceof String text) {
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            writeHeader(out, 0x60, utf8.length);
            out.writeBytes(utf8);
        } else if (value instanceof Integer number) {
            if (number >= 0) {
                writeHeader(out, 0x00, number);
            } else {
                writeHeader(out, 0x20, -1L - number);
            }
        } else {
            throw new IllegalArgumentException("Unsupported test value: " + value);
        }
    }

    private static void writeHeader(ByteArrayOutputStream out, int major, long value) {
        if (value < 24) {
            out.write(major | (int) value);
        } else if (value < 0x100) {
            out.write(major | 24);
            out.write((int) value);
        } else if (value < 0x10000) {
            out.write(major | 25);
            out.write((int) (value >>> 8) & 0xFF);
            out.write((int) value & 0xFF);
        } else {
            out.write(major | 26);
            for (int shift = 24; shift >= 0; shift -= 8) {
                out.write((int) (value >>> shift) & 0xFF);
            }
        }
    }

    @Test
    void rejectsTrailingContentAfterGeneratedRoot() {
        var generated = new SmithyGeneratedCborSerde();
        var value = ScalarStruct.builder().name("value").count(1).build();
        byte[] encoded = remainingBytes(generated.serialize(value, CborSettings.defaultSettings()));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);

        assertThatThrownBy(() -> generated.deserialize(
                trailing,
                ScalarStruct.builder(),
                CborSettings.defaultSettings()))
                .isInstanceOf(software.amazon.smithy.java.core.serde.SerializationException.class)
                .hasMessageContaining("Unexpected CBOR content");
    }

    private static <T extends SerializableShape> void assertGeneratedRoundTrip(
            SmithyGeneratedCborSerde generated,
            SerializableStruct value,
            Supplier<ShapeBuilder<T>> builder,
            CborSettings settings
    ) {
        ByteBuffer generatedBuffer = generated.serialize(value, settings);
        assertThat(generatedBuffer).isNotNull();
        byte[] generatedBytes = remainingBytes(generatedBuffer);
        byte[] dispatchBytes = remainingBytes(new DefaultCborSerdeProvider().serialize(value, settings));

        assertThat(generated.deserialize(generatedBytes, builder.get(), settings)).isEqualTo(value);
        assertThat(generated.deserialize(dispatchBytes, builder.get(), settings)).isEqualTo(value);
        assertThat(dispatch(generatedBytes, builder.get())).isEqualTo(value);
    }

    private static byte[] encodeDocument(Document document) {
        CborSerializer serializer = CborSerializer.acquire();
        try {
            serializer.writeDocument(null, document);
            return remainingBytes(serializer.extractResult());
        } finally {
            CborSerializer.release(serializer, false);
        }
    }

    private static byte[] remainingBytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.duplicate().get(result);
        return result;
    }

    private static void withProperty(String name, String value, Runnable action) {
        String previous = System.getProperty(name);
        try {
            System.setProperty(name, value);
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, previous);
            }
        }
    }

    @Test
    void rejectsRuntimeCodegenWithCustomProvider() {
        CborSerdeProvider provider = new CborSerdeProvider() {
            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public String getName() {
                return "custom";
            }

            @Override
            public ShapeDeserializer newDeserializer(byte[] source, CborSettings settings) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ShapeDeserializer newDeserializer(ByteBuffer source, CborSettings settings) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ShapeSerializer newSerializer(OutputStream sink, CborSettings settings) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuffer serialize(SerializableShape shape, CborSettings settings) {
                return ByteBuffer.wrap(new byte[] {99});
            }
        };
        assertThatThrownBy(() -> Rpcv2CborCodec.builder()
                .overrideSerdeProvider(provider)
                .runtimeCodegen(true)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("built-in CBOR provider");
    }

    public static final class ScalarStruct implements SerializableStruct {
        private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("codegen.cbor#ScalarStruct"))
                .shapeClass(ScalarStruct.class)
                .builderSupplier(Builder::new)
                .putMember("name", PreludeSchemas.STRING)
                .putMember("count", PreludeSchemas.INTEGER)
                .putMember("enabled", PreludeSchemas.BOOLEAN)
                .putMember("score", PreludeSchemas.DOUBLE)
                .build();
        private final String name;
        private final int count;
        private final boolean enabled;
        private final double score;

        private ScalarStruct(Builder builder) {
            name = builder.name;
            count = builder.count;
            enabled = builder.enabled;
            score = builder.score;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public double getScore() {
            return score;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (name != null) {
                serializer.writeString(SCHEMA.member("name"), name);
            }
            serializer.writeInteger(SCHEMA.member("count"), count);
            serializer.writeBoolean(SCHEMA.member("enabled"), enabled);
            serializer.writeDouble(SCHEMA.member("score"), score);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberIndex()) {
                case 0 -> name;
                case 1 -> count;
                case 2 -> enabled;
                case 3 -> score;
                default -> null;
            };
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ScalarStruct that
                    && count == that.count
                    && enabled == that.enabled
                    && Double.compare(score, that.score) == 0
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, count, enabled, score);
        }

        public static final class Builder implements ShapeBuilder<ScalarStruct> {
            private String name;
            private int count;
            private boolean enabled;
            private double score;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder count(int count) {
                this.count = count;
                return this;
            }

            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public Builder score(double score) {
                this.score = score;
                return this;
            }

            @Override
            public ScalarStruct build() {
                return new ScalarStruct(this);
            }

            @Override
            public ShapeBuilder<ScalarStruct> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, reader) -> {
                    switch (member.memberIndex()) {
                        case 0 -> builder.name(reader.readString(member));
                        case 1 -> builder.count(reader.readInteger(member));
                        case 2 -> builder.enabled(reader.readBoolean(member));
                        case 3 -> builder.score(reader.readDouble(member));
                        default -> {
                        }
                    }
                });
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public sealed interface StructureEncodedValue extends SerializableStruct
            permits StructureEncodedValue.SMember, StructureEncodedValue.$Unknown {
        Schema SCHEMA = Schema.structureBuilder(ShapeId.from("codegen.cbor#StructureEncodedValue"))
                .shapeClass(StructureEncodedValue.class)
                .builderSupplier(Builder::new)
                .putMember("S", PreludeSchemas.STRING)
                .build();

        public static Builder builder() {
            return new Builder();
        }

        @Override
        default Schema schema() {
            return SCHEMA;
        }

        @Override
        @SuppressWarnings("unchecked")
        default <T> T getMemberValue(Schema member) {
            return (T) ((SMember) this).s();
        }

        record SMember(String s) implements StructureEncodedValue {
            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                serializer.writeString(SCHEMA.member("S"), s);
            }
        }

        record $Unknown(String memberName) implements StructureEncodedValue {
            @Override
            public void serializeMembers(ShapeSerializer serializer) {}
        }

        final class Builder implements ShapeBuilder<StructureEncodedValue> {
            private StructureEncodedValue value;

            public Builder s(String value) {
                this.value = new SMember(value);
                return this;
            }

            @Override
            public StructureEncodedValue build() {
                return value;
            }

            @Override
            public ShapeBuilder<StructureEncodedValue> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, reader) -> {
                    if (member.memberIndex() == 0) {
                        builder.s(reader.readString(member));
                    }
                });
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public record StructureUnionEnvelope(StructureEncodedValue value) implements SerializableStruct {
        private static final Schema SCHEMA =
                Schema.structureBuilder(ShapeId.from("codegen.cbor#StructureUnionEnvelope"))
                        .shapeClass(StructureUnionEnvelope.class)
                        .builderSupplier(Builder::new)
                        .putMember("value", StructureEncodedValue.SCHEMA)
                        .build();

        static Builder builder() {
            return new Builder();
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA.member("value"), value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) value;
        }

        public static final class Builder implements ShapeBuilder<StructureUnionEnvelope> {
            private StructureEncodedValue value;

            public Builder value(StructureEncodedValue value) {
                this.value = value;
                return this;
            }

            @Override
            public StructureUnionEnvelope build() {
                return new StructureUnionEnvelope(value);
            }

            @Override
            public ShapeBuilder<StructureUnionEnvelope> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, reader) -> {
                    if (member.memberIndex() == 0) {
                        builder.value(StructureEncodedValue.builder().deserialize(reader).build());
                    }
                });
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public static final class AggregateStruct implements SerializableStruct {
        private static final Schema LIST = Schema.listBuilder(ShapeId.from("codegen.cbor#StringList"))
                .putMember("member", PreludeSchemas.STRING)
                .build();
        private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("codegen.cbor#AggregateStruct"))
                .shapeClass(AggregateStruct.class)
                .builderSupplier(Builder::new)
                .putMember("values", LIST)
                .build();
        private final List<String> values;

        private AggregateStruct(Builder builder) {
            values = builder.values;
        }

        public static Builder builder() {
            return new Builder();
        }

        public List<String> getValues() {
            return values;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (values != null) {
                serializer.writeList(SCHEMA.member("values"), values, values.size(), (items, writer) -> {
                    for (String value : items) {
                        writer.writeString(LIST.listMember(), value);
                    }
                });
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) values;
        }

        public static final class Builder implements ShapeBuilder<AggregateStruct> {
            private List<String> values;

            public Builder values(List<String> values) {
                this.values = values;
                return this;
            }

            @Override
            public AggregateStruct build() {
                return new AggregateStruct(this);
            }

            @Override
            public ShapeBuilder<AggregateStruct> deserialize(ShapeDeserializer decoder) {
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }
}
