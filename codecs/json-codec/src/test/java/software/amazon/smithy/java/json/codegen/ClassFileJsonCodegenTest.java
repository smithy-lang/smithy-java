/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.bench.model.Color;
import software.amazon.smithy.java.json.bench.model.ComplexStruct;
import software.amazon.smithy.java.json.bench.model.DocumentHolder;
import software.amazon.smithy.java.json.bench.model.EnumStruct;
import software.amazon.smithy.java.json.bench.model.InnerStruct;
import software.amazon.smithy.java.json.bench.model.JsonNameStruct;
import software.amazon.smithy.java.json.bench.model.NestedStruct;
import software.amazon.smithy.java.json.bench.model.Priority;
import software.amazon.smithy.java.json.bench.model.RecursiveStruct;
import software.amazon.smithy.java.json.bench.model.SimpleStruct;
import software.amazon.smithy.java.json.bench.model.SparseStruct;
import software.amazon.smithy.java.json.bench.model.TimestampStruct;
import software.amazon.smithy.java.json.bench.model.UnionHolder;
import software.amazon.smithy.java.json.smithy.JsonWriterContext;

public class ClassFileJsonCodegenTest {

    private static SpecializedCodecRegistry registry;
    private static JsonCodec jsonCodec;

    @BeforeAll
    static void setup() {
        registry = new SpecializedCodecRegistry(new ClassFileJsonCodecProfile());
        jsonCodec = JsonCodec.builder().useTimestampFormat(true).build();

        registry.warmup(SimpleStruct.$SCHEMA, SimpleStruct.class);
        registry.warmup(ComplexStruct.$SCHEMA, ComplexStruct.class);
        registry.warmup(JsonNameStruct.$SCHEMA, JsonNameStruct.class);
        registry.warmup(NestedStruct.$SCHEMA, NestedStruct.class);
        registry.warmup(InnerStruct.$SCHEMA, InnerStruct.class);
        registry.warmup(RecursiveStruct.$SCHEMA, RecursiveStruct.class);
        registry.warmup(EnumStruct.$SCHEMA, EnumStruct.class);
        registry.warmup(SparseStruct.$SCHEMA, SparseStruct.class);
        registry.warmup(DocumentHolder.$SCHEMA, DocumentHolder.class);
        registry.warmup(TimestampStruct.$SCHEMA, TimestampStruct.class);
        registry.warmup(UnionHolder.$SCHEMA, UnionHolder.class);
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
                .active(true)
                .score(99.5)
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"name\":\"Bob\""), "name: " + json);
        assertTrue(json.contains("\"age\":25"), "age: " + json);
        assertTrue(json.contains("\"active\":true"), "active: " + json);
        assertTrue(json.contains("\"score\":99.5"), "score: " + json);
    }

    @Test
    void testSimpleStructOmitsNullOptionals() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Charlie")
                .age(40)
                .build();

        String json = serializeToJson(obj);
        assertTrue(!json.contains("\"active\""), "Should not contain active: " + json);
        assertTrue(!json.contains("\"score\""), "Should not contain score: " + json);
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
        String json = "{\"name\":\"Bob\",\"age\":25,\"active\":true,\"score\":99.5}";
        SimpleStruct obj = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);

        assertEquals("Bob", obj.getName());
        assertEquals(25, obj.getAge());
        assertTrue(obj.isActive());
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

    // ---- Roundtrip Tests ----

    @Test
    void testSimpleStructRoundtrip() {
        SimpleStruct original = SimpleStruct.builder()
                .name("Alice")
                .age(30)
                .active(true)
                .score(95.5)
                .build();

        String json = serializeToJson(original);
        SimpleStruct deserialized = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getAge(), deserialized.getAge());
        assertEquals(original.isActive(), deserialized.isActive());
        assertEquals(original.getScore(), deserialized.getScore());
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

        SimpleStruct fromCodegen = jsonCodec.deserializeShape(codegenBytes, SimpleStruct.builder());
        SimpleStruct fromDispatch = jsonCodec.deserializeShape(dispatchBytes, SimpleStruct.builder());

        assertEquals(fromDispatch.getName(), fromCodegen.getName());
        assertEquals(fromDispatch.getAge(), fromCodegen.getAge());
    }

    // ---- Complex Struct Tests ----

    @Test
    void testComplexStructSerialization() {
        ComplexStruct obj = ComplexStruct.builder()
                .id("Full")
                .count(99)
                .enabled(true)
                .score(4.5f)
                .ratio(1.5)
                .bigCount(1000L)
                .payload(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)))
                .updatedAt(Instant.parse("2025-01-15T10:30:00Z"))
                .bigIntValue(new BigInteger("12345678901234567890"))
                .bigDecValue(new BigDecimal("99.99"))
                .tags(List.of("a", "b"))
                .metadata(Map.of("k1", "v1"))
                .nested(NestedStruct.builder().field1("f1").field2(1).build())
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"id\":\"Full\""), "id: " + json);
        assertTrue(json.contains("\"count\":99"), "count: " + json);
        assertTrue(json.contains("\"enabled\":true"), "enabled: " + json);
    }

    @Test
    void testComplexStructRoundtrip() {
        ComplexStruct original = ComplexStruct.builder()
                .id("Roundtrip")
                .count(42)
                .enabled(false)
                .ratio(3.14)
                .score(2.5f)
                .bigCount(999L)
                .payload(ByteBuffer.wrap("world".getBytes(StandardCharsets.UTF_8)))
                .updatedAt(Instant.parse("2025-06-01T00:00:00Z"))
                .bigIntValue(new BigInteger("98765432109876543210"))
                .bigDecValue(new BigDecimal("12.34"))
                .tags(List.of("x", "y", "z"))
                .metadata(Map.of("a", "1", "b", "2"))
                .nested(NestedStruct.builder().field1("f1").field2(1).build())
                .build();

        String json = serializeToJson(original);
        ComplexStruct deserialized = deserialize(json, ComplexStruct.builder(), ComplexStruct.class);

        assertEquals(original.getId(), deserialized.getId());
        assertEquals(original.getCount(), deserialized.getCount());
        assertEquals(original.isEnabled(), deserialized.isEnabled());
        assertEquals(original.getRatio(), deserialized.getRatio());
        assertEquals(original.getBigCount(), deserialized.getBigCount());
        assertEquals(original.getUpdatedAt(), deserialized.getUpdatedAt());
        assertEquals(original.getBigIntValue(), deserialized.getBigIntValue());
        assertEquals(original.getBigDecValue(), deserialized.getBigDecValue());
        assertEquals(original.getTags(), deserialized.getTags());
        assertEquals(original.getMetadata(), deserialized.getMetadata());
    }

    // ---- Null handling ----

    @Test
    void testDeserializationHandlesNullValues() {
        String json = "{\"name\":\"Test\",\"age\":1,\"active\":null,\"score\":null}";
        SimpleStruct obj = deserialize(json, SimpleStruct.builder(), SimpleStruct.class);
        assertEquals("Test", obj.getName());
        assertEquals(1, obj.getAge());
    }

    // ---- JsonName Tests ----

    @Test
    void testJsonNameSerializationUsesMemberName() {
        JsonNameStruct obj = JsonNameStruct.builder()
                .id("test-id")
                .displayName("Alice")
                .normalField("normal")
                .build();

        String json = serializeToJson(obj);
        assertTrue(json.contains("\"id\":\"test-id\""), "Should use memberName by default: " + json);
        assertTrue(json.contains("\"displayName\":\"Alice\""), "Should use memberName by default: " + json);
        assertTrue(json.contains("\"normalField\":\"normal\""), "normalField: " + json);
    }

    @Test
    void testJsonNameSerializationUsesJsonName() {
        JsonNameStruct obj = JsonNameStruct.builder()
                .id("test-id")
                .displayName("Alice")
                .normalField("normal")
                .build();

        JsonWriterContext ctx = JsonWriterContext.acquire(registry);
        ctx.useJsonName = true;
        try {
            registry.getSerializer(JsonNameStruct.$SCHEMA, JsonNameStruct.class).serialize(obj, ctx);
            String json = new String(ctx.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"ID\":\"test-id\""),
                    "Should use jsonName when useJsonName=true: " + json);
            assertTrue(json.contains("\"DisplayName\":\"Alice\""),
                    "Should use jsonName when useJsonName=true: " + json);
            assertTrue(json.contains("\"normalField\":\"normal\""),
                    "Fields without jsonName unchanged: " + json);
        } finally {
            JsonWriterContext.release(ctx);
        }
    }

    @Test
    void testJsonNameDeserializationWithMemberName() {
        String json = "{\"id\":\"test-id\",\"displayName\":\"Bob\",\"normalField\":\"n\"}";
        JsonNameStruct obj = deserialize(json, JsonNameStruct.builder(), JsonNameStruct.class);

        assertEquals("test-id", obj.getId());
        assertEquals("Bob", obj.getDisplayName());
        assertEquals("n", obj.getNormalField());
    }

    @Test
    void testJsonNameDeserializationWithJsonName() {
        String json = "{\"ID\":\"test-id\",\"DisplayName\":\"Bob\",\"normalField\":\"n\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        JsonReaderContext ctx = new JsonReaderContext(bytes, 0, bytes.length, registry);
        ctx.useJsonName = true;
        JsonNameStruct obj = (JsonNameStruct) registry
                .getDeserializer(JsonNameStruct.$SCHEMA, JsonNameStruct.class)
                .deserialize(ctx, JsonNameStruct.builder());

        assertEquals("test-id", obj.getId());
        assertEquals("Bob", obj.getDisplayName());
        assertEquals("n", obj.getNormalField());
    }

    @Test
    void testJsonNameRoundtripWithJsonName() {
        JsonNameStruct original = JsonNameStruct.builder()
                .id("test-id")
                .displayName("Charlie")
                .normalField("normal")
                .build();

        JsonWriterContext wctx = JsonWriterContext.acquire(registry);
        wctx.useJsonName = true;
        byte[] bytes;
        try {
            registry.getSerializer(JsonNameStruct.$SCHEMA, JsonNameStruct.class).serialize(original, wctx);
            bytes = wctx.toByteArray();
        } finally {
            JsonWriterContext.release(wctx);
        }

        JsonReaderContext rctx = new JsonReaderContext(bytes, 0, bytes.length, registry);
        rctx.useJsonName = true;
        JsonNameStruct deserialized = (JsonNameStruct) registry
                .getDeserializer(JsonNameStruct.$SCHEMA, JsonNameStruct.class)
                .deserialize(rctx, JsonNameStruct.builder());

        assertEquals(original.getId(), deserialized.getId());
        assertEquals(original.getDisplayName(), deserialized.getDisplayName());
        assertEquals(original.getNormalField(), deserialized.getNormalField());
    }

    // ---- Nested Struct Tests ----

    @Test
    void testNestedStructRoundtrip() {
        InnerStruct inner = InnerStruct.builder()
                .value("hello")
                .numbers(List.of(1, 2, 3))
                .build();
        NestedStruct original = NestedStruct.builder()
                .field1("outer")
                .field2(42)
                .inner(inner)
                .build();

        String json = serializeToJson(original);
        assertTrue(json.contains("\"inner\":{"), "nested: " + json);
        assertTrue(json.contains("\"value\":\"hello\""), "inner value: " + json);

        NestedStruct deserialized = deserialize(json, NestedStruct.builder(), NestedStruct.class);
        assertEquals("outer", deserialized.getField1());
        assertEquals(42, deserialized.getField2());
        assertEquals("hello", deserialized.getInner().getValue());
    }

    @Test
    void testNestedStructWithNullOptional() {
        NestedStruct original = NestedStruct.builder()
                .field1("outer")
                .field2(1)
                .build();

        String json = serializeToJson(original);
        NestedStruct deserialized = deserialize(json, NestedStruct.builder(), NestedStruct.class);
        assertEquals("outer", deserialized.getField1());
        assertEquals(1, deserialized.getField2());
    }

    // ---- Recursive Struct Tests ----

    @Test
    void testRecursiveStructRoundtrip() {
        RecursiveStruct leaf = RecursiveStruct.builder()
                .value("leaf")
                .build();
        RecursiveStruct mid = RecursiveStruct.builder()
                .value("mid")
                .child(leaf)
                .build();
        RecursiveStruct root = RecursiveStruct.builder()
                .value("root")
                .child(mid)
                .children(List.of(leaf, mid))
                .build();

        String json = serializeToJson(root);
        RecursiveStruct deserialized = deserialize(json, RecursiveStruct.builder(), RecursiveStruct.class);
        assertEquals("root", deserialized.getValue());
        assertEquals("mid", deserialized.getChild().getValue());
        assertEquals("leaf", deserialized.getChild().getChild().getValue());
        assertEquals(2, deserialized.getChildren().size());
        assertEquals("leaf", deserialized.getChildren().get(0).getValue());
        assertEquals("mid", deserialized.getChildren().get(1).getValue());
    }

    // ---- Enum Tests ----

    @Test
    void testEnumStructRoundtrip() {
        EnumStruct original = EnumStruct.builder()
                .color(Color.GREEN)
                .priority(Priority.HIGH)
                .colors(List.of(Color.RED, Color.BLUE))
                .build();

        String json = serializeToJson(original);
        assertTrue(json.contains("\"GREEN\""), "color enum: " + json);
        assertTrue(json.contains("3"), "priority int enum: " + json);

        EnumStruct deserialized = deserialize(json, EnumStruct.builder(), EnumStruct.class);
        assertEquals(Color.GREEN, deserialized.getColor());
        assertEquals(Priority.HIGH, deserialized.getPriority());
        assertEquals(List.of(Color.RED, Color.BLUE), deserialized.getColors());
    }

    // ---- Sparse Collection Tests ----

    @Test
    void testSparseStructRoundtrip() {
        var sparseMap = new java.util.HashMap<String, String>();
        sparseMap.put("a", "1");
        sparseMap.put("b", null);
        sparseMap.put("c", "3");

        SparseStruct original = SparseStruct.builder()
                .sparseStrings(Arrays.asList("x", null, "z"))
                .sparseMap(sparseMap)
                .build();

        String json = serializeToJson(original);
        assertTrue(json.contains("null"), "should contain null: " + json);

        SparseStruct deserialized = deserialize(json, SparseStruct.builder(), SparseStruct.class);
        assertEquals(3, deserialized.getSparseStrings().size());
        assertEquals("x", deserialized.getSparseStrings().get(0));
        assertNull(deserialized.getSparseStrings().get(1));
        assertEquals("z", deserialized.getSparseStrings().get(2));
        assertEquals("1", deserialized.getSparseMap().get("a"));
        assertNull(deserialized.getSparseMap().get("b"));
        assertEquals("3", deserialized.getSparseMap().get("c"));
    }

    // ---- Struct Collection Tests ----

    @Test
    void testStructCollectionsMatchesDispatch() {
        InnerStruct inner = InnerStruct.builder().value("v").build();
        ComplexStruct obj = ComplexStruct.builder()
                .id("test")
                .count(1)
                .enabled(true)
                .ratio(1.0)
                .score(1.0f)
                .bigCount(1L)
                .nested(NestedStruct.builder().field1("f").field2(1).build())
                .structList(List.of(NestedStruct.builder().field1("a").field2(1).build()))
                .structMap(Map.of("k", NestedStruct.builder().field1("b").field2(2).build()))
                .build();
        assertCodegenMatchesDispatch(obj, ComplexStruct.builder(), ComplexStruct.class);
    }

    // ---- Document Tests ----

    @Test
    void testDocumentRoundtrip() {
        DocumentHolder original = DocumentHolder.builder()
                .name("doc-test")
                .freeform(Document.of("hello"))
                .build();

        String json = serializeToJson(original);
        assertTrue(json.contains("\"freeform\":\"hello\""), "doc string: " + json);

        DocumentHolder deserialized = deserialize(json, DocumentHolder.builder(), DocumentHolder.class);
        assertEquals("doc-test", deserialized.getName());
        assertNotNull(deserialized.getFreeform());
        assertEquals("hello", deserialized.getFreeform().asString());
    }

    @Test
    void testDocumentComplexValue() {
        String json = "{\"name\":\"test\",\"freeform\":{\"key\":\"value\",\"num\":42,\"arr\":[1,2,3]}}";
        DocumentHolder deserialized = deserialize(json, DocumentHolder.builder(), DocumentHolder.class);
        assertEquals("test", deserialized.getName());
        Document doc = deserialized.getFreeform();
        assertNotNull(doc);
        assertEquals("value", doc.getMember("key").asString());
        assertEquals(42, doc.getMember("num").asInteger());
        assertEquals(3, doc.getMember("arr").asList().size());
    }

    @Test
    void testDocumentNull() {
        String json = "{\"name\":\"test\",\"freeform\":null}";
        DocumentHolder deserialized = deserialize(json, DocumentHolder.builder(), DocumentHolder.class);
        assertEquals("test", deserialized.getName());
    }

    @Test
    void testDocumentBoolean() {
        String json = "{\"name\":\"test\",\"freeform\":true}";
        DocumentHolder deserialized = deserialize(json, DocumentHolder.builder(), DocumentHolder.class);
        assertTrue(deserialized.getFreeform().asBoolean());
    }

    @Test
    void testDocumentNumber() {
        String json = "{\"name\":\"test\",\"freeform\":3.14}";
        DocumentHolder deserialized = deserialize(json, DocumentHolder.builder(), DocumentHolder.class);
        assertEquals(3.14, deserialized.getFreeform().asDouble(), 0.001);
    }

    @Test
    void testDocumentArray() {
        String json = "{\"name\":\"test\",\"freeform\":[\"a\",\"b\",\"c\"]}";
        DocumentHolder deserialized = deserialize(json, DocumentHolder.builder(), DocumentHolder.class);
        assertEquals(3, deserialized.getFreeform().asList().size());
        assertEquals("a", deserialized.getFreeform().asList().get(0).asString());
    }

    // ---- Timestamp Tests ----

    @Test
    void testTimestampRoundtrip() {
        TimestampStruct original = TimestampStruct.builder()
                .epochSeconds(Instant.parse("2025-01-15T10:30:00Z"))
                .dateTime(Instant.parse("2025-06-01T12:00:00Z"))
                .httpDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        String json = serializeToJson(original);
        TimestampStruct deserialized = deserialize(json,
                TimestampStruct.builder(),
                TimestampStruct.class);
        assertEquals(original.getEpochSeconds(), deserialized.getEpochSeconds());
        assertEquals(original.getDateTime(), deserialized.getDateTime());
        assertEquals(original.getHttpDate(), deserialized.getHttpDate());
    }

    // ---- Codegen vs Dispatch Comparison Tests ----

    @Test
    void testNestedStructMatchesDispatch() {
        NestedStruct obj = NestedStruct.builder()
                .field1("test")
                .field2(1)
                .inner(InnerStruct.builder().value("v").build())
                .build();
        assertCodegenMatchesDispatch(obj, NestedStruct.builder(), NestedStruct.class);
    }

    @Test
    void testRecursiveStructMatchesDispatch() {
        RecursiveStruct obj = RecursiveStruct.builder()
                .value("root")
                .child(RecursiveStruct.builder()
                        .value("child")
                        .build())
                .build();
        assertCodegenMatchesDispatch(obj, RecursiveStruct.builder(), RecursiveStruct.class);
    }

    @Test
    void testTimestampMatchesDispatch() {
        TimestampStruct obj = TimestampStruct.builder()
                .epochSeconds(Instant.parse("2025-01-15T10:30:00Z"))
                .dateTime(Instant.parse("2025-06-01T12:00:00Z"))
                .httpDate(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        assertCodegenMatchesDispatch(obj, TimestampStruct.builder(), TimestampStruct.class);
    }

    @SuppressWarnings("unchecked")
    private <T extends SerializableStruct> void assertCodegenMatchesDispatch(
            T obj,
            Object builder,
            Class<T> clazz
    ) {
        byte[] codegenBytes = serializeToBytes(obj);
        byte[] dispatchBytes = serializeWithDispatchCodec(obj);

        // Deserialize both through the dispatch codec and re-serialize to normalize field order
        T fromCodegen = jsonCodec.deserializeShape(codegenBytes,
                (software.amazon.smithy.java.core.schema.ShapeBuilder<T>) obj.schema().shapeBuilder());
        T fromDispatch = jsonCodec.deserializeShape(dispatchBytes,
                (software.amazon.smithy.java.core.schema.ShapeBuilder<T>) obj.schema().shapeBuilder());

        String normalizedCodegen = new String(serializeWithDispatchCodec(fromCodegen), StandardCharsets.UTF_8);
        String normalizedDispatch = new String(serializeWithDispatchCodec(fromDispatch), StandardCharsets.UTF_8);

        assertEquals(normalizedDispatch,
                normalizedCodegen,
                "Codegen and dispatch should produce semantically equivalent JSON");
    }

    // ---- Stress Test ----

    @Test
    void testSerializationStress() {
        SimpleStruct obj = SimpleStruct.builder()
                .name("Stress")
                .age(42)
                .active(true)
                .score(3.14)
                .build();

        for (int i = 0; i < 100_000; i++) {
            String json = serializeToJson(obj);
            assertNotNull(json);
            assertTrue(json.contains("\"name\":\"Stress\""));
        }
    }

    @Test
    void testDeserializationStress() {
        String json = "{\"name\":\"Stress\",\"age\":42,\"active\":true,\"score\":3.14}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < 100_000; i++) {
            SimpleStruct obj = deserialize(bytes, SimpleStruct.builder(), SimpleStruct.class);
            assertEquals("Stress", obj.getName());
            assertEquals(42, obj.getAge());
        }
    }

    // ---- Helper methods ----

    private String serializeToJson(SerializableStruct obj) {
        byte[] bytes = serializeToBytes(obj);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] serializeToBytes(SerializableStruct obj) {
        JsonWriterContext ctx = JsonWriterContext.acquire(registry);
        try {
            registry.getSerializer(obj.schema(), obj.getClass()).serialize(obj, ctx);
            return ctx.toByteArray();
        } finally {
            JsonWriterContext.release(ctx);
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

    void dumpBytecode(
            Class<? extends SerializableStruct> shapeClass,
            software.amazon.smithy.java.core.schema.Schema schema
    ) throws Exception {
        var profile = new ClassFileJsonCodecProfile();
        var plan = software.amazon.smithy.java.codegen.rt.plan.StructCodePlan.analyze(schema, shapeClass);
        String pkg = shapeClass.getPackageName();
        String name = shapeClass.getSimpleName();

        var serResult = profile.generateSerializerBytecode(plan, name + "$Ser", pkg, java.util.Map.of());
        var deResult = profile.generateDeserializerBytecode(plan, name + "$De", pkg, java.util.Map.of());

        var dumpDir = java.nio.file.Path.of("build/codegen-dump");
        java.nio.file.Files.createDirectories(dumpDir);

        var serFile = dumpDir.resolve(name + "_Ser.class");
        var deFile = dumpDir.resolve(name + "_De.class");
        java.nio.file.Files.write(serFile, serResult.bytecode());
        java.nio.file.Files.write(deFile, deResult.bytecode());

        var serJavap = dumpDir.resolve(name + "_Ser.txt");
        var deJavap = dumpDir.resolve(name + "_De.txt");
        new ProcessBuilder("javap", "-c", "-p", serFile.toString())
                .redirectOutput(serJavap.toFile())
                .start()
                .waitFor();
        new ProcessBuilder("javap", "-c", "-p", deFile.toString())
                .redirectOutput(deJavap.toFile())
                .start()
                .waitFor();

        var decompileDir = dumpDir.resolve("decompiled");
        java.nio.file.Files.createDirectories(decompileDir);
        org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler.main(new String[] {
                serFile.toString(),
                decompileDir.toString()});
        org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler.main(new String[] {
                deFile.toString(),
                decompileDir.toString()});

        System.out.println("Dumped to: " + dumpDir.toAbsolutePath());
        System.out.println("  Serializer:   " + serResult.bytecode().length + " bytes bytecode");
        System.out.println("  Deserializer: " + deResult.bytecode().length + " bytes bytecode");
    }
}
