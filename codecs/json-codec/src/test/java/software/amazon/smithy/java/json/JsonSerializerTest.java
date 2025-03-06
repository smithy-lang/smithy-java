/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

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
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
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
        var document = Document.of(List.of(Document.of("a")));

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
        return List.of(
                Arguments.of(Document.of("a"), "\"a\""),
                Arguments.of(Document.of("a".getBytes(StandardCharsets.UTF_8)), "\"YQ==\""),
                Arguments.of(Document.of((byte) 1), "1"),
                Arguments.of(Document.of((short) 1), "1"),
                Arguments.of(Document.of(1), "1"),
                Arguments.of(Document.of(1L), "1"),
                Arguments.of(Document.of(1.1f), "1.1"),
                Arguments.of(Document.of(Float.NaN), "\"NaN\""),
                Arguments.of(Document.of(Float.POSITIVE_INFINITY), "\"Infinity\""),
                Arguments.of(Document.of(Float.NEGATIVE_INFINITY), "\"-Infinity\""),
                Arguments.of(Document.of(1.1), "1.1"),
                Arguments.of(Document.of(Double.NaN), "\"NaN\""),
                Arguments.of(Document.of(Double.POSITIVE_INFINITY), "\"Infinity\""),
                Arguments.of(Document.of(Double.NEGATIVE_INFINITY), "\"-Infinity\""),
                Arguments.of(Document.of(BigInteger.ZERO), "0"),
                Arguments.of(Document.of(BigDecimal.ONE), "1"),
                Arguments.of(Document.of(true), "true"),
                Arguments.of(Document.of(Instant.EPOCH), "0"),
                Arguments.of(Document.of(List.of(Document.of("a"))), "[\"a\"]"),
                Arguments.of(
                        Document.of(
                                List.of(
                                        Document.of(List.of(Document.of("a"), Document.of("b"))),
                                        Document.of("c"))),
                        "[[\"a\",\"b\"],\"c\"]"),
                Arguments.of(
                        Document.of(List.of(Document.of("a"), Document.of("b"))),
                        "[\"a\",\"b\"]"),
                Arguments.of(Document.of(Map.of("a", Document.of("av"))), "{\"a\":\"av\"}"),
                Arguments.of(Document.of(new LinkedHashMap<>() {
                    {
                        this.put("a", Document.of("av"));
                        this.put("b", Document.of("bv"));
                        this.put("c", Document.of(1));
                        this.put(
                                "d",
                                Document.of(List.of(Document.of(1), Document.of(2))));
                        this.put("e", Document.of(Map.of("ek", Document.of("ek1"))));
                    }
                }), "{\"a\":\"av\",\"b\":\"bv\",\"c\":1,\"d\":[1,2],\"e\":{\"ek\":\"ek1\"}}"));
    }

    @ParameterizedTest
    @MethodSource("configurableTimestampFormatProvider")
    public void configurableTimestampFormat(
            boolean useTimestampFormat,
            String json
    ) throws Exception {
        Schema schema = Schema.createTimestamp(
                ShapeId.from("smithy.example#foo"),
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME));
        try (
                var codec = JsonCodec.builder()
                        .useTimestampFormat(useTimestampFormat)
                        .build();
                var output = new ByteArrayOutputStream()) {
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
                Arguments.of(false, "0"));
    }

    @ParameterizedTest
    @MethodSource("configurableJsonNameProvider")
    public void configurableJsonName(boolean useJsonName, String json) throws Exception {
        try (
                var codec = JsonCodec.builder().useJsonName(useJsonName).build();
                var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(
                        JsonTestData.BIRD,
                        new SerializableStruct() {
                            @Override
                            public Schema schema() {
                                return JsonTestData.BIRD;
                            }

                            @Override
                            public void serializeMembers(ShapeSerializer ser) {
                                ser.writeString(schema().member("name"), "Toucan");
                                ser.writeString(schema().member("color"), "red");
                            }

                            @Override
                            public <T> T getMemberValue(Schema member) {
                                return null;
                            }
                        });
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo(json));
        }
    }

    public static List<Arguments> configurableJsonNameProvider() {
        return List.of(
                Arguments.of(true, "{\"name\":\"Toucan\",\"Color\":\"red\"}"),
                Arguments.of(false, "{\"name\":\"Toucan\",\"color\":\"red\"}"));
    }

    @Test
    public void writesNestedStructures() throws Exception {
        try (var codec = JsonCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(
                        JsonTestData.BIRD,
                        new SerializableStruct() {
                            @Override
                            public Schema schema() {
                                return JsonTestData.BIRD;
                            }

                            @Override
                            public void serializeMembers(ShapeSerializer ser) {
                                ser.writeStruct(schema().member("nested"), new NestedStruct());
                            }

                            @Override
                            public <T> T getMemberValue(Schema member) {
                                return null;
                            }
                        });
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
        var document = Document.of(struct);
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
        var document = Document.of(struct);
        var map = Document.of(Map.of("a", document));
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
        var document = Document.of(struct);
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
        public Schema schema() {
            return JsonTestData.NESTED;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(JsonTestData.NESTED.member("number"), 10);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    private static final class EmptyStruct implements SerializableStruct {
        @Override
        public Schema schema() {
            return JsonTestData.NESTED;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
