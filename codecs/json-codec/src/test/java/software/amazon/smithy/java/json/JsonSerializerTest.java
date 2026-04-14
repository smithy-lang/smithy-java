/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
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

    @PerProvider
    public void writesIntegerBoundaryValues(JsonSerdeProvider provider) throws Exception {
        var testCases = Map.of(
                0,
                "0",
                1,
                "1",
                -1,
                "-1",
                Integer.MIN_VALUE,
                "-2147483648",
                Integer.MAX_VALUE,
                "2147483647",
                12,
                "12",
                123,
                "123",
                1234,
                "1234",
                12345,
                "12345",
                1234567890,
                "1234567890");

        for (var entry : testCases.entrySet()) {
            try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
                try (var serializer = codec.createSerializer(output)) {
                    serializer.writeInteger(PreludeSchemas.INTEGER, entry.getKey());
                }
                assertThat(output.toString(StandardCharsets.UTF_8), equalTo(entry.getValue()));
            }
        }
    }

    @PerProvider
    public void writesLongBoundaryValues(JsonSerdeProvider provider) throws Exception {
        var testCases = Map.of(
                0L,
                "0",
                1L,
                "1",
                -1L,
                "-1",
                Long.MIN_VALUE,
                "-9223372036854775808",
                Long.MAX_VALUE,
                "9223372036854775807",
                1000000L,
                "1000000", // fits in int range
                3000000000L,
                "3000000000", // exceeds int range
                10000000000L,
                "10000000000", // 11 digits
                1000000000000000000L,
                "1000000000000000000"); // 19 digits

        for (var entry : testCases.entrySet()) {
            try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
                try (var serializer = codec.createSerializer(output)) {
                    serializer.writeLong(PreludeSchemas.LONG, entry.getKey());
                }
                assertThat(output.toString(StandardCharsets.UTF_8), equalTo(entry.getValue()));
            }
        }
    }

    @PerProvider
    public void writesStringWithControlChars(JsonSerdeProvider provider) throws Exception {
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeString(PreludeSchemas.STRING, "a\u0000b\u001Fc");
            }
            String result = output.toString(StandardCharsets.UTF_8);
            // Verify control chars are escaped (case may vary between providers)
            String lower = result.toLowerCase();
            assertThat(lower, containsString("\\u0000"));
            assertThat(lower, containsString("\\u001f"));
        }
    }

    @PerProvider
    public void writesStringWithUnicode(JsonSerdeProvider provider) throws Exception {
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeString(PreludeSchemas.STRING, "\u00e9\u4e2d\uD83D\uDE00");
            }
            String result = output.toString(StandardCharsets.UTF_8);
            var de = codec.createDeserializer(result.getBytes(StandardCharsets.UTF_8));
            assertThat(de.readString(PreludeSchemas.STRING), equalTo("\u00e9\u4e2d\uD83D\uDE00"));
        }
    }

    @PerProvider
    public void writesEmptyString(JsonSerdeProvider provider) throws Exception {
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeString(PreludeSchemas.STRING, "");
            }
            assertThat(output.toString(StandardCharsets.UTF_8), equalTo("\"\""));
        }
    }

    @PerProvider
    public void writesBigDecimalVariousScales(JsonSerdeProvider provider) throws Exception {
        var testCases = List.of(
                new BigDecimal("42"), // scale=0
                new BigDecimal("99999.99999"), // positive scale, long unscaled
                new BigDecimal("-123.45"), // negative unscaled
                new BigDecimal("1.005"), // leading zeros in fraction
                new BigDecimal("1E+10")); // negative scale

        for (var bd : testCases) {
            try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
                try (var serializer = codec.createSerializer(output)) {
                    serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, bd);
                }
                String result = output.toString(StandardCharsets.UTF_8);
                // Parse back and verify equivalence
                assertThat(new BigDecimal(result).compareTo(bd) == 0, equalTo(true));
            }
        }
    }

    @PerProvider
    public void handlesLargeStringsWithBufferGrowth(JsonSerdeProvider provider) throws Exception {
        // String > 8192 bytes to trigger buffer growth
        String large = "x".repeat(10000);
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeString(PreludeSchemas.STRING, large);
            }
            var de = codec.createDeserializer(output.toByteArray());
            assertThat(de.readString(PreludeSchemas.STRING), equalTo(large));
        }
    }

    @PerProvider
    public void doubleSerializationRoundtrips(JsonSerdeProvider provider) throws Exception {
        for (double v : new double[] {0.0, 1.0, -1.0, 3.14, 1e100, Double.MIN_VALUE, Double.MAX_VALUE}) {
            try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
                try (var serializer = codec.createSerializer(output)) {
                    serializer.writeDouble(PreludeSchemas.DOUBLE, v);
                }
                String json = output.toString(StandardCharsets.UTF_8);
                assertThat(Double.parseDouble(json), equalTo(v));
            }
        }
    }

    @PerProvider
    public void floatSerializationRoundtrips(JsonSerdeProvider provider) throws Exception {
        for (float v : new float[] {0.0f, 1.0f, -1.0f, 3.14f, Float.MIN_VALUE, Float.MAX_VALUE}) {
            try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
                try (var serializer = codec.createSerializer(output)) {
                    serializer.writeFloat(PreludeSchemas.FLOAT, v);
                }
                String json = output.toString(StandardCharsets.UTF_8);
                assertThat(Float.parseFloat(json), equalTo(v));
            }
        }
    }

    @Test
    public void smithyProviderNameAndPriority() {
        var provider = new software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider();
        assertThat(provider.getName(), equalTo("smithy"));
        assertThat(provider.getPriority(), equalTo(5));
    }

    @Test
    public void smithyProviderDirectSerializeReturnsBuffer() {
        var provider = new software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider();
        var struct = new NestedStruct();
        java.nio.ByteBuffer result = provider.serialize(struct, JsonSettings.builder().build());
        assertThat(result != null, equalTo(true));
        assertThat(result.remaining() > 0, equalTo(true));
    }

    @Test
    public void smithyProviderPrettyPrintFallsBackToJackson() throws Exception {
        var provider = new software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider();
        var struct = new NestedStruct();
        var settings = JsonSettings.builder().prettyPrint(true).build();
        // serialize(ByteBuffer) path
        java.nio.ByteBuffer result = provider.serialize(struct, settings);
        assertThat(result.remaining() > 0, equalTo(true));
        // newSerializer path
        var output = new ByteArrayOutputStream();
        var ser = provider.newSerializer(output, settings);
        ser.writeInteger(PreludeSchemas.INTEGER, 42);
        ser.close();
        assertThat(output.toString(StandardCharsets.UTF_8), equalTo("42"));
    }

    @Test
    public void smithyProviderNonArrayBackedByteBuffer() {
        var provider = new software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider();
        var settings = JsonSettings.builder().build();
        // Direct ByteBuffer is not array-backed
        java.nio.ByteBuffer direct = java.nio.ByteBuffer.allocateDirect(4);
        direct.put("\"hi\"".getBytes(StandardCharsets.UTF_8));
        direct.flip();
        var de = provider.newDeserializer(direct, settings);
        assertThat(de.readString(PreludeSchemas.STRING), equalTo("hi"));
    }

    @PerProvider
    public void writesIntegersOfAllDigitCounts(JsonSerdeProvider provider) throws Exception {
        // Cover digitCount branches for 6 and 8 digits
        int[] values = {100000, 999999, 10000000, 99999999};
        for (int v : values) {
            try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
                try (var serializer = codec.createSerializer(output)) {
                    serializer.writeInteger(PreludeSchemas.INTEGER, v);
                }
                assertThat(output.toString(StandardCharsets.UTF_8), equalTo(String.valueOf(v)));
            }
        }
    }

    @PerProvider
    public void writesLongsOfAllDigitCounts(JsonSerdeProvider provider) throws Exception {
        // Cover digitCountLong branches for 12-18 digits
        long[] values = {
                100000000000L, // 12 digits
                1000000000000L, // 13 digits
                10000000000000L, // 14 digits
                100000000000000L, // 15 digits
                1000000000000000L, // 16 digits
                10000000000000000L, // 17 digits
                100000000000000000L // 18 digits
        };
        for (long v : values) {
            try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
                try (var serializer = codec.createSerializer(output)) {
                    serializer.writeLong(PreludeSchemas.LONG, v);
                }
                assertThat(output.toString(StandardCharsets.UTF_8), equalTo(String.valueOf(v)));
            }
        }
    }

    @PerProvider
    public void writesStringWithLoneSurrogate(JsonSerdeProvider provider) throws Exception {
        String loneSurrogate = "a\uD800b";
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeString(PreludeSchemas.STRING, loneSurrogate);
            }
            String result = output.toString(StandardCharsets.UTF_8);
            // Verify the lone surrogate is escaped, not written as raw bytes
            assertThat(result.toLowerCase().contains("\\ud800"), equalTo(true));
        }
    }

    @PerProvider
    public void writesBlobFromByteArray(JsonSerdeProvider provider) throws Exception {
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeBlob(PreludeSchemas.BLOB, "hello".getBytes(StandardCharsets.UTF_8));
            }
            String result = output.toString(StandardCharsets.UTF_8);
            // Should be base64 of "hello" = "aGVsbG8="
            assertThat(result, equalTo("\"aGVsbG8=\""));
        }
    }

    @PerProvider
    public void writesBlobFromDirectByteBuffer(JsonSerdeProvider provider) throws Exception {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        java.nio.ByteBuffer direct = java.nio.ByteBuffer.allocateDirect(data.length);
        direct.put(data);
        direct.flip();
        try (var codec = codec(provider); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeBlob(PreludeSchemas.BLOB, direct);
            }
            assertThat(output.toString(StandardCharsets.UTF_8), equalTo("\"aGVsbG8=\""));
        }
    }

    @Test
    public void documentStructAtDepthLimitThrowsSerializationException() {
        var innerStruct = new NestedStruct();
        Document innerDoc = Document.of(innerStruct);

        Document nested = innerDoc;
        for (int i = 0; i < 64; i++) {
            // Wrap in a struct document via a SerializableStruct that contains the nested doc
            final Document childDoc = nested;
            var wrapper = new SerializableStruct() {
                @Override
                public Schema schema() {
                    return JsonTestData.NESTED;
                }

                @Override
                public void serializeMembers(ShapeSerializer ser) {
                    ser.writeDocument(JsonTestData.NESTED.member("number"), childDoc);
                }

                @Override
                public <T> T getMemberValue(Schema member) {
                    return null;
                }
            };
            nested = Document.of(wrapper);
        }

        Document deepDoc = nested;
        Assertions.assertThrows(
                software.amazon.smithy.java.core.serde.SerializationException.class,
                () -> {
                    try (var codec = JsonCodec.builder()
                            .overrideSerdeProvider(
                                    new software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider())
                            .build();
                            var output = new ByteArrayOutputStream()) {
                        try (var serializer = codec.createSerializer(output)) {
                            deepDoc.serialize(serializer);
                        }
                    }
                });
    }

    @Test
    public void smithySerializerHandlesIOExceptionOnFlush() {
        var settings = JsonSettings.builder().build();
        var failingStream = new java.io.OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                throw new java.io.IOException("simulated");
            }
        };
        var provider = new software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider();
        var serializer = provider.newSerializer(failingStream, settings);
        serializer.writeInteger(PreludeSchemas.INTEGER, 42);
        Assertions.assertThrows(
                software.amazon.smithy.java.core.serde.SerializationException.class,
                serializer::flush);
    }

    @Test
    public void smithySerializerHandlesIOExceptionOnClose() {
        var settings = JsonSettings.builder().build();
        var failingStream = new java.io.OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                throw new java.io.IOException("simulated");
            }
        };
        var provider = new software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider();
        var serializer = provider.newSerializer(failingStream, settings);
        serializer.writeInteger(PreludeSchemas.INTEGER, 42);
        Assertions.assertThrows(
                software.amazon.smithy.java.core.serde.SerializationException.class,
                serializer::close);
    }
}
