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

public class JsonSerializerTest extends ProviderTestBase {

    @PerProvider
    public void writesNull(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var output = new ByteArrayOutputStream();
        var serializer = codec.createSerializer(output);
        serializer.writeNull(PreludeSchemas.STRING);
        serializer.flush();
        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result, equalTo("null"));
    }

    @PerProvider
    public void writesDocumentsInline(JsonSerdeProvider provider) throws Exception {
        var document = Document.of(List.of(Document.of("a")));

        try (JsonCodec codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeDocument(PreludeSchemas.DOCUMENT, document);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("[\"a\"]"));
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

    // Cross-product: each provider x each json value
    static List<Arguments> serializesJsonValuesWithProvider() {
        var values = serializesJsonValuesProvider();
        var provs = providers();
        List<Arguments> result = new java.util.ArrayList<>();
        for (var prov : provs) {
            for (var val : values) {
                result.add(Arguments.of(prov.get()[0], val.get()[0], val.get()[1]));
            }
        }
        return result;
    }

    @ParameterizedTest
    @MethodSource("serializesJsonValuesWithProvider")
    public void serializesJsonValues(JsonSerdeProvider provider, Document value, String expected) throws Exception {
        try (JsonCodec codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                value.serializeContents(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo(expected));
        }
    }

    // Cross-product: each provider x each timestamp config
    static List<Arguments> configurableTimestampFormatWithProvider() {
        var configs = List.of(
                Arguments.of(true, "\"1970-01-01T00:00:00Z\""),
                Arguments.of(false, "0"));
        var provs = providers();
        List<Arguments> result = new java.util.ArrayList<>();
        for (var prov : provs) {
            for (var cfg : configs) {
                result.add(Arguments.of(prov.get()[0], cfg.get()[0], cfg.get()[1]));
            }
        }
        return result;
    }

    @ParameterizedTest
    @MethodSource("configurableTimestampFormatWithProvider")
    public void configurableTimestampFormat(
            JsonSerdeProvider provider,
            boolean useTimestampFormat,
            String json
    ) throws Exception {
        Schema schema = Schema.createTimestamp(
                ShapeId.from("smithy.example#foo"),
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME));
        try (
                var codec = codecBuilder(provider)
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

    // Cross-product: each provider x each jsonName config
    static List<Arguments> configurableJsonNameWithProvider() {
        var configs = List.of(
                Arguments.of(true, "{\"name\":\"Toucan\",\"Color\":\"red\"}"),
                Arguments.of(false, "{\"name\":\"Toucan\",\"color\":\"red\"}"));
        var provs = providers();
        List<Arguments> result = new java.util.ArrayList<>();
        for (var prov : provs) {
            for (var cfg : configs) {
                result.add(Arguments.of(prov.get()[0], cfg.get()[0], cfg.get()[1]));
            }
        }
        return result;
    }

    @ParameterizedTest
    @MethodSource("configurableJsonNameWithProvider")
    public void configurableJsonName(JsonSerdeProvider provider, boolean useJsonName, String json) throws Exception {
        try (
                var codec = codecBuilder(provider).useJsonName(useJsonName).build();
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

    @PerProvider
    public void writesNestedStructures(JsonSerdeProvider provider) throws Exception {
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
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

    @PerProvider
    public void writesStructureUsingSerializableStruct(JsonSerdeProvider provider) throws Exception {
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(JsonTestData.NESTED, new NestedStruct());
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"number\":10}"));
        }
    }

    @PerProvider
    public void writesDunderTypeAndMoreMembers(JsonSerdeProvider provider) throws Exception {
        var struct = new NestedStruct();
        var document = Document.of(struct);
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                document.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"__type\":\"smithy.example#Nested\",\"number\":10}"));
        }
    }

    @PerProvider
    public void writesNestedDunderType(JsonSerdeProvider provider) throws Exception {
        var struct = new NestedStruct();
        var document = Document.of(struct);
        var map = Document.of(Map.of("a", document));
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                map.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"a\":{\"__type\":\"smithy.example#Nested\",\"number\":10}}"));
        }
    }

    @PerProvider
    public void writesDunderTypeForEmptyStruct(JsonSerdeProvider provider) throws Exception {
        var struct = new EmptyStruct();
        var document = Document.of(struct);
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                document.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"__type\":\"smithy.example#Nested\"}"));
        }
    }

    @Test
    public void testPrettyPrinting() throws Exception {
        // Pretty printing delegates to Jackson regardless of provider, so test once
        try (var codec = JsonCodec.builder().prettyPrint(true).build(); var output = new ByteArrayOutputStream()) {
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
            String expectedFormat = """
                    {
                      "name" : "Toucan",
                      "color" : "red"
                    }""";
            assertThat(result.replace("\r", ""), equalTo(expectedFormat));
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
