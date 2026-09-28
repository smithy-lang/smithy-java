/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import software.amazon.smithy.java.context.Context;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class TemplateResolutionBenchmark {

    @Param({"3", "5", "9"})
    public int segmentCount;

    private BytecodeEvaluator resolveRegisters;
    private BytecodeEvaluator buildRegisters;
    private BytecodeEvaluator resolveProperties;
    private BytecodeEvaluator buildProperties;

    @Setup(Level.Trial)
    public void setup() {
        resolveRegisters = createEvaluator(createProgram(false, false));
        buildRegisters = createEvaluator(createProgram(true, false));
        resolveProperties = createEvaluator(createProgram(false, true));
        buildProperties = createEvaluator(createProgram(true, true));
    }

    @Benchmark
    public boolean resolveRegisters() {
        return resolveRegisters.test(0);
    }

    @Benchmark
    public boolean buildRegisters() {
        return buildRegisters.test(0);
    }

    @Benchmark
    public boolean resolveProperties() {
        return resolveProperties.test(0);
    }

    @Benchmark
    public boolean buildProperties() {
        return buildProperties.test(0);
    }

    private Bytecode createProgram(boolean buildTemplate, boolean properties) {
        BytecodeWriter writer = new BytecodeWriter();
        int dynamicCount = segmentCount / 2;
        RegisterDefinition[] registers = new RegisterDefinition[dynamicCount];
        StringBuilder expected = new StringBuilder();

        writer.markConditionStart();
        if (buildTemplate) {
            writer.writeByte(Opcodes.BUILD_TEMPLATE);
            writer.writeByte(segmentCount);
        }

        for (int i = 0; i < segmentCount; i++) {
            if ((i & 1) == 0) {
                String literal = i == 0 ? "service." : ".";
                expected.append(literal);
                writeLiteral(writer, buildTemplate, literal);
            } else {
                int register = i / 2;
                String value = "value" + register;
                expected.append(value);
                registers[register] = new RegisterDefinition(
                        "register" + register,
                        false,
                        properties ? Map.of("value", value) : value,
                        null,
                        false);
                writeDynamic(writer, buildTemplate, properties, register);
            }
        }

        if (!buildTemplate) {
            writer.writeByte(Opcodes.RESOLVE_TEMPLATE);
            writer.writeByte(segmentCount);
        }
        writeLoadConstant(writer, writer.getConstantIndex(expected.toString()));
        writer.writeByte(Opcodes.STRING_EQUALS);
        writer.writeByte(Opcodes.RETURN_VALUE);

        return writer.build(registers, new RulesFunction[0], new int[] {-1, 1, -1}, 1);
    }

    private void writeLiteral(BytecodeWriter writer, boolean buildTemplate, String literal) {
        int constant = writer.getConstantIndex(literal);
        if (buildTemplate) {
            writer.writeByte(TemplateSegmentType.LITERAL);
            writer.writeShort(constant);
        } else {
            writeLoadConstant(writer, constant);
        }
    }

    private void writeDynamic(BytecodeWriter writer, boolean buildTemplate, boolean property, int register) {
        if (buildTemplate) {
            writer.writeByte(property ? TemplateSegmentType.REGISTER_PROPERTY : TemplateSegmentType.REGISTER);
            writer.writeByte(register);
            if (property) {
                writer.writeShort(writer.getConstantIndex("value"));
            }
        } else if (property) {
            writer.writeByte(Opcodes.GET_PROPERTY_REG);
            writer.writeByte(register);
            writer.writeShort(writer.getConstantIndex("value"));
        } else {
            writer.writeByte(Opcodes.LOAD_REGISTER);
            writer.writeByte(register);
        }
    }

    private void writeLoadConstant(BytecodeWriter writer, int constant) {
        if (constant < 256) {
            writer.writeByte(Opcodes.LOAD_CONST);
            writer.writeByte(constant);
        } else {
            writer.writeByte(Opcodes.LOAD_CONST_W);
            writer.writeShort(constant);
        }
    }

    private BytecodeEvaluator createEvaluator(Bytecode bytecode) {
        RegisterFiller filler = RegisterFiller.of(bytecode, Collections.emptyMap());
        BytecodeEvaluator evaluator = new BytecodeEvaluator(bytecode, new RulesExtension[0], filler);
        evaluator.reset(Context.empty(), Collections.emptyMap());
        return evaluator;
    }
}
