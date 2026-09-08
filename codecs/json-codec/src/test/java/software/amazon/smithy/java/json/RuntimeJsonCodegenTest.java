/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.bench.model.BenchError;
import software.amazon.smithy.java.json.bench.model.BenchUnion;
import software.amazon.smithy.java.json.bench.model.BlobStruct;
import software.amazon.smithy.java.json.bench.model.Color;
import software.amazon.smithy.java.json.bench.model.ComplexStruct;
import software.amazon.smithy.java.json.bench.model.HashCollisionStruct;
import software.amazon.smithy.java.json.bench.model.InnerStruct;
import software.amazon.smithy.java.json.bench.model.JsonNameStruct;
import software.amazon.smithy.java.json.bench.model.NestedStruct;
import software.amazon.smithy.java.json.bench.model.NumericStruct;
import software.amazon.smithy.java.json.bench.model.Priority;
import software.amazon.smithy.java.json.bench.model.RecursiveStruct;
import software.amazon.smithy.java.json.bench.model.RequiredDefaultStruct;
import software.amazon.smithy.java.json.bench.model.SimpleStruct;
import software.amazon.smithy.java.json.bench.model.StringStruct;
import software.amazon.smithy.java.json.bench.model.TimestampStruct;
import software.amazon.smithy.java.json.bench.model.WireLengthEnum;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyGeneratedJsonSerde;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

final class RuntimeJsonCodegenTest {
    private static final JsonSettings SETTINGS = JsonSettings.builder()
            .useTimestampFormat(true)
            .build();
    private static final JsonSettings FALLBACK_SETTINGS = JsonSettings.builder()
            .useTimestampFormat(true)
            .runtimeCodegen(false)
            .build();

    @Test
    void directlySerializesAndDeserializesScalarStructure() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = SimpleStruct.builder()
                .name("test-\u00e9")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();

