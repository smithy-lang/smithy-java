/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BytecodeWalkerTest {
    @Test
    void testBasicWalking() {
        byte[] bytecode = {Opcodes.LOAD_CONST, 5, Opcodes.NOT, Opcodes.RETURN_VALUE};
        BytecodeWalker walker = new BytecodeWalker(bytecode);

        // First instruction: LOAD_CONST
        assertTrue(walker.hasNext());
        assertEquals(0, walker.getPosition());
        assertEquals(Opcodes.LOAD_CONST, walker.currentOpcode());
        assertEquals(2, walker.getInstructionLength());
        assertEquals(1, walker.getOperandCount());
        assertEquals(5, walker.getOperand(0));

        // Advance to NOT
        assertTrue(walker.advance());
        assertEquals(2, walker.getPosition());
        assertEquals(Opcodes.NOT, walker.currentOpcode());
        assertEquals(1, walker.getInstructionLength());
        assertEquals(0, walker.getOperandCount());

        // Advance to RETURN_VALUE
        assertTrue(walker.advance());
        assertEquals(3, walker.getPosition());
        assertEquals(Opcodes.RETURN_VALUE, walker.currentOpcode());
        assertTrue(walker.isReturnOpcode());

        // Can advance past RETURN_VALUE to position 4 (end of bytecode)
        assertTrue(walker.advance());
        assertEquals(4, walker.getPosition());

        // Now at end, hasNext() is false
        assertFalse(walker.hasNext());

        // And advance() returns false
        assertFalse(walker.advance());
    }

    @Test
    void testJumpInstruction() {
        byte[] bytecode = {
                Opcodes.LOAD_CONST,
                0, // 0-1
                Opcodes.JNN_OR_POP,
                0,
                3, // 2-4 (jump to 8: 2 + 3 + 3)
                Opcodes.LOAD_CONST,
                1, // 5-6
                Opcodes.RETURN_VALUE // 7
        };
        BytecodeWalker walker = new BytecodeWalker(bytecode, 2);

        assertEquals(Opcodes.JNN_OR_POP, walker.currentOpcode());
        assertEquals(3, walker.getOperand(0));
        assertEquals(8, walker.getJumpTarget());
    }

    @Test
    void testMultiOperandInstructions() {
        byte[] bytecode = {Opcodes.SUBSTRING, 1, 5, 0, Opcodes.RETURN_VALUE};
        BytecodeWalker walker = new BytecodeWalker(bytecode);

        assertEquals(Opcodes.SUBSTRING, walker.currentOpcode());
        assertEquals(3, walker.getOperandCount());
        assertEquals(1, walker.getOperand(0));
        assertEquals(5, walker.getOperand(1));
        assertEquals(0, walker.getOperand(2));

        assertThrows(IllegalArgumentException.class, () -> walker.getOperand(3));
    }

    @Test
    void testPropertyAccessInstructions() {
        byte[] bytecode = {
                Opcodes.GET_PROPERTY_REG,
                2,
                0x00,
                0x0A, // register 2, property index 10
                Opcodes.GET_INDEX_REG,
                3,
                5, // register 3, index 5
                Opcodes.RETURN_VALUE
        };
        BytecodeWalker walker = new BytecodeWalker(bytecode);

        // GET_PROPERTY_REG
        assertEquals(Opcodes.GET_PROPERTY_REG, walker.currentOpcode());
        assertEquals(2, walker.getOperandCount());
        assertEquals(2, walker.getOperand(0)); // register
        assertEquals(10, walker.getOperand(1)); // property index (0x000A)

        // Advance to GET_INDEX_REG
        assertTrue(walker.advance());
        assertEquals(Opcodes.GET_INDEX_REG, walker.currentOpcode());
        assertEquals(2, walker.getOperandCount());
        assertEquals(3, walker.getOperand(0)); // register
        assertEquals(5, walker.getOperand(1)); // index
    }

    @Test
    void testEmptyBytecode() {
        BytecodeWalker walker = new BytecodeWalker(new byte[0]);

        assertFalse(walker.hasNext());
        assertEquals(0, walker.getPosition());
        assertThrows(IllegalStateException.class, walker::currentOpcode);
        assertFalse(walker.advance());
    }

    @Test
    void testStartOffset() {
        byte[] bytecode = {Opcodes.NOT, Opcodes.NOT, Opcodes.RETURN_VALUE};
        BytecodeWalker walker = new BytecodeWalker(bytecode, 1);

        assertEquals(1, walker.getPosition());
        assertEquals(Opcodes.NOT, walker.currentOpcode());

        assertTrue(walker.advance());
        assertEquals(2, walker.getPosition());
        assertEquals(Opcodes.RETURN_VALUE, walker.currentOpcode());
    }
}
