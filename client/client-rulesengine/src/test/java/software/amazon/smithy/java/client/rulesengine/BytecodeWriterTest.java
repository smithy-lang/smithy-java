/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BytecodeWriterTest {

    @Test
    void testBasicBytecodeWriting() {
        BytecodeWriter writer = new BytecodeWriter();

        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        Bytecode bytecode = writer.build(
                new RegisterDefinition[0],
                new RulesFunction[0],
                new int[0],
                0);

        byte[] bytes = bytecode.getBytecode();
        assertEquals(3, bytes.length);
        assertEquals(Opcodes.LOAD_CONST, bytes[0]);
        assertEquals(0, bytes[1]);
        assertEquals(Opcodes.RETURN_VALUE, bytes[2]);
    }

    @Test
    void testWriteShort() {
        BytecodeWriter writer = new BytecodeWriter();

        writer.writeByte(Opcodes.LOAD_CONST_W);
        writer.writeShort(0x1234);

        Bytecode bytecode = writer.build(
                new RegisterDefinition[0],
                new RulesFunction[0],
                new int[0],
                0);

        byte[] bytes = bytecode.getBytecode();
        assertEquals(3, bytes.length);
        assertEquals(Opcodes.LOAD_CONST_W, bytes[0]);
        assertEquals(0x12, bytes[1] & 0xFF);
        assertEquals(0x34, bytes[2] & 0xFF);
    }

    @Test
    void testConstantPoolManagement() {
        BytecodeWriter writer = new BytecodeWriter();

        // Add different types of constants
        int stringIdx = writer.getConstantIndex("hello");
        int intIdx = writer.getConstantIndex(42);
        int boolIdx = writer.getConstantIndex(true);
        int nullIdx = writer.getConstantIndex(null);
        int listIdx = writer.getConstantIndex(List.of("a", "b"));
        int mapIdx = writer.getConstantIndex(Map.of("key", "value"));

        // Same constant should return same index
        int stringIdx2 = writer.getConstantIndex("hello");
        assertEquals(stringIdx, stringIdx2);

        // Different constants should have different indices
        assertTrue(stringIdx != intIdx);
        assertTrue(intIdx != boolIdx);

        Bytecode bytecode = writer.build(new RegisterDefinition[0], new RulesFunction[0], new int[0], 0);

        // Verify constants were stored correctly
        assertEquals("hello", bytecode.getConstant(stringIdx));
        assertEquals(42, bytecode.getConstant(intIdx));
        assertEquals(true, bytecode.getConstant(boolIdx));
        assertNull(bytecode.getConstant(nullIdx));
        assertEquals(List.of("a", "b"), bytecode.getConstant(listIdx));
        assertEquals(Map.of("key", "value"), bytecode.getConstant(mapIdx));
    }

    @Test
    void testConditionAndResultOffsets() {
        BytecodeWriter writer = new BytecodeWriter();

        // Write first condition
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(0);
        writer.writeByte(Opcodes.RETURN_VALUE);

        // Write second condition
        writer.markConditionStart();
        writer.writeByte(Opcodes.LOAD_REGISTER);
        writer.writeByte(1);
        writer.writeByte(Opcodes.RETURN_VALUE);

        // Write first result
        writer.markResultStart();
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(2);
        writer.writeByte(Opcodes.RETURN_ENDPOINT);
        writer.writeByte(0);

        Bytecode bytecode = writer.build(new RegisterDefinition[0], new RulesFunction[0], new int[0], 0);

        assertEquals(2, bytecode.getConditionCount());
        assertEquals(1, bytecode.getResultCount());

        assertEquals(0, bytecode.getConditionStartOffset(0));
        assertEquals(3, bytecode.getConditionStartOffset(1));
        assertEquals(6, bytecode.getResultOffset(0));
    }

    @Test
    void testFunctionRegistration() {
        BytecodeWriter writer = new BytecodeWriter();

        writer.registerFunction("parseUrl");
        writer.registerFunction("isValidHostLabel");
        writer.registerFunction("parseUrl"); // Duplicate should be ignored

        TestFunction parseUrl = new TestFunction("parseUrl", 1);
        TestFunction isValid = new TestFunction("isValidHostLabel", 2);

        Bytecode bytecode = writer.build(
                new RegisterDefinition[0],
                new RulesFunction[] {parseUrl, isValid},
                new int[0],
                0);

        assertEquals(2, bytecode.getFunctions().length);
        assertEquals("parseUrl", bytecode.getFunctions()[0].getFunctionName());
        assertEquals("isValidHostLabel", bytecode.getFunctions()[1].getFunctionName());
    }

    @Test
    void testRegisterDefinitions() {
        BytecodeWriter writer = new BytecodeWriter();

        RegisterDefinition[] defs = {
                new RegisterDefinition("region", true, null, null, false),
                new RegisterDefinition("useDualStack", false, false, null, false),
                new RegisterDefinition("endpoint", false, null, "SDK::Endpoint", false)
        };

        Bytecode bytecode = writer.build(defs, new RulesFunction[0], new int[0], 0);

        RegisterDefinition[] loadedDefs = bytecode.getRegisterDefinitions();
        assertEquals(3, loadedDefs.length);
        assertEquals("region", loadedDefs[0].name());
        assertEquals("useDualStack", loadedDefs[1].name());
        assertEquals("endpoint", loadedDefs[2].name());
    }

    @Test
    void testBddNodes() {
        BytecodeWriter writer = new BytecodeWriter();

        // BDD with 2 nodes
        int[] bddNodes = {
                0,
                1,
                -1, // Node 0: var=0, high=TRUE, low=FALSE
                1,
                100_000_000,
                100_000_001 // Node 1: var=1, high=Result0, low=Result1
        };

        Bytecode bytecode = writer.build(
                new RegisterDefinition[0],
                new RulesFunction[0],
                bddNodes,
                2 // root ref = 2 (points to node 1)
        );

        assertEquals(2, bytecode.getBddNodeCount());
        assertEquals(2, bytecode.getBddRootRef());

        int[] loadedNodes = bytecode.getBddNodes();
        assertArrayEquals(bddNodes, loadedNodes);
    }

    @Test
    void testJumpLabels() {
        BytecodeWriter writer = new BytecodeWriter();

        // Create a simple jump pattern
        String endLabel = writer.createLabel();

        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(0);
        writer.writeByte(Opcodes.JNN_OR_POP);
        writer.writeJumpPlaceholder(endLabel);

        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(1);

        writer.markLabel(endLabel);
        writer.writeByte(Opcodes.RETURN_VALUE);

        Bytecode bytecode = writer.build(new RegisterDefinition[0], new RulesFunction[0], new int[0], 0);

        byte[] bytes = bytecode.getBytecode();

        // Verify structure
        assertEquals(Opcodes.LOAD_CONST, bytes[0]);
        assertEquals(0, bytes[1]);
        assertEquals(Opcodes.JNN_OR_POP, bytes[2]);

        // Jump offset should be 2 (from position 5 to position 7)
        // The jump is relative to the position AFTER the jump instruction
        int jumpOffset = ((bytes[3] & 0xFF) << 8) | (bytes[4] & 0xFF);
        assertEquals(2, jumpOffset);

        assertEquals(Opcodes.LOAD_CONST, bytes[5]);
        assertEquals(1, bytes[6]);
        assertEquals(Opcodes.RETURN_VALUE, bytes[7]);
    }

    @Test
    void testMultipleLabelsAndJumps() {
        BytecodeWriter writer = new BytecodeWriter();

        String label1 = writer.createLabel();
        String label2 = writer.createLabel();

        // Position 0: Jump to label1
        writer.writeByte(Opcodes.JNN_OR_POP);
        writer.writeJumpPlaceholder(label1);

        // Position 3: Some code
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(0);

        // Position 5: Jump to label2
        writer.writeByte(Opcodes.JNN_OR_POP);
        writer.writeJumpPlaceholder(label2);

        // Position 8: Label1 location
        writer.markLabel(label1);
        writer.writeByte(Opcodes.LOAD_CONST);
        writer.writeByte(1);

        // Position 10: Label2 location
        writer.markLabel(label2);
        writer.writeByte(Opcodes.RETURN_VALUE);

        Bytecode bytecode = writer.build(new RegisterDefinition[0], new RulesFunction[0], new int[0], 0);

        byte[] bytes = bytecode.getBytecode();

        // Layout:
        // 0: JNN_OR_POP
        // 1-2: jump offset (2 bytes)
        // 3: LOAD_CONST
        // 4: 0
        // 5: JNN_OR_POP
        // 6-7: jump offset (2 bytes)
        // 8: LOAD_CONST (label1 is here)
        // 9: 1
        // 10: RETURN_VALUE (label2 is here)

        // First jump: from position 3 (after JNN_OR_POP + 2 bytes) to position 8 (label1)
        // Offset = 8 - 3 = 5
        int jump1 = ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
        assertEquals(5, jump1);

        // Second jump: from position 8 (after second JNN_OR_POP + 2 bytes) to position 10 (label2)
        // Offset = 10 - 8 = 2
        int jump2 = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        assertEquals(2, jump2);
    }

    @Test
    void testZeroJump() {
        BytecodeWriter writer = new BytecodeWriter();

        String label = writer.createLabel();

        writer.writeByte(Opcodes.JNN_OR_POP);
        writer.writeJumpPlaceholder(label);
        writer.markLabel(label); // Label immediately after jump
        writer.writeByte(Opcodes.RETURN_VALUE);

        Bytecode bytecode = writer.build(new RegisterDefinition[0], new RulesFunction[0], new int[0], 0);

        byte[] bytes = bytecode.getBytecode();

        // Jump offset should be 0 (no bytes to skip)
        int jumpOffset = ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
        assertEquals(0, jumpOffset);
    }

    @Test
    void testConstantDeduplication() {
        BytecodeWriter writer = new BytecodeWriter();

        // String interning deduplicates
        String str1 = new String("test");
        String str2 = new String("test");

        int idx1 = writer.getConstantIndex(str1);
        int idx2 = writer.getConstantIndex(str2);

        assertEquals(idx1, idx2);

        // But different strings should have different indices
        int idx3 = writer.getConstantIndex("different");
        assertNotEquals(idx1, idx3);
    }

    // Simple test implementation of RulesFunction
    private static class TestFunction implements RulesFunction {
        private final String name;
        private final int argCount;

        TestFunction(String name, int argCount) {
            this.name = name;
            this.argCount = argCount;
        }

        @Override
        public String getFunctionName() {
            return name;
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }
    }
}
