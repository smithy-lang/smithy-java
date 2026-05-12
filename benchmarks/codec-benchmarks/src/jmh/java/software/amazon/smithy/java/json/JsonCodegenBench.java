/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static tools.jackson.core.JsonToken.PROPERTY_NAME;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.benchmarks.codec.BenchData;
import software.amazon.smithy.java.benchmarks.codec.model.ComplexStruct;
import software.amazon.smithy.java.benchmarks.codec.model.InnerStruct;
import software.amazon.smithy.java.benchmarks.codec.model.NestedStruct;
import software.amazon.smithy.java.benchmarks.codec.model.SimpleStruct;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.json.codegen.ClassFileJsonCodecProfile;
import software.amazon.smithy.java.json.codegen.JsonReaderContext;
import software.amazon.smithy.java.json.smithy.JsonWriterContext;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class JsonCodegenBench {

    private SpecializedCodecRegistry registry;
    private Codec dispatchCodec;

    private SimpleStruct simpleStruct;
    private byte[] simpleBytes;

    private ComplexStruct complexStruct;
    private byte[] complexBytes;
    private byte[] complexBytesReversed;
    private byte[] complexBytesSparse;

    @Setup(Level.Trial)
    public void setup() {
        registry = new SpecializedCodecRegistry(new ClassFileJsonCodecProfile());
        // Use the raw provider without codegen wrapping to get a true dispatch-only codec
        JsonSerdeProvider rawProvider = ServiceLoader.load(JsonSerdeProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> !(p instanceof CodegenJsonSerdeProvider))
                .findFirst()
                .orElseThrow();
        dispatchCodec = JsonCodec.builder().overrideSerdeProvider(rawProvider).build();

        simpleStruct = BenchData.buildSimpleStruct();
        complexStruct = BenchData.buildComplexStruct();

        // Warmup codegen for all shapes
        registry.warmup(SimpleStruct.$SCHEMA, SimpleStruct.class);
        registry.warmup(ComplexStruct.$SCHEMA, ComplexStruct.class);
        registry.warmup(NestedStruct.$SCHEMA, NestedStruct.class);
        registry.warmup(InnerStruct.$SCHEMA, InnerStruct.class);

        // Pre-serialize for deserialization benchmarks
        ByteBuffer buf = dispatchCodec.serialize(simpleStruct);
        simpleBytes = new byte[buf.remaining()];
        buf.get(simpleBytes);

        ByteBuffer cbuf = dispatchCodec.serialize(complexStruct);
        complexBytes = new byte[cbuf.remaining()];
        cbuf.get(complexBytes);

        complexBytesReversed = reverseJsonFieldOrder(complexBytes);
        complexBytesSparse = dropJsonFields(complexBytes,
                Set.of(
                        "optionalString",
                        "optionalInt",
                        "optionalNested",
                        "sparseStrings",
                        "sparseMap",
                        "bigIntValue",
                        "bigDecValue",
                        "freeformData",
                        "choice"));
    }

    // ---- Simple struct benchmarks ----

    @Benchmark
    public ByteBuffer serializeSimpleCodegen() {
        JsonWriterContext ctx = JsonWriterContext.acquire(registry);
        try {
            registry.getSerializer(SimpleStruct.$SCHEMA, SimpleStruct.class)
                    .serialize(simpleStruct, ctx);
            return ctx.toByteBuffer();
        } finally {
            JsonWriterContext.release(ctx);
        }
    }

    @Benchmark
    public ByteBuffer serializeSimpleDispatch() {
        return dispatchCodec.serialize(simpleStruct);
    }

    @Benchmark
    public SimpleStruct deserializeSimpleCodegen() {
        JsonReaderContext ctx = new JsonReaderContext(simpleBytes, 0, simpleBytes.length, registry);
        return (SimpleStruct) registry
                .getDeserializer(SimpleStruct.$SCHEMA, SimpleStruct.class)
                .deserialize(ctx, SimpleStruct.builder());
    }

    @Benchmark
    public SimpleStruct deserializeSimpleDispatch() {
        return dispatchCodec.deserializeShape(
                ByteBuffer.wrap(simpleBytes),
                SimpleStruct.builder());
    }

    // ---- Complex struct benchmarks ----

    @Benchmark
    public ByteBuffer serializeComplexCodegen() {
        JsonWriterContext ctx = JsonWriterContext.acquire(registry);
        try {
            registry.getSerializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                    .serialize(complexStruct, ctx);
            return ctx.toByteBuffer();
        } finally {
            JsonWriterContext.release(ctx);
        }
    }

    @Benchmark
    public ByteBuffer serializeComplexDispatch() {
        return dispatchCodec.serialize(complexStruct);
    }

    @Benchmark
    public ComplexStruct deserializeComplexCodegen() {
        JsonReaderContext ctx = new JsonReaderContext(complexBytes, 0, complexBytes.length, registry);
        return (ComplexStruct) registry
                .getDeserializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                .deserialize(ctx, ComplexStruct.builder());
    }

    @Benchmark
    public ComplexStruct deserializeComplexDispatch() {
        return dispatchCodec.deserializeShape(
                ByteBuffer.wrap(complexBytes),
                ComplexStruct.builder());
    }

    // ---- Missing fields benchmarks (in-order but with gaps — realistic scenario) ----

    @Benchmark
    public ComplexStruct deserializeSparseCodegen() {
        JsonReaderContext ctx = new JsonReaderContext(
                complexBytesSparse,
                0,
                complexBytesSparse.length,
                registry);
        return (ComplexStruct) registry
                .getDeserializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                .deserialize(ctx, ComplexStruct.builder());
    }

    @Benchmark
    public ComplexStruct deserializeSparseDispatch() {
        return dispatchCodec.deserializeShape(
                ByteBuffer.wrap(complexBytesSparse),
                ComplexStruct.builder());
    }

    // ---- Reversed field order benchmarks (tests hash dispatch fallback) ----

    @Benchmark
    public ComplexStruct deserializeReversedCodegen() {
        JsonReaderContext ctx = new JsonReaderContext(
                complexBytesReversed,
                0,
                complexBytesReversed.length,
                registry);
        return (ComplexStruct) registry
                .getDeserializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                .deserialize(ctx, ComplexStruct.builder());
    }

    @Benchmark
    public ComplexStruct deserializeReversedDispatch() {
        return dispatchCodec.deserializeShape(
                ByteBuffer.wrap(complexBytesReversed),
                ComplexStruct.builder());
    }

    private static byte[] dropJsonFields(byte[] json, Set<String> fieldsToDrop) {
        var factory = JsonFactory.builder().build();
        List<Map.Entry<String, byte[]>> fields = new ArrayList<>();
        try (var parser = factory.createParser(ObjectReadContext.empty(), json)) {
            parser.nextToken();
            while (parser.nextToken() == PROPERTY_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                if (fieldsToDrop.contains(name)) {
                    parser.skipChildren();
                    continue;
                }
                var baos = new ByteArrayOutputStream();
                try (var gen = factory.createGenerator(ObjectWriteContext.empty(), baos)) {
                    gen.copyCurrentStructure(parser);
                }
                fields.add(new AbstractMap.SimpleEntry<>(name, baos.toByteArray()));
            }
        }
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

    private static byte[] reverseJsonFieldOrder(byte[] json) {
        var factory = JsonFactory.builder().build();
        List<Map.Entry<String, byte[]>> fields = new ArrayList<>();
        try (var parser = factory.createParser(ObjectReadContext.empty(), json)) {
            parser.nextToken();
            while (parser.nextToken() == PROPERTY_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                var baos = new ByteArrayOutputStream();
                try (var gen = factory.createGenerator(ObjectWriteContext.empty(), baos)) {
                    gen.copyCurrentStructure(parser);
                }
                fields.add(new AbstractMap.SimpleEntry<>(name, baos.toByteArray()));
            }
        }
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
