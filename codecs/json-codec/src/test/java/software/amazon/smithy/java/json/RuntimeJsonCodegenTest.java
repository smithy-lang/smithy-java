/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.bench.model.BenchUnion;
import software.amazon.smithy.java.json.bench.model.BlobStruct;
import software.amazon.smithy.java.json.bench.model.Color;
import software.amazon.smithy.java.json.bench.model.ComplexStruct;
import software.amazon.smithy.java.json.bench.model.InnerStruct;
import software.amazon.smithy.java.json.bench.model.NestedStruct;
import software.amazon.smithy.java.json.bench.model.NumericStruct;
import software.amazon.smithy.java.json.bench.model.RecursiveStruct;
import software.amazon.smithy.java.json.bench.model.SimpleStruct;
import software.amazon.smithy.java.json.bench.model.StringStruct;
import software.amazon.smithy.java.json.bench.model.TimestampStruct;
import software.amazon.smithy.java.json.smithy.SmithyGeneratedJsonSerde;

final class RuntimeJsonCodegenTest {
    private static final JsonSettings SETTINGS = JsonSettings.builder()
            .useTimestampFormat(true)
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
        assertThat(result)
                .withFailMessage(() -> serde.diagnostics(SETTINGS).toString())
                .isNotNull();
        byte[] bytes = new byte[result.remaining()];
        result.get(bytes);
        assertThat(serde.deserialize(bytes, SimpleStruct.builder(), SETTINGS)).isEqualTo(value);
        assertThat(serde.scan(bytes, SimpleStruct.builder().schema(), SETTINGS)).isEqualTo(bytes.length);
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"test-\u00e9\",\"age\":42,\"active\":true,"
                        + "\"score\":98.6,\"createdAt\":1736937000}");

        assertThat(serde.diagnostics(SETTINGS).successes()).isEqualTo(1);
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
    void supportsBorrowedOutput() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = SimpleStruct.builder().name("x").age(1).build();
        var sink = new ByteArrayOutputStream();

        assertThat(serde.serializeTo(value, sink, SETTINGS)).isTrue();
        assertThat(sink.toString(StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"x\",\"age\":1}");
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
        assertThat(encoded)
                .withFailMessage(() -> serde.diagnostics(SETTINGS).toString())
                .isNotNull();
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        assertThat(serde.deserialize(bytes, ComplexStruct.builder(), SETTINGS)).isEqualTo(value);
    }

    @Test
    void supportsStructureEncodedSealedInterfaceUnions() {
        var serde = new SmithyGeneratedJsonSerde();
        var value = StructureEncodedUnionModel.Envelope.builder()
                .attribute(new StructureEncodedUnionModel.Value.SMember("value"))
                .build();

        assertRoundTrip(serde, value, StructureEncodedUnionModel.Envelope.builder());
        assertThat(serde.diagnostics(SETTINGS).failures()).isZero();
    }

    private static <T extends SerializableShape> void assertRoundTrip(
            SmithyGeneratedJsonSerde serde,
            T value,
            ShapeBuilder<T> builder
    ) {
        ByteBuffer encoded =
                serde.serialize((SerializableStruct) value, SETTINGS);
        assertThat(encoded)
                .withFailMessage(() -> serde.diagnostics(SETTINGS).toString())
                .isNotNull();
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        assertThat(serde.deserialize(bytes, builder, SETTINGS)).isEqualTo(value);
    }

}
