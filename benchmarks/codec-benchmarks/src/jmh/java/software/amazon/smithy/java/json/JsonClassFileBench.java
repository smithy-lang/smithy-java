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
import software.amazon.smithy.java.json.codegen.ClassFileJsonCodecProfile;
import software.amazon.smithy.java.json.codegen.JsonCodecProfile;
import software.amazon.smithy.java.json.codegen.JsonReaderContext;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class JsonClassFileBench {

    private SpecializedCodecRegistry classFileRegistry;
    private SpecializedCodecRegistry janinoRegistry;

    private SimpleStruct simpleStruct;
    private byte[] simpleBytes;

    private ComplexStruct complexStruct;
    private byte[] complexBytes;

    @Setup(Level.Trial)
    public void setup() {
        classFileRegistry = new SpecializedCodecRegistry(new ClassFileJsonCodecProfile());
        janinoRegistry = new SpecializedCodecRegistry(new JsonCodecProfile());
        Codec dispatchCodec = JsonCodec.builder().build();

        simpleStruct = BenchData.buildSimpleStruct();
        complexStruct = BenchData.buildComplexStruct();

        // Warmup both registries
        classFileRegistry.warmup(SimpleStruct.$SCHEMA, SimpleStruct.class);
        classFileRegistry.warmup(ComplexStruct.$SCHEMA, ComplexStruct.class);
        classFileRegistry.warmup(NestedStruct.$SCHEMA, NestedStruct.class);
        classFileRegistry.warmup(InnerStruct.$SCHEMA, InnerStruct.class);

        janinoRegistry.warmup(SimpleStruct.$SCHEMA, SimpleStruct.class);
        janinoRegistry.warmup(ComplexStruct.$SCHEMA, ComplexStruct.class);
        janinoRegistry.warmup(NestedStruct.$SCHEMA, NestedStruct.class);
        janinoRegistry.warmup(InnerStruct.$SCHEMA, InnerStruct.class);

        ByteBuffer buf = dispatchCodec.serialize(simpleStruct);
        simpleBytes = new byte[buf.remaining()];
        buf.get(simpleBytes);

        ByteBuffer cbuf = dispatchCodec.serialize(complexStruct);
        complexBytes = new byte[cbuf.remaining()];
        cbuf.get(complexBytes);
    }

    // ---- ClassFile API benchmarks ----

    @Benchmark
    public byte[] serializeSimpleClassFile() {
        WriterContext ctx = WriterContext.acquire(classFileRegistry);
        try {
            classFileRegistry.getSerializer(SimpleStruct.$SCHEMA, SimpleStruct.class)
                    .serialize(simpleStruct, ctx);
            return ctx.toByteArray();
        } finally {
            WriterContext.release(ctx);
        }
    }

    @Benchmark
    public SimpleStruct deserializeSimpleClassFile() {
        JsonReaderContext ctx = new JsonReaderContext(simpleBytes, 0, simpleBytes.length, classFileRegistry);
        return (SimpleStruct) classFileRegistry
                .getDeserializer(SimpleStruct.$SCHEMA, SimpleStruct.class)
                .deserialize(ctx, SimpleStruct.builder());
    }

    @Benchmark
    public byte[] serializeComplexClassFile() {
        WriterContext ctx = WriterContext.acquire(classFileRegistry);
        try {
            classFileRegistry.getSerializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                    .serialize(complexStruct, ctx);
            return ctx.toByteArray();
        } finally {
            WriterContext.release(ctx);
        }
    }

    @Benchmark
    public ComplexStruct deserializeComplexClassFile() {
        JsonReaderContext ctx = new JsonReaderContext(complexBytes, 0, complexBytes.length, classFileRegistry);
        return (ComplexStruct) classFileRegistry
                .getDeserializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                .deserialize(ctx, ComplexStruct.builder());
    }

    // ---- Janino benchmarks (for direct comparison) ----

    @Benchmark
    public byte[] serializeSimpleJanino() {
        WriterContext ctx = WriterContext.acquire(janinoRegistry);
        try {
            janinoRegistry.getSerializer(SimpleStruct.$SCHEMA, SimpleStruct.class)
                    .serialize(simpleStruct, ctx);
            return ctx.toByteArray();
        } finally {
            WriterContext.release(ctx);
        }
    }

    @Benchmark
    public SimpleStruct deserializeSimpleJanino() {
        JsonReaderContext ctx = new JsonReaderContext(simpleBytes, 0, simpleBytes.length, janinoRegistry);
        return (SimpleStruct) janinoRegistry
                .getDeserializer(SimpleStruct.$SCHEMA, SimpleStruct.class)
                .deserialize(ctx, SimpleStruct.builder());
    }

    @Benchmark
    public byte[] serializeComplexJanino() {
        WriterContext ctx = WriterContext.acquire(janinoRegistry);
        try {
            janinoRegistry.getSerializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                    .serialize(complexStruct, ctx);
            return ctx.toByteArray();
        } finally {
            WriterContext.release(ctx);
        }
    }

    @Benchmark
    public ComplexStruct deserializeComplexJanino() {
        JsonReaderContext ctx = new JsonReaderContext(complexBytes, 0, complexBytes.length, janinoRegistry);
        return (ComplexStruct) janinoRegistry
                .getDeserializer(ComplexStruct.$SCHEMA, ComplexStruct.class)
                .deserialize(ctx, ComplexStruct.builder());
    }
}
