/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.java.client.rulesengine;

import java.util.Map;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;

/**
 * Provides a human-readable representation of a Bytecode program.
 */
final class BytecodeDisassembler {

    private static final Map<Byte, InstructionDef> INSTRUCTION_DEFS = Map.ofEntries(
            Map.entry(Opcodes.LOAD_CONST, new InstructionDef("LOAD_CONST", OperandType.BYTE, Show.CONST)),
            Map.entry(Opcodes.LOAD_CONST_W, new InstructionDef("LOAD_CONST_W", OperandType.SHORT, Show.CONST)),
            Map.entry(Opcodes.SET_REGISTER, new InstructionDef("SET_REGISTER", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.LOAD_REGISTER, new InstructionDef("LOAD_REGISTER", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.NOT, new InstructionDef("NOT", OperandType.NONE)),
            Map.entry(Opcodes.ISSET, new InstructionDef("ISSET", OperandType.NONE)),
            Map.entry(Opcodes.TEST_REGISTER_ISSET,
                      new InstructionDef("TEST_REGISTER_ISSET", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.TEST_REGISTER_NOT_SET,
                      new InstructionDef("TEST_REGISTER_NOT_SET", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.RETURN_ERROR, new InstructionDef("RETURN_ERROR", OperandType.NONE)),
            Map.entry(Opcodes.RETURN_ENDPOINT,
                      new InstructionDef("RETURN_ENDPOINT", OperandType.BYTE, Show.ENDPOINT_FLAGS)),
            Map.entry(Opcodes.CREATE_LIST, new InstructionDef("CREATE_LIST", OperandType.BYTE, Show.NUMBER)),
            Map.entry(Opcodes.CREATE_MAP, new InstructionDef("CREATE_MAP", OperandType.BYTE, Show.NUMBER)),
            Map.entry(Opcodes.RESOLVE_TEMPLATE,
                      new InstructionDef("RESOLVE_TEMPLATE", OperandType.SHORT, Show.CONST)),
            Map.entry(Opcodes.FN, new InstructionDef("FN", OperandType.BYTE, Show.FN)),
            Map.entry(Opcodes.GET_ATTR, new InstructionDef("GET_ATTR", OperandType.SHORT, Show.CONST)),
            Map.entry(Opcodes.IS_TRUE, new InstructionDef("IS_TRUE", OperandType.NONE)),
            Map.entry(Opcodes.TEST_REGISTER_IS_TRUE,
                      new InstructionDef("TEST_REGISTER_IS_TRUE", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.TEST_REGISTER_IS_FALSE,
                      new InstructionDef("TEST_REGISTER_IS_FALSE", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.RETURN_VALUE, new InstructionDef("RETURN_VALUE", OperandType.NONE)),
            Map.entry(Opcodes.EQUALS, new InstructionDef("EQUALS", OperandType.NONE)),
            Map.entry(Opcodes.SUBSTRING, new InstructionDef("SUBSTRING", OperandType.THREE_BYTES, Show.SUBSTRING)));

    // Enum to define operand types
    private enum OperandType {
        NONE(0),
        BYTE(1),
        SHORT(2),
        THREE_BYTES(3);

        private final int byteCount;

        OperandType(int byteCount) {
            this.byteCount = byteCount;
        }
    }

    private enum Show {
        CONST, FN, REGISTER, NUMBER, ENDPOINT_FLAGS, SUBSTRING
    }

    // Instruction definition class
    private record InstructionDef(String name, OperandType operandType, Show show) {
        InstructionDef(String name, OperandType operandType) {
            this(name, operandType, null);
        }
    }

    // Result class for operand parsing
    private record OperandResult(int value, int nextPc) {}

    private final Bytecode bytecode;

    BytecodeDisassembler(Bytecode bytecode) {
        this.bytecode = bytecode;
    }

    String disassemble() {
        StringBuilder s = new StringBuilder();

        // Header
        s.append("=== Bytecode Program ===\n");
        s.append("Conditions: ").append(bytecode.getConditionCount()).append("\n");
        s.append("Results: ").append(bytecode.getResultCount()).append("\n");
        s.append("Registers: ").append(bytecode.getRegisterDefinitions().length).append("\n");
        s.append("Functions: ").append(bytecode.getFunctions().length).append("\n");
        s.append("Constants: ").append(bytecode.getConstantPoolCount()).append("\n");
        s.append("BDD Nodes: ").append(bytecode.getBddNodes().length).append("\n");
        s.append("BDD Root: ").append(formatBddReference(bytecode.getBddRootRef())).append("\n");
        s.append("\n");

        // Functions
        if (bytecode.getFunctions().length > 0) {
            s.append("=== Functions ===\n");
            int i = 0;
            for (var fn : bytecode.getFunctions()) {
                s.append(String.format("  %2d: %-20s [%d args]\n",
                                       i++,
                                       fn.getFunctionName(),
                                       fn.getArgumentCount()));
            }
            s.append("\n");
        }

        // Registers
        if (bytecode.getRegisterDefinitions().length > 0) {
            s.append("=== Registers ===\n");
            int i = 0;
            for (var r : bytecode.getRegisterDefinitions()) {
                s.append(String.format("  %2d: %-20s", i++, r.name()));
                if (r.required()) s.append(" [required]");
                if (r.temp()) s.append(" [temp]");
                if (r.defaultValue() != null) {
                    s.append(" default=").append(formatValue(r.defaultValue()));
                }
                if (r.builtin() != null) {
                    s.append(" builtin=").append(r.builtin());
                }
                s.append("\n");
            }
            s.append("\n");
        }

        // BDD Structure
        if (bytecode.getBddNodes().length > 0) {
            s.append("=== BDD Structure ===\n");
            s.append("Root: ").append(formatBddReference(bytecode.getBddRootRef())).append("\n");
            s.append("Nodes:\n");

            int[][] nodes = bytecode.getBddNodes();

            // Calculate width needed for node indices
            int indexWidth = String.valueOf(nodes.length - 1).length();

            for (int i = 0; i < nodes.length; i++) {
                int[] node = nodes[i];
                s.append(String.format("    %" + indexWidth + "d: ", i));

                if (i == 0 && node[0] == -1) {
                    s.append("terminal");
                } else {
                    // Format variable reference
                    String varRef;
                    if (node[0] >= 0 && node[0] < bytecode.getConditionCount()) {
                        varRef = String.format("C%d", node[0]);
                    } else {
                        varRef = String.valueOf(node[0]);
                    }
                    s.append("[")
                            .append(varRef)
                            .append(", ")
                            .append(String.format("%6s", formatBddReference(node[1])))
                            .append(", ")
                            .append(String.format("%6s", formatBddReference(node[2])))
                            .append("]");
                }
                s.append("\n");
            }
            s.append("\n");
        }

        // Constants
        if (bytecode.getConstantPoolCount() > 0) {
            s.append("=== Constant Pool ===\n");
            for (int i = 0; i < bytecode.getConstantPoolCount(); i++) {
                s.append(String.format("  %3d: ", i));
                s.append(formatConstant(bytecode.getConstant(i))).append("\n");
            }
            s.append("\n");
        }

        // Conditions
        if (bytecode.getConditionCount() > 0) {
            s.append("=== Conditions ===\n");
            for (int i = 0; i < bytecode.getConditionCount(); i++) {
                s.append(String.format("Condition %d:\n", i));
                int startOffset = bytecode.getConditionStartOffset(i);
                int endOffset = bytecode.getConditionEndOffset(i);
                disassembleSection(s, startOffset, endOffset, "  ");
                s.append("\n");
            }
        }

        // Results
        if (bytecode.getResultCount() > 0) {
            s.append("=== Results ===\n");
            for (int i = 0; i < bytecode.getResultCount(); i++) {
                s.append(String.format("Result %d:\n", i));
                int startOffset = bytecode.getResultOffset(i);
                int endOffset = findResultEndOffset(i);
                disassembleSection(s, startOffset, endOffset, "  ");
                s.append("\n");
            }
        }

        return s.toString();
    }

    private String formatBddReference(int ref) {
        if (ref == 0) {
            return "0"; // Invalid reference
        } else if (ref == 1) {
            return "TRUE";
        } else if (ref == -1) {
            return "FALSE";
        } else if (ref >= Bdd.RESULT_OFFSET) {
            // Result reference
            return "R" + (ref - Bdd.RESULT_OFFSET);
        } else if (ref < 0) {
            // Complement edge - show as negative of node index
            return "!" + (-ref - 1);
        } else {
            // Normal node reference - convert to 0-based index
            return String.valueOf(ref - 1);
        }
    }

    private int findResultEndOffset(int resultIndex) {
        // For results, calculate end offset
        if (resultIndex + 1 < bytecode.getResultCount()) {
            return bytecode.getResultOffset(resultIndex + 1);
        }
        // Last result goes to end of bytecode
        return bytecode.getBytecode().length;
    }

    private void disassembleSection(StringBuilder s, int startOffset, int endOffset, String indent) {
        byte[] instructions = bytecode.getBytecode();

        for (int pc = startOffset; pc < endOffset && pc < instructions.length; pc++) {
            pc = writeInstruction(s, pc, indent);
            if (pc < 0) {
                break;
            }
        }
    }

    private int writeInstruction(StringBuilder s, int pc, String indent) {
        byte[] instructions = bytecode.getBytecode();

        // instruction address
        s.append(indent).append(String.format("%04d: ", pc));

        byte opcode = instructions[pc];
        InstructionDef def = INSTRUCTION_DEFS.get(opcode);

        // Handle unknown instruction
        if (def == null) {
            s.append(String.format("UNKNOWN_OPCODE(0x%02X)\n", opcode));
            return -1;
        }

        s.append(String.format("%-22s", def.name()));

        // Parse operands based on type
        OperandResult operandResult = parseOperands(s, pc, def.operandType(), instructions);
        int nextPc = operandResult.nextPc();
        int displayValue = operandResult.value();

        // Add symbolic information if available
        if (def.show() != null) {
            s.append("  ; ");
            appendSymbolicInfo(s, pc, displayValue, def.show, instructions);
        }

        s.append("\n");
        return nextPc;
    }

    private OperandResult parseOperands(StringBuilder s, int pc, OperandType type, byte[] instructions) {
        return switch (type) {
            case NONE -> new OperandResult(-1, pc);
            case BYTE -> {
                int value = appendByte(s, pc, instructions);
                yield new OperandResult(value, pc + 1);
            }
            case SHORT -> {
                int value = appendShort(s, pc, instructions);
                yield new OperandResult(value, pc + 2);
            }
            case THREE_BYTES -> {
                s.append(" ");
                int b1 = appendByte(s, pc, instructions);
                int b2 = appendByte(s, pc + 1, instructions);
                int b3 = appendByte(s, pc + 2, instructions);
                yield new OperandResult(-1, pc + 3);
            }
        };
    }

    private void appendSymbolicInfo(StringBuilder s, int pc, int value, Show show, byte[] instructions) {
        switch (show) {
            case CONST -> {
                if (value >= 0 && value < bytecode.getConstantPoolCount()) {
                    s.append(formatConstant(bytecode.getConstant(value)));
                }
            }
            case FN -> {
                if (value >= 0 && value < bytecode.getFunctions().length) {
                    var fn = bytecode.getFunctions()[value];
                    s.append(fn.getFunctionName()).append("(").append(fn.getArgumentCount()).append(" args)");
                }
            }
            case REGISTER -> {
                if (value >= 0 && value < bytecode.getRegisterDefinitions().length) {
                    s.append(bytecode.getRegisterDefinitions()[value].name());
                }
            }
            case NUMBER -> s.append(value);
            case ENDPOINT_FLAGS -> {
                boolean hasHeaders = (value & 1) != 0;
                boolean hasProperties = (value & 2) != 0;
                s.append("headers=").append(hasHeaders).append(", properties=").append(hasProperties);
            }
            case SUBSTRING -> {
                if (pc + 3 < instructions.length) {
                    int start = instructions[pc + 1] & 0xFF;
                    int end = instructions[pc + 2] & 0xFF;
                    boolean reverse = (instructions[pc + 3] & 0xFF) != 0;
                    s.append("start=").append(start)
                            .append(", end=").append(end)
                            .append(", reverse=").append(reverse);
                }
            }
        }
    }

    private int appendByte(StringBuilder s, int pc, byte[] instructions) {
        if (instructions.length <= pc + 1) {
            s.append("??");
            return -1;
        } else {
            int result = instructions[pc + 1] & 0xFF;
            s.append(String.format("%3d", result));
            return result;
        }
    }

    private int appendShort(StringBuilder s, int pc, byte[] instructions) {
        if (instructions.length <= pc + 2) {
            s.append("??");
            return -1;
        } else {
            int result = EndpointUtils.bytesToShort(instructions, pc + 1);
            s.append(String.format("%5d", result));
            return result;
        }
    }

    private String formatConstant(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        } else if (value instanceof StringTemplate) {
            return "StringTemplate[" + value + "]";
        } else if (value instanceof AttrExpression) {
            return "AttrExpression[" + value + "]";
        } else {
            return value.getClass().getSimpleName() + "[" + value + "]";
        }
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        } else {
            return value.toString();
        }
    }

    private String escapeString(String s) {
        if (s.length() > 50) {
            s = s.substring(0, 47) + "...";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
