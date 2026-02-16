/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.nio.ByteBuffer;

/**
 * Utility for walking through bytecode instructions, understanding instruction boundaries
 * and operand sizes. This prevents misinterpreting operands as opcodes.
 */
final class BytecodeWalker {
    private final ByteBuffer code;
    private int pc;

    public BytecodeWalker(byte[] code) {
        this(ByteBuffer.wrap(code));
    }

    public BytecodeWalker(byte[] code, int startOffset) {
        this(ByteBuffer.wrap(code), startOffset);
    }

    public BytecodeWalker(ByteBuffer code) {
        this(code, 0);
    }

    public BytecodeWalker(ByteBuffer code, int startOffset) {
        this.code = code.duplicate();
        this.pc = startOffset;
    }

    public boolean hasNext() {
        return pc < code.limit();
    }

    public int getPosition() {
        return pc;
    }

    public byte currentOpcode() {
        if (!hasNext()) {
            throw new IllegalStateException("No more instructions");
        }
        return code.get(pc);
    }

    public boolean advance() {
        if (!hasNext()) {
            return false;
        }
        int length = getInstructionLength();
        if (length < 0) {
            return false; // Unknown opcode
        }
        int next = pc + length;
        if (next > code.limit()) {
            return false; // Instruction extends past end
        }
        pc = next;
        return true;
    }

    public int getInstructionLength() {
        if (!hasNext()) {
            return -1;
        }
        int length = getInstructionLength(code.get(pc));
        // Validate the full instruction fits within the buffer
        if (length > 0 && pc + length > code.limit()) {
            return -1;
        }
        return length;
    }

    public int getOperandCount() {
        // If the instruction doesn't fully fit in the buffer, report 0 operands
        if (getInstructionLength() < 0) {
            return 0;
        }
        byte opcode = currentOpcode();
        return switch (opcode) {
            case Opcodes.NOT, Opcodes.ISSET, Opcodes.LIST0, Opcodes.LIST1, Opcodes.LIST2, Opcodes.MAP0, Opcodes.MAP1,
                    Opcodes.MAP2, Opcodes.MAP3, Opcodes.MAP4, Opcodes.IS_TRUE, Opcodes.EQUALS, Opcodes.STRING_EQUALS,
                    Opcodes.BOOLEAN_EQUALS, Opcodes.IS_VALID_HOST_LABEL, Opcodes.PARSE_URL, Opcodes.URI_ENCODE,
                    Opcodes.RETURN_ERROR, Opcodes.RETURN_VALUE, Opcodes.SPLIT ->
                0;
            case Opcodes.LOAD_CONST, Opcodes.SET_REGISTER, Opcodes.LOAD_REGISTER, Opcodes.TEST_REGISTER_ISSET,
                    Opcodes.TEST_REGISTER_NOT_SET, Opcodes.LISTN, Opcodes.MAPN, Opcodes.FN0, Opcodes.FN1, Opcodes.FN2,
                    Opcodes.FN3, Opcodes.FN, Opcodes.GET_INDEX, Opcodes.TEST_REGISTER_IS_TRUE,
                    Opcodes.TEST_REGISTER_IS_FALSE, Opcodes.RETURN_ENDPOINT, Opcodes.LOAD_CONST_W, Opcodes.GET_PROPERTY,
                    Opcodes.JNN_OR_POP, Opcodes.GET_NEGATIVE_INDEX, Opcodes.JMP_IF_FALSE, Opcodes.JUMP ->
                1;
            case Opcodes.GET_PROPERTY_REG, Opcodes.GET_INDEX_REG, Opcodes.RESOLVE_TEMPLATE,
                    Opcodes.GET_NEGATIVE_INDEX_REG ->
                2;
            case Opcodes.SUBSTRING, Opcodes.SPLIT_GET -> 3;
            case Opcodes.SUBSTRING_EQ -> 5;
            default -> -1;
        };
    }

