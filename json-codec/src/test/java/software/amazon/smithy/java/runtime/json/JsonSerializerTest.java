/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

public class JsonSerializerTest {

    @Test
    public void writesNull() {
        var codec = JsonCodec.builder().build();
        var output = new ByteArrayOutputStream();
        var serializer = codec.createSerializer(output);
        serializer.writeNull(PreludeSchemas.STRING);
        serializer.flush();
        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result, equalTo("null"));
    }

    @Test
    public void writesDocumentsInline() throws Exception {
        var document = Document.createList(List.of(Document.createString("a")));

        try (JsonCodec codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeDocument(PreludeSchemas.DOCUMENT, document);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("[\"a\"]"));
        }
    }

    @ParameterizedTest
    @MethodSource("serializesJsonValuesProvider")
    public void serializesJsonValues(Document value, String expected) throws Exception {
        try (JsonCodec codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                value.serializeContents(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo(expected));
        }
    }

    static List<Arguments> serializesJsonValuesProvider() {
        var now = Instant.now();

        return List.of(
            Arguments.of(Document.createString("a"), "\"a\""),
            Arguments.of(Document.createBlob("a".getBytes(StandardCharsets.UTF_8)), "\"YQ==\""),
            Arguments.of(Document.createByte((byte) 1), "1"),
            Arguments.of(Document.createShort((short) 1), "1"),
            Arguments.of(Document.createInteger(1), "1"),
            Arguments.of(Document.createLong(1L), "1"),
            Arguments.of(Document.createFloat(1.1f), "1.1"),
            Arguments.of(Document.createFloat(Float.NaN), "\"NaN\""),
            Arguments.of(Document.createFloat(Float.POSITIVE_INFINITY), "\"Infinity\""),
            Arguments.of(Document.createFloat(Float.NEGATIVE_INFINITY), "\"-Infinity\""),
            Arguments.of(Document.createDouble(1.1), "1.1"),
            Arguments.of(Document.createDouble(Double.NaN), "\"NaN\""),
            Arguments.of(Document.createDouble(Double.POSITIVE_INFINITY), "\"Infinity\""),
            Arguments.of(Document.createDouble(Double.NEGATIVE_INFINITY), "\"-Infinity\""),
            Arguments.of(Document.createBigInteger(BigInteger.ZERO), "0"),
            Arguments.of(Document.createBigDecimal(BigDecimal.ONE), "1"),
            Arguments.of(Document.createBoolean(true), "true"),
            Arguments.of(Document.createTimestamp(now), Double.toString(((double) now.toEpochMilli()) / 1000)),
            Arguments.of(Document.createList(List.of(Document.createString("a"))), "[\"a\"]"),
            Arguments.of(
                Document.createList(
                    List.of(
                        Document.createList(List.of(Document.createString("a"), Document.createString("b"))),
                        Document.createString("c")
                    )
                ),
                "[[\"a\",\"b\"],\"c\"]"
            ),
            Arguments.of(
                Document.createList(List.of(Document.createString("a"), Document.createString("b"))),
                "[\"a\",\"b\"]"
            ),
            Arguments.of(Document.createStringMap(Map.of("a", Document.createString("av"))), "{\"a\":\"av\"}"),
            Arguments.of(Document.createStringMap(new LinkedHashMap<>() {
                {
                    this.put("a", Document.createString("av"));
                    this.put("b", Document.createString("bv"));
                    this.put("c", Document.createInteger(1));
                    this.put(
                        "d",
                        Document.createList(List.of(Document.createInteger(1), Document.createInteger(2)))
                    );
                    this.put("e", Document.createStringMap(Map.of("ek", Document.createString("ek1"))));
                }
            }), "{\"a\":\"av\",\"b\":\"bv\",\"c\":1,\"d\":[1,2],\"e\":{\"ek\":\"ek1\"}}")
        );
    }

    @ParameterizedTest
    @MethodSource("configurableTimestampFormatProvider")
    public void configurableTimestampFormat(
        boolean useTimestampFormat,
        String json
    ) throws Exception {
        Schema schema = Schema.builder()
            .type(ShapeType.TIMESTAMP)
            .id("smithy.example#foo")
            .traits(new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME))
            .build();
        try (
            var codec = JsonCodec.builder()
                .useTimestampFormat(useTimestampFormat)
                .build(); var output = new ByteArrayOutputStream()
        ) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeTimestamp(schema, Instant.EPOCH);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo(json));
        }
    }

    public static List<Arguments> configurableTimestampFormatProvider() {
        return List.of(
            Arguments.of(true, "\"1970-01-01T00:00:00Z\""),
            Arguments.of(false, "0")
        );
    }

    @ParameterizedTest
    @MethodSource("configurableJsonNameProvider")
    public void configurableJsonName(boolean useJsonName, String json) throws Exception {
        try (
            var codec = JsonCodec.builder().useJsonName(useJsonName).build(); var output = new ByteArrayOutputStream()
        ) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(
                    JsonTestData.BIRD,
                    SerializableStruct.create(JsonTestData.BIRD, (schema, ser) -> {
                        ser.writeString(schema.member("name"), "Toucan");
                        ser.writeString(schema.member("color"), "red");
                    })
                );
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo(json));
        }
    }

    public static List<Arguments> configurableJsonNameProvider() {
        return List.of(
            Arguments.of(true, "{\"name\":\"Toucan\",\"Color\":\"red\"}"),
            Arguments.of(false, "{\"name\":\"Toucan\",\"color\":\"red\"}")
        );
    }

    @Test
    public void writesNestedStructures() throws Exception {
        try (var codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(
                    JsonTestData.BIRD,
                    SerializableStruct.create(JsonTestData.BIRD, (schema, ser) -> {
                        ser.writeStruct(schema.member("nested"), new NestedStruct());
                    })
                );
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"nested\":{\"number\":10}}"));
        }
    }

    @Test
    public void writesStructureUsingSerializableStruct() throws Exception {
        try (var codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(JsonTestData.NESTED, new NestedStruct());
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"number\":10}"));
        }
    }

    @Test
    public void writesDunderTypeAndMoreMembers() throws Exception {
        var struct = new NestedStruct();
        var document = Document.createTyped(struct);
        try (var codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                document.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"__type\":\"smithy.example#Nested\",\"number\":10}"));
        }
    }

    @Test
    public void writesNestedDunderType() throws Exception {
        var struct = new NestedStruct();
        var document = Document.createTyped(struct);
        var map = Document.createStringMap(Map.of("a", document));
        try (var codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                map.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"a\":{\"__type\":\"smithy.example#Nested\",\"number\":10}}"));
        }
    }

    @Test
    public void writesDunderTypeForEmptyStruct() throws Exception {
        var struct = new EmptyStruct();
        var document = Document.createTyped(struct);
        try (var codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                document.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"__type\":\"smithy.example#Nested\"}"));
        }
    }

    private static final class NestedStruct implements SerializableStruct {
        @Override
        public void serialize(ShapeSerializer encoder) {
            encoder.writeStruct(JsonTestData.NESTED, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(JsonTestData.NESTED.member("number"), 10);
        }
    }

    private static final class EmptyStruct implements SerializableStruct {
        @Override
        public void serialize(ShapeSerializer encoder) {
            encoder.writeStruct(JsonTestData.NESTED, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}
    }
}
