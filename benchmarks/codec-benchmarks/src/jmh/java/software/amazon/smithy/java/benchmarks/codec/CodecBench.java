/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.codec;

import java.nio.ByteBuffer;
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
import software.amazon.smithy.java.benchmarks.codec.model.ComplexStruct;
import software.amazon.smithy.java.benchmarks.codec.model.SimpleStruct;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
public abstract class CodecBench {

    public enum TestCase {
        SIMPLE,
        COMPLEX,
    }

    @Param
    protected TestCase testCase;

    protected Codec codec;
    protected SerializableStruct shape;
    protected byte[] serializedBytes;
    protected Supplier<ShapeBuilder<?>> builderSupplier;

    protected abstract Codec createCodec();

    @Setup
    public void setup() {
        codec = createCodec();

        switch (testCase) {
            case SIMPLE -> {
                shape = BenchData.buildSimpleStruct();
                builderSupplier = SimpleStruct::builder;
            }
            case COMPLEX -> {
                shape = BenchData.buildComplexStruct();
                builderSupplier = ComplexStruct::builder;
            }
        }

        ByteBuffer buf = codec.serialize(shape);
        serializedBytes = new byte[buf.remaining()];
        buf.get(serializedBytes);
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
    public Object roundtrip() {
        ByteBuffer bytes = codec.serialize(shape);
        return codec.deserializeShape(bytes, builderSupplier.get());
    }
}
