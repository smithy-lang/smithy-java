/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static tools.jackson.core.JsonToken.PROPERTY_NAME;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.bench.model.BenchUnion;
import software.amazon.smithy.java.json.bench.model.Color;
import software.amazon.smithy.java.json.bench.model.ComplexStruct;
import software.amazon.smithy.java.json.bench.model.InnerStruct;
import software.amazon.smithy.java.json.bench.model.NestedStruct;
import software.amazon.smithy.java.json.bench.model.SimpleStruct;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JsonBench {

    public enum TestCase {
        SIMPLE,
        COMPLEX,
    }

    public enum Provider {
        jackson,
        smithy,
    }

    @Param
    private TestCase testCase;

    @Param
    private Provider provider;

    private JsonCodec codec;
    private SerializableStruct shape;
    private byte[] serializedBytes;
    private byte[] reversedBytes;
    private Supplier<ShapeBuilder<?>> builderSupplier;

    @Setup
    public void setup() {
        JsonSerdeProvider serdeProvider = switch (provider) {
            case jackson -> new JacksonJsonSerdeProvider();
            case smithy -> new SmithyJsonSerdeProvider();
        };
        codec = JsonCodec.builder()
                .overrideSerdeProvider(serdeProvider)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();

        switch (testCase) {
            case SIMPLE -> {
                shape = buildSimpleStruct();
                builderSupplier = SimpleStruct::builder;
            }
            case COMPLEX -> {
                shape = buildComplexStruct();
                builderSupplier = ComplexStruct::builder;
            }
        }

        // Pre-serialize to byte[] for deserialization benchmarks
        ByteBuffer buf = codec.serialize(shape);
        serializedBytes = new byte[buf.remaining()];
        buf.get(serializedBytes);

        // Reversed field order to force the slow path in SmithyMemberLookup
        reversedBytes = reverseJsonFieldOrder(serializedBytes);
    }

    private static SimpleStruct buildSimpleStruct() {
        return SimpleStruct.builder()
                .name("benchmark-test")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
    }

    private static ComplexStruct buildComplexStruct() {
        var inner = InnerStruct.builder()
                .value("inner-value")
                .numbers(List.of(1, 2, 3, 4, 5))
                .build();
        var nested = NestedStruct.builder()
                .field1("nested-field")
                .field2(100)
                .inner(inner)
                .build();
        var sparseMap = new HashMap<String, String>();
        sparseMap.put("x", "1");
        sparseMap.put("y", "2");
        sparseMap.put("z", null);
        return ComplexStruct.builder()
                .id("bench-001")
                .count(999)
                .enabled(true)
                .ratio(1.618)
                .score(2.718f)
                .bigCount(1_000_000L)
                .optionalString("optional-value")
                .optionalInt(42)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .updatedAt(Instant.parse("2025-06-01T12:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T00:00:00Z"))
                .payload(ByteBuffer.wrap("binary-payload-data".getBytes(StandardCharsets.UTF_8)))
                .tags(List.of("alpha", "beta", "gamma", "delta"))
                .intList(List.of(10, 20, 30, 40, 50))
                .metadata(Map.of("key1", "value1", "key2", "value2", "key3", "value3"))
                .intMap(Map.of("a", 1, "b", 2, "c", 3))
                .nested(nested)
                .optionalNested(NestedStruct.builder()
                        .field1("opt-nested")
                        .field2(200)
                        .build())
                .structList(List.of(nested, nested))
                .structMap(Map.of("first", nested, "second", nested))
                .choice(new BenchUnion.StringValueMember("union-string"))
                .color(Color.GREEN)
                .colorList(List.of(Color.RED, Color.BLUE, Color.YELLOW))
                .sparseStrings(Arrays.asList("a", null, "c"))
                .sparseMap(sparseMap)
                .bigIntValue(new BigInteger("123456789012345678901234567890"))
                .bigDecValue(new BigDecimal("99999.99999"))
                .freeformData(Document.of(Map.of("key", Document.of("value"), "num", Document.of(42))))
                .build();
    }

    @Benchmark
    public ByteBuffer serialize() {
        return codec.serialize(shape);
    }

    @Benchmark
    public Object deserialize() {
        return codec.deserializeShape(serializedBytes, builderSupplier.get());
    }

    @Benchmark
    public Object deserializeReversed() {
        return codec.deserializeShape(reversedBytes, builderSupplier.get());
    }

    @Benchmark
    public Object roundtrip() {
        ByteBuffer bytes = codec.serialize(shape);
        return codec.deserializeShape(bytes, builderSupplier.get());
    }

    /**
     * Reverses the order of top-level JSON object fields using Jackson streaming.
     * Only used at setup time.
     */
    private static byte[] reverseJsonFieldOrder(byte[] json) {
        var factory = JsonFactory.builder().build();

        // Parse top-level fields and capture each value as raw bytes
        List<Map.Entry<String, byte[]>> fields = new ArrayList<>();
        try (var parser = factory.createParser(ObjectReadContext.empty(), json)) {
            parser.nextToken(); // START_OBJECT
            while (parser.nextToken() == PROPERTY_NAME) {
                String name = parser.currentName();
                parser.nextToken(); // advance to value
                var baos = new ByteArrayOutputStream();
                try (var gen = factory.createGenerator(ObjectWriteContext.empty(), baos)) {
                    gen.copyCurrentStructure(parser);
                }
                fields.add(new AbstractMap.SimpleEntry<>(name, baos.toByteArray()));
            }
        }

        // Re-emit in reverse order
        Collections.reverse(fields);
        var out = new ByteArrayOutputStream();
        try (var gen = factory.createGenerator(ObjectWriteContext.empty(), out)) {
            gen.writeStartObject();
            for (var field : fields) {
                gen.writeName(field.getKey());
                try (var p = factory.createParser(ObjectReadContext.empty(), field.getValue())) {
                    p.nextToken();
                    gen.copyCurrentStructure(p);
                }
            }
            gen.writeEndObject();
        }
        return out.toByteArray();
    }
}
