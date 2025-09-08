/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertContains(result, "=== Bytecode Program ===");
        assertContains(result, "Conditions: 1");
        assertContains(result, "Results: 1");
        assertContains(result, "Registers: 1");
        assertContains(result, "Functions: 1");
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
        assertContains(result, "LOAD_REGISTER");
        assertContains(result, "SET_REGISTER");
        assertContains(result, "FN1");
        assertContains(result, "param1");
        assertContains(result, "temp1");
        assertContains(result, "parseUrl");
    }

    @Test
    void disassemblesConstantPool() {
        BytecodeWriter writer = new BytecodeWriter();

        // Add various constant types
        int stringConst = writer.getConstantIndex("test string");
        int intConst = writer.getConstantIndex(42);
        int boolConst = writer.getConstantIndex(true);
        int listConst = writer.getConstantIndex(List.of("a", "b"));
        int mapConst = writer.getConstantIndex(Map.of("key", "value"));

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
        assertContains(result, "=== Constant Pool ===");
        assertContains(result, "\"test string\"");
        assertContains(result, "Integer[42]");
        assertContains(result, "Boolean[true]");
        assertContains(result, "List[2 items]");
        assertContains(result, "Map[1 entries]");
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
        assertContains(result, "=== Registers ===");
        assertContains(result, "required");
        assertContains(result, "[required]");
        assertContains(result, "withDefault");
        assertContains(result, "default=\"default\"");
        assertContains(result, "withBuiltin");
        assertContains(result, "builtin=SDK::Endpoint");
        assertContains(result, "temp");
        assertContains(result, "[temp]");
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

        assertContains(result, "=== BDD Structure ===");
        assertContains(result, "Bdd {");
        assertContains(result, "conditions:");
        assertContains(result, "results:");
        assertContains(result, "root:");
    }

    @Test
    void handlesEmptyProgram() {
        BytecodeWriter writer = new BytecodeWriter();
        Bytecode bytecode = writer.build(new RegisterDefinition[0], new RulesFunction[0], new int[] {-1, 1, -1}, 1);

        String result = new BytecodeDisassembler(bytecode).disassemble();

        assertContains(result, "=== Bytecode Program ===");
        assertContains(result, "Conditions: 0");
        assertContains(result, "Results: 0");
        assertContains(result, "Registers: 0");
        assertContains(result, "Functions: 0");
    }

    private void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), "Expected to find '" + expected + "' in:\n" + actual);
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