        ByteBuffer result = serde.serialize(value, SETTINGS);
        assertThat(result).isNotNull();
        byte[] bytes = new byte[result.remaining()];
        result.get(bytes);
        assertThat(serde.deserialize(bytes, SimpleStruct.builder(), SETTINGS)).isEqualTo(value);
        assertThat(serde.scan(bytes, SimpleStruct.builder().schema(), SETTINGS)).isEqualTo(bytes.length);
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"test-\u00e9\",\"age\":42,\"active\":true,"
                        + "\"score\":98.6,\"createdAt\":1736937000}");
    }

    @Test
    void streamingSerializerUsesGeneratedAndFallbackStructPaths() {
        var value = SimpleStruct.builder().name("value").age(7).build();
        var proxy = new SimpleSubsetProxy(value, (struct, member) -> true);
        try (var generated = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .runtimeCodegen(true)
                .build();
                var dispatch = JsonCodec.builder()
                        .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                        .runtimeCodegen(false)
                        .build()) {
            assertThat(stream(generated, value)).containsExactly(stream(dispatch, value));
            withProperty("smithy-java.runtime-codegen.json", "enabled", () -> {
                try (var fallback = JsonCodec.builder()
                        .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                        .runtimeCodegen(true)
                        .build()) {
                    assertThat(stream(fallback, proxy)).containsExactly(stream(dispatch, proxy));
                }
            });
        }
    }

    @Test
    void strictStreamingSerializerRejectsProxyFallback() {
        assumeTrue(RuntimeCodegenFeature.available());
        var value = SimpleStruct.builder().name("value").age(7).build();
        var proxy = new SimpleSubsetProxy(value, (struct, member) -> true);

        withProperty("smithy-java.runtime-codegen.json", "strict", () -> {
            try (var codec = JsonCodec.builder()
                    .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                    .build()) {
                assertThatThrownBy(() -> stream(codec, proxy))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("cannot fall back");
            }
        });
    }

    @Test
    void strictRejectsRootlessDispatchDeserializer() {
        assumeTrue(RuntimeCodegenFeature.available());
        withProperty("smithy-java.runtime-codegen.json", "strict", () -> {
            try (var codec = JsonCodec.builder()
                    .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                    .build()) {
                assertThatThrownBy(() -> codec.createDeserializer(new byte[] {'{', '}'}))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("createDeserializer would use dispatch serde");
            }
        });
    }

    @Test
    void deserializesSlicedHeapAndDirectByteBuffers() {
        byte[] payload = "{\"name\":\"value\",\"age\":7}".getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[payload.length + 4];
        System.arraycopy(payload, 0, padded, 2, payload.length);
        ByteBuffer sliced = ByteBuffer.wrap(padded, 2, payload.length).slice();
        ByteBuffer direct = ByteBuffer.allocateDirect(payload.length);
        direct.put(payload).flip();

        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .runtimeCodegen(true)
                .build()) {
            SimpleStruct expected = codec.deserializeShape(payload, SimpleStruct.builder());
            assertThat(codec.deserializeShape(sliced, SimpleStruct.builder())).isEqualTo(expected);
            assertThat(codec.deserializeShape(direct, SimpleStruct.builder())).isEqualTo(expected);
        }
    }

    @Test
    void acceptsReorderedAndUnknownFieldsAndNullOptionals() {
        var serde = new SmithyGeneratedJsonSerde();
        byte[] payload = """
                {"unknown":{"nested":[1,2]},"score":null,"age":7,"name":"value","active":false}
                """.getBytes(StandardCharsets.UTF_8);

        SimpleStruct result = serde.deserialize(payload, SimpleStruct.builder(), SETTINGS);

        assertThat(result.getName()).isEqualTo("value");
        assertThat(result.getAge()).isEqualTo(7);
        assertThat(result.isActive()).isFalse();
        assertThat(result.getScore()).isNull();
    }

    @Test
    void nullRequiredReferencesUseGenericErrorCorrection() {
        var generated = new SmithyGeneratedJsonSerde();
        var dispatch = new SmithyJsonSerdeProvider();
        byte[] payload = """
                {"name":null,"age":7}
                """.getBytes(StandardCharsets.UTF_8);

        SimpleStruct generatedResult = generated.deserialize(payload, SimpleStruct.builder(), SETTINGS);
        SimpleStruct dispatchResult;
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(dispatch)
                .runtimeCodegen(false)
                .build()) {
            dispatchResult = codec.deserializeShape(payload, SimpleStruct.builder());
        }

        assertThat(generatedResult.getName()).isEmpty();
        assertThat(generatedResult).isEqualTo(dispatchResult);
    }

    @Test
    void nullRequiredMemberWithDefaultUsesDefault() {
        var generated = new SmithyGeneratedJsonSerde();
        var dispatch = new SmithyJsonSerdeProvider();
        byte[] payload = "{\"type\":null}".getBytes(StandardCharsets.UTF_8);

        RequiredDefaultStruct generatedResult =
                generated.deserialize(payload, RequiredDefaultStruct.builder(), SETTINGS);
        RequiredDefaultStruct dispatchResult;
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(dispatch)
                .runtimeCodegen(false)
                .build()) {
            dispatchResult = codec.deserializeShape(payload, RequiredDefaultStruct.builder());
        }

        assertThat(generatedResult.getType()).isEqualTo("text");
        assertThat(generatedResult).isEqualTo(dispatchResult);
    }

    @Test
    void acceptsWhitespaceAroundPackedFieldTokens() {
        var serde = new SmithyGeneratedJsonSerde();
        byte[] payload = """
                { "name" : "value", "age": 7 , "active" :false, "createdAt": 1736937000 }
                """.getBytes(StandardCharsets.UTF_8);

        SimpleStruct result = serde.deserialize(payload, SimpleStruct.builder(), SETTINGS);

        assertThat(result.getName()).isEqualTo("value");
        assertThat(result.getAge()).isEqualTo(7);
        assertThat(result.isActive()).isFalse();
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2025-01-15T10:30:00Z"));
    }

    @Test
    void readsAndWritesNonAsciiJsonName() {
        var serde = new SmithyGeneratedJsonSerde();
        JsonSettings settings = JsonSettings.builder()
                .useJsonName(true)
                .build();
        var value = JsonNameStruct.builder().unicodeName("value").build();
        String serialized = StandardCharsets.UTF_8.decode(serde.serialize(value, settings)).toString();
        byte[] escaped = """
                {"\\u00e9":"value"}
                """.getBytes(StandardCharsets.UTF_8);
        byte[] raw = """
                {"é":"value"}
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(serialized).contains("\"é\":\"value\"");
        assertThat(serde.deserialize(escaped, JsonNameStruct.builder(), settings).getUnicodeName())
                .isEqualTo("value");
        assertThat(serde.deserialize(raw, JsonNameStruct.builder(), settings).getUnicodeName())
                .isEqualTo("value");
    }

    @Test
    void supportsBorrowedOutput() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = SimpleStruct.builder().name("x").age(1).build();
        var sink = new ByteArrayOutputStream();

        assertThat(serde.serializeTo(value, sink, SETTINGS)).isTrue();
        assertThat(sink.toString(StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"x\",\"age\":1}");
    }

    @Test
    void serializesMemberSubsetsLikeDispatch() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = SimpleStruct.builder()
                .name("name")
                .age(42)
                .active(true)
                .build();
        MemberSubsetCodec.MemberSubset subset =
                (struct, member) -> member.memberName().equals("name") || member.memberName().equals("active");

        byte[] generated = bytes(serde.serialize(value, SETTINGS, subset));
        byte[] interpreted = bytes(new SmithyJsonSerdeProvider()
                .serialize(new SimpleSubsetProxy(value, subset), SETTINGS));

        assertThat(generated).containsExactly(interpreted);
        assertThat(new String(generated, StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"name\",\"active\":true}");
    }

    @Test
    void deserializesMemberSubsetsIntoConcreteBuilder() {
        var serde = new SmithyGeneratedJsonSerde();
        ShapeBuilder<SimpleStruct> builder = SimpleStruct.builder().age(7);
        MemberSubsetCodec.MemberSubset subset =
                (struct, member) -> member.memberName().equals("name");
        ByteBuffer source = ByteBuffer.wrap(
                "{\"name\":\"body\",\"age\":999,\"active\":true}".getBytes(StandardCharsets.UTF_8));

        assertThat(serde.deserialize(builder.schema(), builder, source, SETTINGS, subset)).isTrue();
        assertThat(builder.build()).isEqualTo(SimpleStruct.builder().name("body").age(7).build());
    }

    @Test
    void serializesEmptyMemberSubsetAsEmptyObject() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = SimpleStruct.builder().name("name").age(42).build();
        MemberSubsetCodec.MemberSubset none = (struct, member) -> false;

        byte[] generated = bytes(serde.serialize(value, SETTINGS, none));
        byte[] interpreted = bytes(new SmithyJsonSerdeProvider()
                .serialize(new SimpleSubsetProxy(value, none), SETTINGS));

        assertThat(generated).containsExactly(interpreted);
        assertThat(new String(generated, StandardCharsets.UTF_8)).isEqualTo("{}");
    }

    @Test
    void declinesMemberSubsetsPastTheCacheBound() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = SimpleStruct.builder().name("name").age(42).build();

        for (int i = 0; i < 16; i++) {
            MemberSubsetCodec.MemberSubset subset = new MemberSubsetCodec.MemberSubset() {
                @Override
                public boolean includes(Schema struct, Schema member) {
                    return member.memberIndex() == 0;
                }
            };
            assertThat(serde.serialize(value, FALLBACK_SETTINGS, subset)).isNotNull();
        }
        MemberSubsetCodec.MemberSubset overflow = new MemberSubsetCodec.MemberSubset() {
            @Override
            public boolean includes(Schema struct, Schema member) {
                return member.memberIndex() == 0;
            }
        };
        assertThat(serde.serialize(value, FALLBACK_SETTINGS, overflow)).isNull();
    }

    @Test
    void customRootBuilderFallsBackInsteadOfReachingGeneratedCast() {
        byte[] payload = "{\"name\":\"value\",\"age\":7}".getBytes(StandardCharsets.UTF_8);
        var direct = new SmithyGeneratedJsonSerde();

        assertThat(direct.deserialize(payload, wrappingSimpleBuilder(), FALLBACK_SETTINGS)).isNull();

        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .runtimeCodegen(false)
                .build()) {
            assertThat(codec.deserializeShape(payload, wrappingSimpleBuilder()))
                    .isEqualTo(SimpleStruct.builder().name("value").age(7).build());
        }
    }

    @Test
    void malformedInputDoesNotRetryThroughFallback() {
        var serde = new SmithyGeneratedJsonSerde();

        assertThrows(
                SerializationException.class,
                () -> serde.deserialize(
                        "{\"name\":\"x\",\"age\":01}".getBytes(StandardCharsets.UTF_8),
                        SimpleStruct.builder(),
                        SETTINGS));
        assertThrows(
                SerializationException.class,
                () -> serde.deserialize(
                        "{\"name\":\"x\"".getBytes(StandardCharsets.UTF_8),
                        SimpleStruct.builder(),
                        SETTINGS));
    }

    @Test
    void rejectsTrailingContent() {
        var serde = new SmithyGeneratedJsonSerde();

        assertThrows(
                SerializationException.class,
                () -> serde.deserialize(
                        "{\"name\":\"x\",\"age\":1} trailing".getBytes(StandardCharsets.UTF_8),
                        SimpleStruct.builder(),
                        SETTINGS));
    }

    @Test
    void rejectsExcessiveGeneratedNesting() {
        var serde = new SmithyGeneratedJsonSerde();
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 1_100; i++) {
            payload.append("{\"value\":\"x\",\"child\":");
        }
        payload.append("{\"value\":\"leaf\"}");
        payload.append("}".repeat(1_100));

        assertThrows(
                SerializationException.class,
                () -> serde.deserialize(
                        payload.toString().getBytes(StandardCharsets.UTF_8),
                        RecursiveStruct.builder(),
                        SETTINGS));
    }

    @Test
    void disambiguatesCollidingFieldHashes() {
        var serde = new SmithyGeneratedJsonSerde();
        byte[] payload = """
                {"bB":"second","aa":"first"}
                """.getBytes(StandardCharsets.UTF_8);

        HashCollisionStruct result = serde.deserialize(payload, HashCollisionStruct.builder(), SETTINGS);

        assertThat(result.getAa()).isEqualTo("first");
        assertThat(result.getBB()).isEqualTo("second");
    }

    @Test
    void generatedDoubleIntegerFastPathPreservesNumberSemantics() {
        var serde = new SmithyGeneratedJsonSerde();

        NumericStruct negativeZero = serde.deserialize(
                "{\"doubleVal\":-0}".getBytes(StandardCharsets.UTF_8),
                NumericStruct.builder(),
                SETTINGS);
        assertThat(Double.doubleToRawLongBits(negativeZero.getDoubleVal()))
                .isEqualTo(Double.doubleToRawLongBits(-0.0d));

        for (String value : List.of(
                "123456789012345678",
                "9223372036854775808",
                "1.25",
                "15e2")) {
            NumericStruct result = serde.deserialize(
                    ("{\"doubleVal\":" + value + "}").getBytes(StandardCharsets.UTF_8),
                    NumericStruct.builder(),
                    SETTINGS);
            assertThat(result.getDoubleVal()).isEqualTo(Double.parseDouble(value));
        }

        assertThrows(
                SerializationException.class,
                () -> serde.deserialize(
                        "{\"doubleVal\":01}".getBytes(StandardCharsets.UTF_8),
                        NumericStruct.builder(),
                        SETTINGS));
    }

    @Test
    void coversScalarBoundariesEscapingBlobsTimestampsAndRecursion() {
        var serde = new SmithyGeneratedJsonSerde();
        var numeric = NumericStruct.builder()
                .byteVal(Byte.MIN_VALUE)
                .shortVal(Short.MAX_VALUE)
                .intVal(Integer.MIN_VALUE)
                .longVal(Long.MAX_VALUE)
                .floatVal(Float.MAX_VALUE)
                .doubleVal(Double.MIN_NORMAL)
                .bigIntVal(new BigInteger("123456789012345678901234567890"))
                .bigDecVal(new BigDecimal("-1234567890.0123456789"))
                .build();
        var string = StringStruct.builder().value("quote=\" slash=\\ newline=\n emoji=\ud83d\ude03").build();
        var blob = BlobStruct.builder().data(ByteBuffer.wrap(new byte[] {0, 1, -1})).build();
        Instant instant = Instant.parse("2025-01-15T10:30:00Z");
        var timestamps = TimestampStruct.builder()
                .epochSeconds(instant)
                .dateTime(instant)
                .httpDate(instant)
                .build();
        var recursive = RecursiveStruct.builder()
                .value("root")
                .child(RecursiveStruct.builder().value("leaf").build())
                .build();

        assertRoundTrip(serde, numeric, NumericStruct.builder());
        assertRoundTrip(serde, string, StringStruct.builder());
        assertRoundTrip(serde, blob, BlobStruct.builder());
        assertRoundTrip(serde, timestamps, TimestampStruct.builder());
        assertRoundTrip(serde, recursive, RecursiveStruct.builder());
    }

    @Test
    void roundTripsComplexAggregateGraph() {
        var serde = new SmithyGeneratedJsonSerde();
        var inner = InnerStruct.builder().value("inner").numbers(List.of(1, 2, 3)).build();
        var nested = NestedStruct.builder().field1("nested").field2(2).inner(inner).build();
        var sparseMap = new HashMap<String, String>();
        sparseMap.put("present", "value");
        sparseMap.put("null", null);
        var metadata = new HashMap<String, String>();
        metadata.put("quote\"slash\\", "escaped");
        metadata.put("snowman-\u2603", "unicode");
        metadata.put("line\nbreak", "control");
        var value = ComplexStruct.builder()
                .id("id")
                .count(1)
                .enabled(true)
                .ratio(1.5)
                .score(2.5f)
                .bigCount(99)
                .tags(List.of("a", "b", "c", "d", "e", "f"))
                .intList(List.of(1, 2))
                .metadata(metadata)
                .intMap(Map.of("n", 3))
                .nested(nested)
                .optionalNested(nested)
                .structList(List.of(nested, nested, nested))
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

        ByteBuffer encoded = serde.serialize(value, SETTINGS);
        assertThat(encoded).isNotNull();
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        assertThat(serde.deserialize(bytes, ComplexStruct.builder(), SETTINGS)).isEqualTo(value);
    }

    @Test
    void denseCollectionsRejectNullOnReadAndWriteLikeDispatch() {
        var serde = new SmithyGeneratedJsonSerde();
        var dispatch = new SmithyJsonSerdeProvider();
        byte[] denseList = "{\"tags\":[\"a\",null]}".getBytes(StandardCharsets.UTF_8);
        byte[] denseMap = "{\"metadata\":{\"key\":null}}".getBytes(StandardCharsets.UTF_8);

        for (byte[] payload : List.of(denseList, denseMap)) {
            assertThatThrownBy(() -> serde.deserialize(payload, ComplexStruct.builder(), SETTINGS))
                    .isInstanceOf(SerializationException.class);
            assertThatThrownBy(() -> dispatch(payload, ComplexStruct.builder()))
                    .isInstanceOf(SerializationException.class);
        }

        var nested = NestedStruct.builder().field1("nested").field2(2).build();
        var listWithNull = ComplexStruct.builder()
                .id("id")
                .count(1)
                .nested(nested)
                .tags(Arrays.asList("a", null))
                .build();
        var mapWithNull = new HashMap<String, String>();
        mapWithNull.put("key", null);
        var valueWithNullMap = ComplexStruct.builder()
                .id("id")
                .count(1)
                .nested(nested)
                .metadata(mapWithNull)
                .build();

        for (ComplexStruct value : List.of(listWithNull, valueWithNullMap)) {
            assertThatThrownBy(() -> serde.serialize(value, SETTINGS))
                    .isInstanceOf(SerializationException.class);
            assertThatThrownBy(() -> dispatch.serialize(value, SETTINGS))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void sparseCollectionsAllowNullOnReadAndWriteLikeDispatch() {
        var serde = new SmithyGeneratedJsonSerde();
        byte[] payload = ("{\"id\":\"id\",\"count\":1,"
                + "\"nested\":{\"field1\":\"nested\",\"field2\":2},"
                + "\"sparseStrings\":[\"a\",null,\"b\"],"
                + "\"sparseMap\":{\"present\":\"value\",\"absent\":null}}")
                .getBytes(StandardCharsets.UTF_8);

        ComplexStruct generated = serde.deserialize(payload, ComplexStruct.builder(), SETTINGS);
        ComplexStruct interpreted = dispatch(payload, ComplexStruct.builder());

        assertThat(generated).isEqualTo(interpreted);
        assertThat(generated.getSparseStrings()).containsExactly("a", null, "b");
        assertThat(generated.getSparseMap()).containsEntry("present", "value").containsEntry("absent", null);
        assertThat(bytes(serde.serialize(generated, SETTINGS)))
                .containsExactly(bytes(new SmithyJsonSerdeProvider().serialize(interpreted, SETTINGS)));
    }

    @Test
    void declinesMapsWhoseKeysAreNotPlainStrings() {
        withProperty("smithy-java.runtime-codegen.json", "enabled", () -> {
            var serde = new SmithyGeneratedJsonSerde();
            var value = new EnumKeyedMapModel.Value(
                    Map.of(new EnumKeyedMapModel.Key("A"), "value"));

            assertThat(serde.serialize(value, FALLBACK_SETTINGS)).isNull();
            assertThat(serde.deserialize(
                    "{\"values\":{\"A\":\"value\"}}".getBytes(StandardCharsets.UTF_8),
                    new EnumKeyedMapModel.Value.Builder(),
                    FALLBACK_SETTINGS))
                    .isNull();
        });
    }

    @Test
    void preservesTypedDocumentDiscriminator() {
        var serde = new SmithyGeneratedJsonSerde();
        var nested = NestedStruct.builder().field1("nested").field2(2).build();
        var value = ComplexStruct.builder()
                .id("id")
                .count(1)
                .nested(nested)
                .freeformData(Document.of(nested))
                .build();

        String json = StandardCharsets.UTF_8.decode(serde.serialize(value, SETTINGS)).toString();

        assertThat(json).contains("\"freeformData\":{\"__type\":\"smithy.java.json.bench#NestedStruct\"");
    }

    @Test
    void supportsMultipleSettingsInstances() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = SimpleStruct.builder()
                .name("value")
                .age(1)
                .createdAt(Instant.EPOCH)
                .build();
        JsonSettings dateTime = JsonSettings.builder()
                .defaultTimestampFormat(TimestampFormatter.Prelude.DATE_TIME)
                .build();

        String epoch = StandardCharsets.UTF_8.decode(serde.serialize(value, SETTINGS)).toString();
        String iso8601 = StandardCharsets.UTF_8.decode(serde.serialize(value, dateTime)).toString();

        assertThat(epoch).contains("\"createdAt\":0");
        assertThat(iso8601).contains("\"createdAt\":\"1970-01-01T00:00:00Z\"");
    }

    @Test
    void concurrentlySwitchesSettingsWithoutCrossContamination() throws Exception {
        var serde = new SmithyGeneratedJsonSerde();
        var value = JsonNameStruct.builder()
                .id("id")
                .displayName("display")
                .unicodeName("unicode")
                .normalField("normal")
                .build();
        JsonSettings memberNames = JsonSettings.builder().useJsonName(false).build();
        JsonSettings jsonNames = JsonSettings.builder().useJsonName(true).build();
        String expectedMembers = StandardCharsets.UTF_8
                .decode(serde.serialize(value, memberNames))
                .toString();
        String expectedJsonNames = StandardCharsets.UTF_8
                .decode(serde.serialize(value, jsonNames))
                .toString();
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                JsonSettings settings = (thread & 1) == 0 ? memberNames : jsonNames;
                String expected = (thread & 1) == 0 ? expectedMembers : expectedJsonNames;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1_000; i++) {
                        String actual = StandardCharsets.UTF_8
                                .decode(serde.serialize(value, settings))
                                .toString();
                        assertThat(actual).isEqualTo(expected);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
    }

    @Test
    void generatedAndDispatchInteroperateForComplexGraph() {
        var generated = new SmithyGeneratedJsonSerde();
        var dispatch = new SmithyJsonSerdeProvider();
        var nested = NestedStruct.builder()
                .field1("nested")
                .field2(2)
                .inner(InnerStruct.builder().value("inner").numbers(List.of(1, 2, 3)).build())
                .build();
        var value = ComplexStruct.builder()
                .id("id")
                .count(1)
                .enabled(true)
                .ratio(1.5)
                .score(2.5f)
                .bigCount(99)
                .tags(List.of("a", "b"))
                .metadata(Map.of("key", "value"))
                .nested(nested)
                .choice(new BenchUnion.StructValueMember(nested))
                .color(Color.GREEN)
                .build();

        ByteBuffer generatedBytes = generated.serialize(value, SETTINGS);
        ByteBuffer dispatchBytes = dispatch.serialize(value, SETTINGS);
        byte[] generatedPayload = new byte[generatedBytes.remaining()];
        generatedBytes.duplicate().get(generatedPayload);
        byte[] dispatchPayload = new byte[dispatchBytes.remaining()];
        dispatchBytes.duplicate().get(dispatchPayload);

        assertThat(generated.deserialize(dispatchPayload, ComplexStruct.builder(), SETTINGS)).isEqualTo(value);
        try (var dispatchCodec = JsonCodec.builder()
                .overrideSerdeProvider(dispatch)
                .runtimeCodegen(false)
                .useTimestampFormat(true)
                .build()) {
            assertThat(dispatchCodec.deserializeShape(generatedPayload, ComplexStruct.builder())).isEqualTo(value);
        }
    }

    @Test
    void clonedCodegenSettingsRetainTimestampConfiguration() {
        JsonSettings original = JsonSettings.builder()
                .defaultTimestampFormat(TimestampFormatter.Prelude.DATE_TIME)
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .runtimeCodegen(true)
                .build();
        JsonSettings cloned = original.toBuilder().build();
        var value = SimpleStruct.builder().name("value").age(1).build();

        assertThat(original.runtimeCodegen()).isTrue();
        assertThat(cloned).isEqualTo(original);
        assertThat(cloned.timestampResolver().defaultFormat())
                .isEqualTo(TimestampFormatter.Prelude.DATE_TIME);
        assertThat(original.provider().serialize(value, original)).isNotNull();
        assertThat(cloned.provider().serialize(value, cloned)).isNotNull();
    }

    @Test
    void rejectsRuntimeCodegenWithCustomProvider() {
        assumeTrue(RuntimeCodegenFeature.available());
        assertThatThrownBy(() -> JsonCodec.builder()
                .overrideSerdeProvider(new JacksonJsonSerdeProvider())
                .runtimeCodegen(true)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("native Smithy provider");
    }

    @Test
    void runtimeCodegenKillSwitchDisablesProviderValidation() {
        withProperty("smithy-java.runtime-codegen.json", "disabled", () -> {
            JsonSettings settings = JsonSettings.builder()
                    .overrideSerdeProvider(new JacksonJsonSerdeProvider())
                    .runtimeCodegen(true)
                    .build();

            assertThat(settings.runtimeCodegen()).isFalse();
            assertThat(settings.provider()).isInstanceOf(JacksonJsonSerdeProvider.class);
        });
    }

    @Test
    void strictRuntimeCodegenRejectsCustomProviderFallback() {
        withProperty("smithy-java.runtime-codegen.json", "strict", () -> {
            assertThatThrownBy(() -> JsonSettings.builder()
                    .overrideSerdeProvider(new JacksonJsonSerdeProvider())
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot fall back");
        });
    }

    @Test
    void explicitDisableWinsOverEnabledProperty() {
        withProperty("smithy-java.runtime-codegen.json", "enabled", () -> {
            JsonSettings settings = JsonSettings.builder()
                    .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                    .runtimeCodegen(false)
                    .build();

            assertThat(settings.runtimeCodegen()).isFalse();
        });
    }

    @Test
    void strictPropertyStillEnablesExplicitCodegen() {
        assumeTrue(RuntimeCodegenFeature.available());
        withProperty("smithy-java.runtime-codegen.json", "strict", () -> {
            JsonSettings settings = JsonSettings.builder()
                    .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                    .runtimeCodegen(true)
                    .build();

            assertThat(settings.runtimeCodegen()).isTrue();
        });
    }

    @Test
    void readsKnownEscapedAndUnknownEnums() {
        var serde = new SmithyGeneratedJsonSerde();
        var nested = NestedStruct.builder().field1("nested").field2(2).build();
        var value = ComplexStruct.builder()
                .id("id")
                .count(1)
                .nested(nested)
                .color(Color.GREEN)
                .build();
        ByteBuffer encoded = serde.serialize(value, SETTINGS);
        String json = StandardCharsets.UTF_8.decode(encoded).toString();

        String escapedGreen = "\"GR" + "\\" + "u0045EN\"";
        byte[] escaped = json.replace("\"GREEN\"", escapedGreen)
                .getBytes(StandardCharsets.UTF_8);
        assertThat(serde.deserialize(escaped, ComplexStruct.builder(), SETTINGS).getColor())
                .isSameAs(Color.GREEN);

        byte[] unknown = json.replace("\"GREEN\"", "\"PURPLE\"")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(serde.deserialize(unknown, ComplexStruct.builder(), SETTINGS).getColor().getValue())
                .isEqualTo("PURPLE");
    }

    @Test
    void readsEveryEnumWireLengthBranch() {
        var serde = new SmithyGeneratedJsonSerde();
        var nested = NestedStruct.builder().field1("nested").field2(2).build();
        List<WireLengthEnum> all = List.of(
                WireLengthEnum.ONE,
                WireLengthEnum.SEVEN,
                WireLengthEnum.EIGHT,
                WireLengthEnum.NINE,
                WireLengthEnum.SIXTEEN,
                WireLengthEnum.SEVENTEEN,
                WireLengthEnum.LONG,
                WireLengthEnum.SHARED_PREFIX_EIGHT,
                WireLengthEnum.SHARED_PREFIX_NINE,
                WireLengthEnum.SHARED_PREFIX_LONG,
                WireLengthEnum.QUOTE,
                WireLengthEnum.BACKSLASH,
                WireLengthEnum.NON_ASCII_SHORT,
                WireLengthEnum.NON_ASCII);

        for (WireLengthEnum value : all) {
            var single = ComplexStruct.builder()
                    .id("id")
                    .count(1)
                    .nested(nested)
                    .wireLength(value)
                    .build();
            assertRoundTrip(serde, single, ComplexStruct.builder());
        }
        var batch = ComplexStruct.builder()
                .id("id")
                .count(1)
                .nested(nested)
                .wireLengthList(all)
                .build();
        assertRoundTrip(serde, batch, ComplexStruct.builder());

        for (WireLengthEnum value : all) {
            byte[] payload = ("{\"id\":\"id\",\"count\":1,\"nested\":{\"field1\":\"n\",\"field2\":2},"
                    + "\"wireLength\":" + quoteJson(value.getValue()) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            assertThat(serde.deserialize(payload, ComplexStruct.builder(), SETTINGS).getWireLength())
                    .isSameAs(value);
        }

        byte[] unknown = ("{\"id\":\"id\",\"count\":1,\"nested\":{\"field1\":\"n\",\"field2\":2},"
                + "\"wireLength\":\"abcdefgy\"}").getBytes(StandardCharsets.UTF_8);
        assertThat(serde.deserialize(unknown, ComplexStruct.builder(), SETTINGS).getWireLength().getValue())
                .isEqualTo("abcdefgy");
    }

    @Test
    void readsAndWritesIntEnumsAtEveryLoweringSite() {
        var serde = new SmithyGeneratedJsonSerde();
        var nested = NestedStruct.builder().field1("nested").field2(2).build();
        List<Priority> all = List.of(
                Priority.MIN,
                Priority.NEGATIVE,
                Priority.ZERO,
                Priority.ONE,
                Priority.HUNDRED,
                Priority.MAX);

        for (Priority value : all) {
            var single = ComplexStruct.builder()
                    .id("id")
                    .count(1)
                    .nested(nested)
                    .priority(value)
                    .build();
            assertRoundTrip(serde, single, ComplexStruct.builder());
        }

        Map<String, Priority> byName = new HashMap<>();
        for (Priority value : all) {
            byName.put(String.valueOf(value.getValue()), value);
        }
        var batch = ComplexStruct.builder()
                .id("id")
                .count(1)
                .nested(nested)
                .priorityList(all)
                .priorityMap(byName)
                .build();
        assertRoundTrip(serde, batch, ComplexStruct.builder());

        for (Priority value : all) {
            byte[] payload = ("{\"id\":\"id\",\"count\":1,\"nested\":{\"field1\":\"n\",\"field2\":2},"
                    + "\"priority\":" + value.getValue() + "}")
                    .getBytes(StandardCharsets.UTF_8);
            assertThat(serde.deserialize(payload, ComplexStruct.builder(), SETTINGS).getPriority())
                    .isSameAs(value);
        }

        byte[] unknown = ("{\"id\":\"id\",\"count\":1,\"nested\":{\"field1\":\"n\",\"field2\":2},"
                + "\"priority\":4242}").getBytes(StandardCharsets.UTF_8);
        assertThat(serde.deserialize(unknown, ComplexStruct.builder(), SETTINGS).getPriority().getValue())
                .isEqualTo(4242);
    }

    @Test
    void structuresWithIntEnumMembersAreGeneratedRatherThanFallingBack() {
        withProperty("smithy-java.runtime-codegen.json", "strict", () -> {
            var serde = new SmithyGeneratedJsonSerde();
            JsonSettings freshSettings = JsonSettings.builder()
                    .useTimestampFormat(true)
                    .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                    .build();
            var value = ComplexStruct.builder()
                    .id("id")
                    .count(1)
                    .nested(NestedStruct.builder().field1("n").field2(2).build())
                    .priority(Priority.HUNDRED)
                    .priorityList(List.of(Priority.MIN, Priority.MAX))
                    .build();

            ByteBuffer encoded = serde.serialize(value, freshSettings);
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            assertThat(serde.deserialize(bytes, ComplexStruct.builder(), freshSettings)).isEqualTo(value);
        });
    }

    private static String quoteJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    @Test
    void skipsUnionTypeDiscriminatorWhereverItAppears() {
        var serde = new SmithyGeneratedJsonSerde();
        var expected = new BenchUnion.StringValueMember("value");

        for (String union : List.of(
                "{\"__type\":\"smithy.java.json.bench#BenchUnion\",\"stringValue\":\"value\"}",
                "{\"stringValue\":\"value\",\"__type\":\"smithy.java.json.bench#BenchUnion\"}",
                "{\"__type\":\"a\",\"stringValue\":\"value\",\"__type\":\"b\"}")) {
            byte[] root = union.getBytes(StandardCharsets.UTF_8);
            assertThat(serde.deserialize(root, BenchUnion.builder(), SETTINGS)).isEqualTo(expected);
            assertThat(serde.deserialize(root, BenchUnion.builder(), SETTINGS))
                    .isEqualTo(dispatch(root, BenchUnion.builder()));

            byte[] nested = wrapChoice(union);
            assertThat(serde.deserialize(nested, ComplexStruct.builder(), SETTINGS).getChoice())
                    .isEqualTo(expected);
        }
    }

    @Test
    void treatsNullUnionMembersAsAbsent() {
        var serde = new SmithyGeneratedJsonSerde();
        var expected = new BenchUnion.IntValueMember(42);

        for (String union : List.of(
                "{\"stringValue\":null,\"intValue\":42}",
                "{\"intValue\":42,\"stringValue\":null}",
                "{\"structValue\":null,\"intValue\":42,\"stringValue\":null}")) {
            byte[] root = union.getBytes(StandardCharsets.UTF_8);
            assertThat(serde.deserialize(root, BenchUnion.builder(), SETTINGS)).isEqualTo(expected);
            assertThat(serde.deserialize(root, BenchUnion.builder(), SETTINGS))
                    .isEqualTo(dispatch(root, BenchUnion.builder()));

            byte[] nested = wrapChoice(union);
            assertThat(serde.deserialize(nested, ComplexStruct.builder(), SETTINGS).getChoice())
                    .isEqualTo(expected);
        }
    }

    @Test
    void rejectsUnionsCarryingNoMemberOrMoreThanOne() {
        var serde = new SmithyGeneratedJsonSerde();

        for (String union : List.of(
                "{}",
                "{\"__type\":\"x\"}",
                "{\"stringValue\":null}",
                "{\"stringValue\":\"a\",\"intValue\":1}",
                "{\"stringValue\":\"a\",\"future\":1}",
                "{\"future\":1,\"stringValue\":\"a\"}")) {
            byte[] root = union.getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> serde.deserialize(root, BenchUnion.builder(), SETTINGS))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> dispatch(root, BenchUnion.builder()))
                    .isInstanceOf(RuntimeException.class);

            byte[] nested = wrapChoice(union);
            assertThatThrownBy(() -> serde.deserialize(nested, ComplexStruct.builder(), SETTINGS))
                    .isInstanceOf(SerializationException.class);
        }
    }

    @Test
    void preservesUnknownUnionMembers() {
        var serde = new SmithyGeneratedJsonSerde();
        byte[] payload = "{\"future\":{\"nested\":[1,2]}}".getBytes(StandardCharsets.UTF_8);

        BenchUnion generated = serde.deserialize(payload, BenchUnion.builder(), SETTINGS);

        assertThat(generated).isInstanceOf(BenchUnion.$Unknown.class);
        assertThat((String) generated.getValue()).isEqualTo("future");
        assertThat(generated).isEqualTo(dispatch(payload, BenchUnion.builder()));

        BenchUnion nested = serde.deserialize(wrapChoice("{\"future\":1}"), ComplexStruct.builder(), SETTINGS)
                .getChoice();
        assertThat(nested).isInstanceOf(BenchUnion.$Unknown.class);
        assertThat((String) nested.getValue()).isEqualTo("future");
    }

    @Test
    void forbidsUnknownUnionMembersInGeneratedRootAndNestedReaders() {
        var serde = new SmithyGeneratedJsonSerde();
        JsonSettings forbid = JsonSettings.builder()
                .forbidUnknownUnionMembers(true)
                .build();
        byte[] root = "{\"future\":1}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serde.deserialize(root, BenchUnion.builder(), forbid))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Unknown union member");
        assertThatThrownBy(() -> serde.deserialize(
                wrapChoice("{\"future\":1}"),
                ComplexStruct.builder(),
                forbid))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Unknown union member");
    }

    @Test
    void treatsNullPrimitiveMembersAsAbsentLikeDispatch() {
        var serde = new SmithyGeneratedJsonSerde();
        byte[] payload = ("{\"id\":\"id\",\"count\":1,\"enabled\":null,\"ratio\":null,\"score\":null,"
                + "\"bigCount\":null,\"optionalInt\":null,\"nested\":{\"field1\":\"n\",\"field2\":2}}")
                .getBytes(StandardCharsets.UTF_8);

        ComplexStruct generated = serde.deserialize(payload, ComplexStruct.builder(), SETTINGS);

        assertThat(generated).isEqualTo(dispatch(payload, ComplexStruct.builder()));
        assertThat(generated.isEnabled()).isFalse();
        assertThat(generated.getBigCount()).isZero();
    }

    @Test
    void refusesToGenerateSerdeForErrorShapes() {
        withProperty("smithy-java.runtime-codegen.json", "enabled", () -> {
            var serde = new SmithyGeneratedJsonSerde();
            var value = BenchError.builder().message("boom").code(7).build();
            byte[] payload = "{\"message\":\"boom\",\"code\":7}".getBytes(StandardCharsets.UTF_8);

            assertThat(serde.serialize(value, FALLBACK_SETTINGS)).isNull();
            assertThat(serde.deserialize(payload, BenchError.builder(), FALLBACK_SETTINGS)).isNull();

            try (var codec = JsonCodec.builder()
                    .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                    .runtimeCodegen(true)
                    .useTimestampFormat(true)
                    .build()) {
                BenchError result = codec.deserializeShape(payload, BenchError.builder());
                assertThat(result.getMessage()).isEqualTo("boom");
                assertThat(result.getCode()).isEqualTo(7);
                assertThat(result.deserialized()).isTrue();
            }
        });
    }

    private static byte[] wrapChoice(String union) {
        return ("{\"id\":\"id\",\"count\":1,\"nested\":{\"field1\":\"n\",\"field2\":2},\"choice\":" + union + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static <T extends SerializableShape> T dispatch(byte[] payload, ShapeBuilder<T> builder) {
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .runtimeCodegen(false)
                .useTimestampFormat(true)
                .build()) {
            return codec.deserializeShape(payload, builder);
        }
    }

    @Test
    void supportsStructureEncodedSealedInterfaceUnions() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = StructureEncodedUnionModel.Envelope.builder()
                .attribute(new StructureEncodedUnionModel.Value.SMember("value"))
                .build();

        assertRoundTrip(serde, value, StructureEncodedUnionModel.Envelope.builder());
    }

    private static <T extends SerializableShape> void assertRoundTrip(
            SmithyGeneratedJsonSerde serde,
            T value,
            ShapeBuilder<T> builder
    ) {
        ByteBuffer encoded = serde.serialize((SerializableStruct) value, SETTINGS);
        assertThat(encoded).isNotNull();
        byte[] generated = bytes(encoded);
        byte[] interpreted = bytes(new SmithyJsonSerdeProvider().serialize(value, SETTINGS));
        assertThat(generated).containsExactly(interpreted);
        assertThat(serde.deserialize(generated, builder, SETTINGS)).isEqualTo(value);
    }

    private static ShapeBuilder<SimpleStruct> wrappingSimpleBuilder() {
        ShapeBuilder<SimpleStruct> delegate = SimpleStruct.builder();
        return new ShapeBuilder<>() {
            @Override
            public SimpleStruct build() {
                return delegate.build();
            }

            @Override
            public ShapeBuilder<SimpleStruct> deserialize(ShapeDeserializer decoder) {
                delegate.deserialize(decoder);
                return this;
            }

            @Override
            public Schema schema() {
                return delegate.schema();
            }
        };
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.duplicate().get(result);
        return result;
    }

    private static byte[] stream(JsonCodec codec, SerializableStruct value) {
        var sink = new ByteArrayOutputStream();
        try (ShapeSerializer serializer = codec.createSerializer(sink)) {
            value.serialize(serializer);
        }
        return sink.toByteArray();
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

    private record SimpleSubsetProxy(
            SimpleStruct value,
            MemberSubsetCodec.MemberSubset subset) implements SerializableStruct {
        @Override
        public Schema schema() {
            return value.schema();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            Schema schema = schema();
            Schema name = schema.member("name");
            Schema age = schema.member("age");
            Schema active = schema.member("active");
            Schema score = schema.member("score");
            Schema createdAt = schema.member("createdAt");
            if (subset.includes(schema, name)) {
                serializer.writeString(name, value.getName());
            }
            if (subset.includes(schema, age)) {
                serializer.writeInteger(age, value.getAge());
            }
            if (subset.includes(schema, active) && value.isActive() != null) {
                serializer.writeBoolean(active, value.isActive());
            }
            if (subset.includes(schema, score) && value.getScore() != null) {
                serializer.writeDouble(score, value.getScore());
            }
            if (subset.includes(schema, createdAt) && value.getCreatedAt() != null) {
                serializer.writeTimestamp(createdAt, value.getCreatedAt());
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return value.getMemberValue(member);
        }
    }

}
