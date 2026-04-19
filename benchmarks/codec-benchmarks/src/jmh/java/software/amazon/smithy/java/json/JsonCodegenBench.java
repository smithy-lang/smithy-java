/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.ByteBuffer;
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
import software.amazon.smithy.java.codegen.rt.WriterContext;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.json.codegen.JsonCodecProfile;
import software.amazon.smithy.java.json.codegen.JsonReaderContext;

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

    @Setup(Level.Trial)
    public void setup() {
        registry = new SpecializedCodecRegistry(new JsonCodecProfile());
        dispatchCodec = JsonCodec.builder().build();

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
    }

    // ---- Simple struct benchmarks ----

    @Benchmark
    public byte[] serializeSimpleCodegen() {
        WriterContext ctx = WriterContext.acquire(registry);
        try {
            registry.getSerializer(SimpleStruct.$SCHEMA, SimpleStruct.class)
                    .serialize(simpleStruct, ctx);
            return ctx.toByteArray();
        } finally {
            WriterContext.release(ctx);
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
    public byte[] serializeComplexCodegen() {
        WriterContext ctx = WriterContext.acquire(registry);
        try {
            registry.getSerializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                    .serialize(complexStruct, ctx);
            return ctx.toByteArray();
        } finally {
            WriterContext.release(ctx);
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
}
