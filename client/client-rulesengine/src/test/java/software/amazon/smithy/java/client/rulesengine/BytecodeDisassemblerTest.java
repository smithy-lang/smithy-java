/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BytecodeDisassemblerTest {

    @Test
    void disassemblesBasicProgram() {
        // Create a simple bytecode program with a few basic instructions
        BytecodeWriter writer = new BytecodeWriter();

        // Condition 0: load constant and return
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(0); // constant index 0
        writer.writeByte(Opcodes.RETURN_VALUE);

        // Result 0: return error
        writer.markResultStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(1); // constant index 1
        writer.writeByte(Opcodes.RETURN_ERROR);

        RegisterDefinition[] registers = {
                new RegisterDefinition("testParam", true, "defaultValue", null, false)
        };

        RulesFunction[] functions = {
                new TestFunction("testFn", 1)
        };

        int[] bddNodes = {
                -1,
                1,
                -1 // terminal node
        };

        Bytecode bytecode = writer.build(registers, functions, bddNodes, 1);

        BytecodeDisassembler disassembler = new BytecodeDisassembler(bytecode);
        String result = disassembler.disassemble();

        // Verify header information is present
        assertThat(result, containsString("=== Bytecode Program ==="));
        assertThat(result, containsString("Conditions: 1"));
        assertThat(result, containsString("Results: 1"));
        assertThat(result, containsString("Registers: 1"));
        assertThat(result, containsString("Functions: 1"));
    }

    @Test
    void disassemblesInstructionWithOperands() {
        BytecodeWriter writer = new BytecodeWriter();

        // Create a condition with various instruction types
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_REGISTER);
        writer.writeByte(0); // register 0
        writer.writeByte(Opcodes.SET_REGISTER);
        writer.writeByte(1); // register 1
        writer.writeByte(Opcodes.FN1);
        writer.writeByte(0); // function 0
        writer.writeByte(Opcodes.RETURN_VALUE);

        RegisterDefinition[] registers = {
                new RegisterDefinition("param1", false, null, null, false),
                new RegisterDefinition("temp1", false, null, null, true)
        };

        RulesFunction[] functions = {
                new TestFunction("parseUrl", 1)
        };

        int[] bddNodes = {-1, 1, -1};
        Bytecode bytecode = writer.build(registers, functions, bddNodes, 1);

        String result = new BytecodeDisassembler(bytecode).disassemble();

        // Verify instruction disassembly
        assertThat(result, containsString("LOAD_REGISTER"));
        assertThat(result, containsString("SET_REGISTER"));
        assertThat(result, containsString("FN1"));
        assertThat(result, containsString("param1"));
        assertThat(result, containsString("temp1"));
        assertThat(result, containsString("parseUrl"));
    }

    @Test
    void disassemblesConstantPool() {
        BytecodeWriter writer = new BytecodeWriter();

        // Add various constant types
        int stringConst = writer.getConstantIndex("test string");
        writer.getConstantIndex(42);
        writer.getConstantIndex(true);
        writer.getConstantIndex(List.of("a", "b"));
        writer.getConstantIndex(Map.of("key", "value"));

        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(stringConst);
        writer.writeByte(Opcodes.RETURN_VALUE);

        Bytecode bytecode = writer.build(
                new RegisterDefinition[0],
                new RulesFunction[0],
                new int[] {-1, 1, -1},
                1);

        String result = new BytecodeDisassembler(bytecode).disassemble();

        // Verify constant pool section
        assertThat(result, containsString("=== Constant Pool ==="));
        assertThat(result, containsString("\"test string\""));
        assertThat(result, containsString("Integer[42]"));
        assertThat(result, containsString("Boolean[true]"));
        assertThat(result, containsString("List[2 items]"));
        assertThat(result, containsString("Map[1 entries]"));
    }

    @Test
    void disassemblesRegisters() {
        RegisterDefinition[] registers = {
                new RegisterDefinition("required", true, null, null, false),
                new RegisterDefinition("withDefault", false, "default", null, false),
                new RegisterDefinition("withBuiltin", false, null, "SDK::Endpoint", false),
                new RegisterDefinition("temp", false, null, null, true)
        };

        BytecodeWriter writer = new BytecodeWriter();
        writer.markConditionStart();
        writer.writeByte(Opcodes.RETURN_VALUE);

        Bytecode bytecode = writer.build(registers, new RulesFunction[0], new int[] {-1, 1, -1}, 1);
        String result = new BytecodeDisassembler(bytecode).disassemble();

        // Verify register information
        assertThat(result, containsString("=== Registers ==="));
        assertThat(result, containsString("required"));
        assertThat(result, containsString("[required]"));
        assertThat(result, containsString("withDefault"));
        assertThat(result, containsString("default=\"default\""));
        assertThat(result, containsString("withBuiltin"));
        assertThat(result, containsString("builtin=SDK::Endpoint"));
        assertThat(result, containsString("temp"));
        assertThat(result, containsString("[temp]"));
    }

    @Test
    void disassemblesBddStructure() {
        int[] bddNodes = {
                -1,
                1,
                -1, // terminal node
                0,
                2,
                3, // condition 0, high=node1, low=node2
                1,
                1,
                -1 // condition 1, high=TRUE, low=FALSE
        };

        BytecodeWriter writer = new BytecodeWriter();
        writer.markConditionStart();
        writer.writeByte(Opcodes.RETURN_VALUE);

        Bytecode bytecode = writer.build(
                new RegisterDefinition[0],
                new RulesFunction[0],
                bddNodes,
                2); // root = node 1 (0-based)

        String result = new BytecodeDisassembler(bytecode).disassemble();

        assertThat(result, containsString("=== BDD Structure ==="));
        assertThat(result, containsString("Bdd {"));
        assertThat(result, containsString("conditions:"));
        assertThat(result, containsString("results:"));
        assertThat(result, containsString("root:"));
    }

    @Test
    void handlesEmptyProgram() {
        BytecodeWriter writer = new BytecodeWriter();
        Bytecode bytecode = writer.build(new RegisterDefinition[0], new RulesFunction[0], new int[] {-1, 1, -1}, 1);

        String result = new BytecodeDisassembler(bytecode).disassemble();

        assertThat(result, containsString("=== Bytecode Program ==="));
        assertThat(result, containsString("Conditions: 0"));
        assertThat(result, containsString("Results: 0"));
        assertThat(result, containsString("Registers: 0"));
        assertThat(result, containsString("Functions: 0"));
    }

    private record TestFunction(String name, int argCount) implements RulesFunction {
        @Override
        public String getFunctionName() {
            return name;
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }

        @Override
        public Object apply1(Object arg1) {
            return "result";
        }
    }
}
