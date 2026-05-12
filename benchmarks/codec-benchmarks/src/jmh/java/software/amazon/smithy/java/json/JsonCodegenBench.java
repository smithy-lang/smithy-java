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
import software.amazon.smithy.java.benchmarks.codec.model.SimpleStruct;
import software.amazon.smithy.java.core.serde.Codec;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class JsonCodegenBench {

    private Codec codegenCodec;
    private Codec dispatchCodec;

    private SimpleStruct simpleStruct;
    private byte[] simpleBytes;

    private ComplexStruct complexStruct;
    private byte[] complexBytes;
    private byte[] complexBytesReversed;
    private byte[] complexBytesSparse;

    @Setup(Level.Trial)
    public void setup() {
        // Default codec picks up CodegenJsonSerdeProvider via ServiceLoader, which wraps
        // the dispatch provider and tries codegen first — this is the real production path.
        codegenCodec = JsonCodec.builder().build();

        // Dispatch-only codec: explicitly select the raw provider without codegen wrapping.
        JsonSerdeProvider rawProvider = ServiceLoader.load(JsonSerdeProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> !(p instanceof CodegenJsonSerdeProvider))
                .findFirst()
                .orElseThrow();
        dispatchCodec = JsonCodec.builder().overrideSerdeProvider(rawProvider).build();

        simpleStruct = BenchData.buildSimpleStruct();
        complexStruct = BenchData.buildComplexStruct();

        // Warmup codegen: first call triggers async generation, spin until the fast path is active.
        // The codegen path returns a result from ClassFileSpecializedJsonSerde once ready;
        // until then it falls back to dispatch. We spin to ensure JMH measurement hits codegen.
        for (int i = 0; i < 200; i++) {
            codegenCodec.serialize(simpleStruct);
            codegenCodec.serialize(complexStruct);
            codegenCodec.deserializeShape(codegenCodec.serialize(simpleStruct), SimpleStruct.builder());
            codegenCodec.deserializeShape(codegenCodec.serialize(complexStruct), ComplexStruct.builder());
        }

        // Pre-serialize for deserialization benchmarks (use dispatch to get clean bytes)
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
        return codegenCodec.serialize(simpleStruct);
    }

    @Benchmark
    public ByteBuffer serializeSimpleDispatch() {
        return dispatchCodec.serialize(simpleStruct);
    }

    @Benchmark
    public SimpleStruct deserializeSimpleCodegen() {
        return codegenCodec.deserializeShape(simpleBytes, SimpleStruct.builder());
    }

    @Benchmark
    public SimpleStruct deserializeSimpleDispatch() {
        return dispatchCodec.deserializeShape(simpleBytes, SimpleStruct.builder());
    }

    // ---- Complex struct benchmarks ----

    @Benchmark
    public ByteBuffer serializeComplexCodegen() {
        return codegenCodec.serialize(complexStruct);
    }

    @Benchmark
    public ByteBuffer serializeComplexDispatch() {
        return dispatchCodec.serialize(complexStruct);
    }

    @Benchmark
    public ComplexStruct deserializeComplexCodegen() {
        return codegenCodec.deserializeShape(complexBytes, ComplexStruct.builder());
    }

    @Benchmark
    public ComplexStruct deserializeComplexDispatch() {
        return dispatchCodec.deserializeShape(complexBytes, ComplexStruct.builder());
    }

    // ---- Missing fields benchmarks (in-order but with gaps — realistic scenario) ----

    @Benchmark
    public ComplexStruct deserializeSparseCodegen() {
        return codegenCodec.deserializeShape(complexBytesSparse, ComplexStruct.builder());
    }

    @Benchmark
    public ComplexStruct deserializeSparseDispatch() {
        return dispatchCodec.deserializeShape(complexBytesSparse, ComplexStruct.builder());
    }

    // ---- Reversed field order benchmarks (tests hash dispatch fallback) ----

    @Benchmark
    public ComplexStruct deserializeReversedCodegen() {
        return codegenCodec.deserializeShape(complexBytesReversed, ComplexStruct.builder());
    }

    @Benchmark
    public ComplexStruct deserializeReversedDispatch() {
        return dispatchCodec.deserializeShape(complexBytesReversed, ComplexStruct.builder());
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
