/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.codegen.rt.WriterContext;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.codegen.test.model.FullStruct;
import software.amazon.smithy.java.json.codegen.test.model.ListStruct;
import software.amazon.smithy.java.json.codegen.test.model.MapStruct;
import software.amazon.smithy.java.json.codegen.test.model.SimpleStruct;

/**
 * Tests for the JSON codegen serializer and deserializer.
 */
public class JsonCodegenTest {

    private static SpecializedCodecRegistry registry;
    private static JsonCodec jsonCodec;

    @BeforeAll
    static void setup() {
        registry = new SpecializedCodecRegistry(new JsonCodecProfile());
        jsonCodec = JsonCodec.builder().useTimestampFormat(true).build();

        // Warmup all test shapes
        registry.warmup(SimpleStruct.$SCHEMA, SimpleStruct.class);
        registry.warmup(ListStruct.$SCHEMA, ListStruct.class);
        registry.warmup(MapStruct.$SCHEMA, MapStruct.class);
        registry.warmup(FullStruct.$SCHEMA, FullStruct.class);
    }

    // ---- Serialization Tests ----

    @Test
    void testSimpleStructSerialization() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Alice")
                .age(30)
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"name\":\"Alice\""), "Should contain name field: " + json);
        assertTrue(json.contains("\"age\":30"), "Should contain age field: " + json);
        assertTrue(json.startsWith("{"), "Should start with {: " + json);
        assertTrue(json.endsWith("}"), "Should end with }: " + json);
    }

    @Test
    void testSimpleStructWithOptionalFields() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Bob")
                .age(25)
                .nickname("Bobby")
                .score(99.5)
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"name\":\"Bob\""), "name: " + json);
        assertTrue(json.contains("\"age\":25"), "age: " + json);
        assertTrue(json.contains("\"nickname\":\"Bobby\""), "nickname: " + json);
        assertTrue(json.contains("\"score\":99.5"), "score: " + json);
    }

    @Test
    void testSimpleStructOmitsNullOptionals() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Charlie")
                .age(40)
                .build();

        String json = serializeToJson(obj);
        assertTrue(!json.contains("nickname"), "Should not contain nickname: " + json);
        assertTrue(!json.contains("score"), "Should not contain score: " + json);
    }

    @Test
    void testListStructSerialization() {
        ListStruct obj = ListStruct.builder()
                .names(List.of("Alice", "Bob", "Charlie"))
                .counts(List.of(1, 2, 3))
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"names\":[\"Alice\",\"Bob\",\"Charlie\"]"), "names: " + json);
        assertTrue(json.contains("\"counts\":[1,2,3]"), "counts: " + json);
    }

    @Test
    void testMapStructSerialization() {
        MapStruct obj = MapStruct.builder()
                .tags(Map.of("env", "prod", "team", "alpha"))
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"tags\":{"), "tags: " + json);
        assertTrue(json.contains("\"env\":\"prod\""), "env: " + json);
        assertTrue(json.contains("\"team\":\"alpha\""), "team: " + json);
    }

    // ---- Deserialization Tests ----

    @Test
    void testSimpleStructDeserialization() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        SimpleStruct obj = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);

        assertEquals("Alice", obj.getName());
        assertEquals(30, obj.getAge());
    }

    @Test
    void testSimpleStructDeserializationWithOptionals() {
        String json = "{\"name\":\"Bob\",\"age\":25,\"nickname\":\"Bobby\",\"score\":99.5}";
        SimpleStruct obj = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);

        assertEquals("Bob", obj.getName());
        assertEquals(25, obj.getAge());
        assertEquals("Bobby", obj.getNickname());
        assertEquals(99.5, obj.getScore());
    }

    @Test
    void testSimpleStructDeserializationUnknownFields() {
        String json = "{\"name\":\"Alice\",\"unknown\":true,\"age\":30}";
        SimpleStruct obj = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);

        assertEquals("Alice", obj.getName());
        assertEquals(30, obj.getAge());
    }

    @Test
    void testSimpleStructDeserializationOutOfOrder() {
        String json = "{\"age\":30,\"name\":\"Alice\"}";
        SimpleStruct obj = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);

        assertEquals("Alice", obj.getName());
        assertEquals(30, obj.getAge());
    }

    @Test
    void testListStructDeserialization() {
        String json = "{\"names\":[\"Alice\",\"Bob\"],\"counts\":[10,20,30]}";
        ListStruct obj = deserialize(json, ListStruct.builder(), ListStruct.class);

        assertEquals(List.of("Alice", "Bob"), obj.getNames());
        assertEquals(List.of(10, 20, 30), obj.getCounts());
    }

    @Test
    void testMapStructDeserialization() {
        String json = "{\"tags\":{\"env\":\"prod\",\"team\":\"alpha\"}}";
        MapStruct obj = deserialize(json, MapStruct.builder(), MapStruct.class);

        assertEquals("prod", obj.getTags().get("env"));
        assertEquals("alpha", obj.getTags().get("team"));
    }

    // ---- Roundtrip Tests ----

    @Test
    void testSimpleStructRoundtrip() {
        SimpleStruct original = SimpleStruct.builder()
                .name("Alice")
                .age(30)
                .nickname("Allie")
                .score(95.5)
                .build();

        String json = serializeToJson(original);
        SimpleStruct deserialized = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getAge(), deserialized.getAge());
        assertEquals(original.getNickname(), deserialized.getNickname());
        assertEquals(original.getScore(), deserialized.getScore());
    }

    @Test
    void testListStructRoundtrip() {
        ListStruct original = ListStruct.builder()
                .names(List.of("X", "Y", "Z"))
                .counts(List.of(100, 200))
                .build();

        String json = serializeToJson(original);
        ListStruct deserialized = deserialize(json, ListStruct.builder(), ListStruct.class);

        assertEquals(original.getNames(), deserialized.getNames());
        assertEquals(original.getCounts(), deserialized.getCounts());
    }

    // ---- Dispatch Codec Comparison Tests ----

    @Test
    void testSimpleStructMatchesDispatchCodec() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Alice")
                .age(30)
                .build();

        byte[] codegenBytes = serializeToBytes(obj);
        byte[] dispatchBytes = serializeWithDispatchCodec(obj);

        // Both should produce valid JSON that parses to the same structure.
        // Field ordering may differ between codegen (optimized order) and dispatch (model order),
        // so we compare by round-tripping both through deserialization instead of byte comparison.
        SimpleStruct fromCodegen = jsonCodec.deserializeShape(codegenBytes, SimpleStruct.builder());
        SimpleStruct fromDispatch = jsonCodec.deserializeShape(dispatchBytes, SimpleStruct.builder());

        assertEquals(fromDispatch.getName(), fromCodegen.getName());
        assertEquals(fromDispatch.getAge(), fromCodegen.getAge());
    }

    @Test
    void testSimpleStructWithOptionalsMatchesDispatchCodec() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Bob")
                .age(25)
                .nickname("Bobby")
                .score(88.8)
                .build();

        byte[] codegenBytes = serializeToBytes(obj);
        byte[] dispatchBytes = serializeWithDispatchCodec(obj);

        SimpleStruct fromCodegen = jsonCodec.deserializeShape(codegenBytes, SimpleStruct.builder());
        SimpleStruct fromDispatch = jsonCodec.deserializeShape(dispatchBytes, SimpleStruct.builder());

        assertEquals(fromDispatch.getName(), fromCodegen.getName());
        assertEquals(fromDispatch.getAge(), fromCodegen.getAge());
        assertEquals(fromDispatch.getNickname(), fromCodegen.getNickname());
        assertEquals(fromDispatch.getScore(), fromCodegen.getScore());
    }

    // ---- Stress Test ----

    @Test
    void testSerializationStress() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Stress")
                .age(42)
                .nickname("Stressy")
                .score(3.14)
                .build();

        // 100k iterations to validate correctness under repeated usage
        for (int i = 0; i < 100_000; i++) {
            String json = serializeToJson(obj);
            assertNotNull(json);
            assertTrue(json.contains("\"name\":\"Stress\""));
        }
    }

    @Test
    void testDeserializationStress() {
        String json = "{\"name\":\"Stress\",\"age\":42,\"nickname\":\"Stressy\",\"score\":3.14}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        // 100k iterations to validate correctness under repeated usage
        for (int i = 0; i < 100_000; i++) {
            SimpleStruct obj = deserialize(bytes, SimpleStruct.builder(), SimpleStruct.class);
            assertEquals("Stress", obj.getName());
            assertEquals(42, obj.getAge());
        }
    }

    // ---- Full Struct Tests ----

    @Test
    void testFullStructSerialization() {
        FullStruct obj = FullStruct.builder()
                .name("Full")
                .age(99)
                .active(true)
                .score(1.5)
                .count(1000L)
                .rating(4.5f)
                .data(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)))
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .bigNum(new BigInteger("12345678901234567890"))
                .precise(new BigDecimal("99.99"))
                .tags(List.of("a", "b"))
                .metadata(Map.of("k1", "v1"))
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"name\":\"Full\""), "name: " + json);
        assertTrue(json.contains("\"age\":99"), "age: " + json);
        assertTrue(json.contains("\"active\":true"), "active: " + json);
    }

    @Test
    void testFullStructDeserialization() {
        String json = "{\"name\":\"Full\",\"age\":99,\"active\":true,"
                + "\"score\":1.5,\"count\":1000,\"rating\":4.5,"
                + "\"createdAt\":\"2025-01-15T10:30:00Z\","
                + "\"bigNum\":12345678901234567890,"
                + "\"precise\":99.99,"
                + "\"tags\":[\"a\",\"b\"],"
                + "\"metadata\":{\"k1\":\"v1\"}}";

        FullStruct obj = deserialize(json, FullStruct.builder(), FullStruct.class);

        assertEquals("Full", obj.getName());
        assertEquals(99, obj.getAge());
        assertTrue(obj.isActive());
        assertEquals(1.5, obj.getScore());
        assertEquals(1000L, obj.getCount());
        assertEquals(4.5f, obj.getRating());
        assertEquals(Instant.parse("2025-01-15T10:30:00Z"), obj.getCreatedAt());
        assertEquals(new BigInteger("12345678901234567890"), obj.getBigNum());
        assertEquals(new BigDecimal("99.99"), obj.getPrecise());
        assertEquals(List.of("a", "b"), obj.getTags());
        assertEquals("v1", obj.getMetadata().get("k1"));
    }

    // ---- Null handling in deserialization ----

    @Test
    void testDeserializationHandlesNullValues() {
        String json = "{\"name\":\"Test\",\"age\":1,\"nickname\":null,\"score\":null}";
        SimpleStruct obj = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);
        assertEquals("Test", obj.getName());
        assertEquals(1, obj.getAge());
    }

    // ---- Helper methods ----

    private String serializeToJson(SerializableStruct obj) {
        byte[] bytes = serializeToBytes(obj);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] serializeToBytes(SerializableStruct obj) {
        WriterContext ctx = WriterContext.acquire(registry);
        try {
            registry.getSerializer(obj.schema(), obj.getClass()).serialize(obj, ctx);
            return ctx.toByteArray();
        } finally {
            WriterContext.release(ctx);
        }
    }

    private byte[] serializeWithDispatchCodec(SerializableStruct obj) {
        ByteBuffer bb = jsonCodec.serialize(obj);
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        return bytes;
    }

    @SuppressWarnings("unchecked")
    private <T extends SerializableStruct> T deserialize(
            String json,
            Object builder,
            Class<T> shapeClass
    ) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return deserialize(bytes, builder, shapeClass);
    }

    @SuppressWarnings("unchecked")
    private <T extends SerializableStruct> T deserialize(
            byte[] bytes,
            Object builder,
            Class<T> shapeClass
    ) {
        JsonReaderContext ctx = new JsonReaderContext(bytes, 0, bytes.length, registry);
        return (T) registry.getDeserializer(
                ((software.amazon.smithy.java.core.schema.ShapeBuilder<?>) builder).schema(),
                shapeClass).deserialize(ctx, (software.amazon.smithy.java.core.schema.ShapeBuilder<?>) builder);
    }
}