    public int getOperand(int index) {
        byte opcode = currentOpcode();

        switch (opcode) {
            // Single byte operand instructions
            case Opcodes.LOAD_CONST:
            case Opcodes.SET_REGISTER:
            case Opcodes.LOAD_REGISTER:
            case Opcodes.TEST_REGISTER_ISSET:
            case Opcodes.TEST_REGISTER_NOT_SET:
            case Opcodes.LISTN:
            case Opcodes.MAPN:
            case Opcodes.FN0:
            case Opcodes.FN1:
            case Opcodes.FN2:
            case Opcodes.FN3:
            case Opcodes.FN:
            case Opcodes.GET_INDEX:
            case Opcodes.TEST_REGISTER_IS_TRUE:
            case Opcodes.TEST_REGISTER_IS_FALSE:
            case Opcodes.RETURN_ENDPOINT:
                if (index == 0) {
                    return code.get(pc + 1) & 0xFF;
                }
                break;

            // Two byte operand instructions
            case Opcodes.LOAD_CONST_W:
            case Opcodes.GET_PROPERTY:
            case Opcodes.JNN_OR_POP:
                if (index == 0) {
                    return ((code.get(pc + 1) & 0xFF) << 8) | (code.get(pc + 2) & 0xFF);
                }
                break;

            // Mixed operand instructions
            case Opcodes.GET_PROPERTY_REG:
                if (index == 0) {
                    return code.get(pc + 1) & 0xFF; // register
                } else if (index == 1) {
                    return ((code.get(pc + 2) & 0xFF) << 8) | (code.get(pc + 3) & 0xFF); // property index
                }
                break;

            case Opcodes.GET_INDEX_REG:
                if (index == 0) {
                    return code.get(pc + 1) & 0xFF; // register
                } else if (index == 1) {
                    return code.get(pc + 2) & 0xFF; // index
                }
                break;

            case Opcodes.RESOLVE_TEMPLATE:
                if (index == 0) {
                    return code.get(pc + 1) & 0xFF; // arg count
                } else if (index == 1) {
                    return ((code.get(pc + 2) & 0xFF) << 8) | (code.get(pc + 3) & 0xFF); // template index
                }
                break;

            case Opcodes.SUBSTRING:
                if (index >= 0 && index < 3) {
                    return code.get(pc + 1 + index) & 0xFF;
                }
                break;

            case Opcodes.GET_NEGATIVE_INDEX:
                if (index == 0) {
                    return code.get(pc + 1) & 0xFF;
                }
                break;

            case Opcodes.GET_NEGATIVE_INDEX_REG:
                if (index == 0) {
                    return code.get(pc + 1) & 0xFF; // register
                } else if (index == 1) {
                    return code.get(pc + 2) & 0xFF; // index
                }
                break;

            case Opcodes.JMP_IF_FALSE:
            case Opcodes.JUMP:
                if (index == 0) {
                    return ((code.get(pc + 1) & 0xFF) << 8) | (code.get(pc + 2) & 0xFF);
                }
                break;

            case Opcodes.SUBSTRING_EQ:
                if (index >= 0 && index < 4) {
                    return code.get(pc + 1 + index) & 0xFF;
                } else if (index == 4) {
                    return ((code.get(pc + 5) & 0xFF) << 8) | (code.get(pc + 6) & 0xFF);
                }
                break;

            case Opcodes.SPLIT_GET:
                if (index == 0) {
                    return code.get(pc + 1) & 0xFF; // register
                } else if (index == 1) {
                    return ((code.get(pc + 2) & 0xFF) << 8) | (code.get(pc + 3) & 0xFF); // delimiter const
                } else if (index == 2) {
                    return code.get(pc + 4) & 0xFF; // index (will be cast to signed byte by caller)
                }
                break;
        }

        throw new IllegalArgumentException("Invalid operand index " + index + " for opcode " + opcode);
    }

    public int getJumpTarget() {
        byte opcode = currentOpcode();
        if (opcode == Opcodes.JNN_OR_POP || opcode == Opcodes.JMP_IF_FALSE || opcode == Opcodes.JUMP) {
            int offset = getOperand(0);
            return pc + getInstructionLength(opcode) + offset;
        }
        throw new IllegalStateException("Not a jump instruction: " + opcode);
    }

    public boolean isReturnOpcode() {
        byte op = currentOpcode();
        return op == Opcodes.RETURN_VALUE || op == Opcodes.RETURN_ENDPOINT || op == Opcodes.RETURN_ERROR;
    }

    public static int getInstructionLength(byte opcode) {
        return switch (opcode) {
            case Opcodes.NOT, Opcodes.ISSET, Opcodes.LIST0, Opcodes.LIST1, Opcodes.LIST2, Opcodes.MAP0, Opcodes.MAP1,
                    Opcodes.MAP2, Opcodes.MAP3, Opcodes.MAP4, Opcodes.IS_TRUE, Opcodes.EQUALS, Opcodes.STRING_EQUALS,
                    Opcodes.BOOLEAN_EQUALS, Opcodes.IS_VALID_HOST_LABEL, Opcodes.PARSE_URL, Opcodes.URI_ENCODE,
                    Opcodes.RETURN_ERROR, Opcodes.RETURN_VALUE, Opcodes.SPLIT ->
                1;
            case Opcodes.LOAD_CONST, Opcodes.SET_REGISTER, Opcodes.LOAD_REGISTER, Opcodes.TEST_REGISTER_ISSET,
                    Opcodes.TEST_REGISTER_NOT_SET, Opcodes.LISTN, Opcodes.MAPN, Opcodes.FN0, Opcodes.FN1, Opcodes.FN2,
                    Opcodes.FN3, Opcodes.FN, Opcodes.GET_INDEX, Opcodes.TEST_REGISTER_IS_TRUE,
                    Opcodes.TEST_REGISTER_IS_FALSE, Opcodes.RETURN_ENDPOINT, Opcodes.GET_NEGATIVE_INDEX ->
                2;
            case Opcodes.LOAD_CONST_W, Opcodes.GET_PROPERTY, Opcodes.JNN_OR_POP, Opcodes.GET_INDEX_REG,
                    Opcodes.GET_NEGATIVE_INDEX_REG, Opcodes.JMP_IF_FALSE, Opcodes.JUMP ->
                3;
            case Opcodes.RESOLVE_TEMPLATE, Opcodes.GET_PROPERTY_REG, Opcodes.SUBSTRING -> 4;
            case Opcodes.SPLIT_GET -> 5;
            case Opcodes.SUBSTRING_EQ -> 7;
            default -> -1;
        };
    }
}
