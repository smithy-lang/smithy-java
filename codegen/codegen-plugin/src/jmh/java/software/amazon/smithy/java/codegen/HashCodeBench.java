/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import software.amazon.smithy.java.codegen.test.model.BooleanMembersInput;
import software.amazon.smithy.java.codegen.test.model.DefaultsInput;
import software.amazon.smithy.java.codegen.test.model.IntegerMembersInput;
import software.amazon.smithy.java.codegen.test.model.StringMembersInput;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class HashCodeBench {

    private IntegerMembersInput integerMembers;
    private BooleanMembersInput booleanMembers;
    private StringMembersInput stringMembers;
    private DefaultsInput defaultsInput;

    @Setup
    public void setup() {
        integerMembers = IntegerMembersInput.builder()
                .requiredInt(42)
                .defaultInt(7)
                .optionalInt(99)
                .build();

        booleanMembers = BooleanMembersInput.builder()
                .requiredBoolean(true)
                .defaultBoolean(false)
                .optionalBoolean(true)
                .build();

        stringMembers = StringMembersInput.builder()
                .requiredString("hello")
                .defaultString("world")
                .optionalString("foo")
                .build();

        // Uses all defaults - 23 fields with 7 primitives + 16 object types
        defaultsInput = DefaultsInput.builder().build();
    }

    @Benchmark
    public int fewFields_primitiveInt() {
        return integerMembers.hashCode();
    }

    @Benchmark
    public int fewFields_primitiveBoolean() {
        return booleanMembers.hashCode();
    }

    @Benchmark
    public int fewFields_objectOnly() {
        return stringMembers.hashCode();
    }

    @Benchmark
    public int manyFields_mixed() {
        return defaultsInput.hashCode();
    }
}
