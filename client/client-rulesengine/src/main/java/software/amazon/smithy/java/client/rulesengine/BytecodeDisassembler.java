/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.rulesengine.logic.bdd.BddFormatter;

/**
 * Provides a human-readable representation of a Bytecode program.
 */
final class BytecodeDisassembler {

    private static final Map<Byte, InstructionDef> INSTRUCTION_DEFS = Map.ofEntries(
            // Basic stack operations
            Map.entry(Opcodes.LOAD_CONST, new InstructionDef("LOAD_CONST", OperandType.BYTE, Show.CONST)),
            Map.entry(Opcodes.LOAD_CONST_W, new InstructionDef("LOAD_CONST_W", OperandType.SHORT, Show.CONST)),
            Map.entry(Opcodes.SET_REGISTER, new InstructionDef("SET_REGISTER", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.LOAD_REGISTER, new InstructionDef("LOAD_REGISTER", OperandType.BYTE, Show.REGISTER)),

            // Boolean operations
            Map.entry(Opcodes.NOT, new InstructionDef("NOT", OperandType.NONE)),
            Map.entry(Opcodes.ISSET, new InstructionDef("ISSET", OperandType.NONE)),
            Map.entry(Opcodes.TEST_REGISTER_ISSET,
                    new InstructionDef("TEST_REGISTER_ISSET", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.TEST_REGISTER_NOT_SET,
                    new InstructionDef("TEST_REGISTER_NOT_SET", OperandType.BYTE, Show.REGISTER)),

            // List operations
            Map.entry(Opcodes.LIST0, new InstructionDef("LIST0", OperandType.NONE)),
            Map.entry(Opcodes.LIST1, new InstructionDef("LIST1", OperandType.NONE)),
            Map.entry(Opcodes.LIST2, new InstructionDef("LIST2", OperandType.NONE)),
            Map.entry(Opcodes.LISTN, new InstructionDef("LISTN", OperandType.BYTE, Show.NUMBER)),

            // Map operations
            Map.entry(Opcodes.MAP0, new InstructionDef("MAP0", OperandType.NONE)),
            Map.entry(Opcodes.MAP1, new InstructionDef("MAP1", OperandType.NONE)),
            Map.entry(Opcodes.MAP2, new InstructionDef("MAP2", OperandType.NONE)),
            Map.entry(Opcodes.MAP3, new InstructionDef("MAP3", OperandType.NONE)),
            Map.entry(Opcodes.MAP4, new InstructionDef("MAP4", OperandType.NONE)),
            Map.entry(Opcodes.MAPN, new InstructionDef("MAPN", OperandType.BYTE, Show.NUMBER)),

            // Template operation
            Map.entry(Opcodes.RESOLVE_TEMPLATE, new InstructionDef("RESOLVE_TEMPLATE", OperandType.BYTE, Show.NUMBER)),

            // Function operations (19-23)
            Map.entry(Opcodes.FN0, new InstructionDef("FN0", OperandType.BYTE, Show.FN)),
            Map.entry(Opcodes.FN1, new InstructionDef("FN1", OperandType.BYTE, Show.FN)),
            Map.entry(Opcodes.FN2, new InstructionDef("FN2", OperandType.BYTE, Show.FN)),
            Map.entry(Opcodes.FN3, new InstructionDef("FN3", OperandType.BYTE, Show.FN)),
            Map.entry(Opcodes.FN, new InstructionDef("FN", OperandType.BYTE, Show.FN)),

            // Property access operations
            Map.entry(Opcodes.GET_PROPERTY, new InstructionDef("GET_PROPERTY", OperandType.SHORT, Show.CONST)),
            Map.entry(Opcodes.GET_INDEX, new InstructionDef("GET_INDEX", OperandType.BYTE, Show.NUMBER)),
            Map.entry(Opcodes.GET_PROPERTY_REG,
                    new InstructionDef("GET_PROPERTY_REG", OperandType.BYTE_SHORT, Show.REG_PROPERTY)),
            Map.entry(Opcodes.GET_INDEX_REG,
                    new InstructionDef("GET_INDEX_REG", OperandType.TWO_BYTES, Show.REG_INDEX)),

            // Boolean test operations
            Map.entry(Opcodes.IS_TRUE, new InstructionDef("IS_TRUE", OperandType.NONE)),
            Map.entry(Opcodes.TEST_REGISTER_IS_TRUE,
                    new InstructionDef("TEST_REGISTER_IS_TRUE", OperandType.BYTE, Show.REGISTER)),
            Map.entry(Opcodes.TEST_REGISTER_IS_FALSE,
                    new InstructionDef("TEST_REGISTER_IS_FALSE", OperandType.BYTE, Show.REGISTER)),

            // Comparison operations
            Map.entry(Opcodes.EQUALS, new InstructionDef("EQUALS", OperandType.NONE)),
            Map.entry(Opcodes.STRING_EQUALS, new InstructionDef("STRING_EQUALS", OperandType.NONE)),
            Map.entry(Opcodes.BOOLEAN_EQUALS, new InstructionDef("BOOLEAN_EQUALS", OperandType.NONE)),

            // String operations
            Map.entry(Opcodes.SUBSTRING, new InstructionDef("SUBSTRING", OperandType.THREE_BYTES, Show.SUBSTRING)),
            Map.entry(Opcodes.IS_VALID_HOST_LABEL, new InstructionDef("IS_VALID_HOST_LABEL", OperandType.NONE)),
            Map.entry(Opcodes.PARSE_URL, new InstructionDef("PARSE_URL", OperandType.NONE)),
            Map.entry(Opcodes.URI_ENCODE, new InstructionDef("URI_ENCODE", OperandType.NONE)),

            // Return operations
            Map.entry(Opcodes.RETURN_ERROR, new InstructionDef("RETURN_ERROR", OperandType.NONE)),
            Map.entry(Opcodes.RETURN_ENDPOINT,
                    new InstructionDef("RETURN_ENDPOINT", OperandType.BYTE, Show.ENDPOINT_FLAGS)),
            Map.entry(Opcodes.RETURN_VALUE, new InstructionDef("RETURN_VALUE", OperandType.NONE)),

            // Control flow
            Map.entry(Opcodes.JT_OR_POP, new InstructionDef("JT_OR_POP", OperandType.SHORT, Show.JUMP_OFFSET)));

    // Enum to define operand types
    private enum OperandType {
        NONE(0),
        BYTE(1),
        SHORT(2),
        TWO_BYTES(2),
        BYTE_SHORT(3),
        THREE_BYTES(3);

        private final int byteCount;

        OperandType(int byteCount) {
            this.byteCount = byteCount;
        }
    }

    private enum Show {
        CONST, FN, REGISTER, NUMBER, ENDPOINT_FLAGS, SUBSTRING, REG_PROPERTY, REG_INDEX, JUMP_OFFSET
    }

    // Instruction definition class
    private record InstructionDef(String name, OperandType operandType, Show show) {
        InstructionDef(String name, OperandType operandType) {
            this(name, operandType, null);
        }
    }

    // Result class for operand parsing
    private record OperandResult(int value, int nextPc, int secondValue, int thirdValue) {
        OperandResult(int value, int nextPc) {
            this(value, nextPc, -1, -1);
        }

        OperandResult(int value, int nextPc, int secondValue) {
            this(value, nextPc, secondValue, -1);
        }
    }

    private final Bytecode bytecode;

    BytecodeDisassembler(Bytecode bytecode) {
        this.bytecode = bytecode;
    }

    String disassemble() {
        StringBuilder s = new StringBuilder();

        s.append("=== Bytecode Program ===\n");
        s.append("Conditions: ").append(bytecode.getConditionCount()).append("\n");
        s.append("Results: ").append(bytecode.getResultCount()).append("\n");
        s.append("Registers: ").append(bytecode.getRegisterDefinitions().length).append("\n");
        s.append("Functions: ").append(bytecode.getFunctions().length).append("\n");
        s.append("Constants: ").append(bytecode.getConstantPoolCount()).append("\n");
        s.append("BDD Nodes: ").append(bytecode.getBddNodes().length / 3).append("\n");
        s.append("BDD Root: ").append(BddFormatter.formatReference(bytecode.getBddRootRef())).append("\n");

        Map<String, Integer> instructionCounts = countInstructions();
        if (!instructionCounts.isEmpty()) {
            s.append("\nInstruction counts: ");
            instructionCounts.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10) // Top 10 most common
                    .forEach(e -> s.append(e.getKey()).append("(").append(e.getValue()).append(") "));
            s.append("\n");
        }
        s.append("\n");

        // Functions
        if (bytecode.getFunctions().length > 0) {
            s.append("=== Functions ===\n");
            int i = 0;
            for (var fn : bytecode.getFunctions()) {
                s.append(String.format("  %2d: %-20s [%d args]%n",
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
                if (r.required())
                    s.append(" [required]");
                if (r.temp())
                    s.append(" [temp]");
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

            try {
                StringWriter sw = new StringWriter();
                BddFormatter formatter = new BddFormatter(bytecode.getBdd(), sw, "");
                formatter.format();
                s.append(sw);
            } catch (Exception e) {
                // Fallback if formatting fails
                s.append("Error formatting BDD nodes: ").append(e.getMessage()).append("\n");
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
                s.append(String.format("Condition %d:%n", i));
                int startOffset = bytecode.getConditionStartOffset(i);
                disassembleSection(s, startOffset, Integer.MAX_VALUE, "  ");
                s.append("\n");
            }
        }

        // Results
        if (bytecode.getResultCount() > 0) {
            s.append("=== Results ===\n");
            for (int i = 0; i < bytecode.getResultCount(); i++) {
                s.append(String.format("Result %d:%n", i));
                int startOffset = bytecode.getResultOffset(i);
                disassembleSection(s, startOffset, Integer.MAX_VALUE, "  ");
                s.append("\n");
            }
        }

        return s.toString();
    }

    private Map<String, Integer> countInstructions() {
        Map<String, Integer> counts = new HashMap<>();
        byte[] instructions = bytecode.getBytecode();

        int pc = 0;
        while (pc < instructions.length) {
            byte opcode = instructions[pc];
            InstructionDef def = INSTRUCTION_DEFS.get(opcode);
            if (def == null) {
                break; // Unknown instruction
            }

            counts.merge(def.name(), 1, Integer::sum);

            // Skip operands
            pc += 1 + def.operandType().byteCount;
        }

        return counts;
    }

    private void disassembleSection(StringBuilder s, int startOffset, int endOffset, String indent) {
        byte[] instructions = bytecode.getBytecode();

        if (startOffset >= instructions.length) {
            s.append(indent).append("(section starts beyond bytecode end)\n");
            return;
        }

        for (int pc = startOffset; pc < endOffset && pc < instructions.length;) {
            // Check if this is a return opcode
            byte opcode = instructions[pc];

            int nextPc = writeInstruction(s, pc, indent);
            if (nextPc < 0) {
                break;
            }

            pc = nextPc;

            // Stop after return instructions
            if (opcode == Opcodes.RETURN_VALUE || opcode == Opcodes.RETURN_ENDPOINT || opcode == Opcodes.RETURN_ERROR) {
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
            s.append(String.format("UNKNOWN_OPCODE(0x%02X)%n", opcode));
            return -1;
        }

        s.append(String.format("%-22s", def.name()));

        // Parse operands based on type
        OperandResult operandResult = parseOperands(s, pc, def.operandType(), instructions);
        int nextPc = operandResult.nextPc();
        int displayValue = operandResult.value();
        int secondValue = operandResult.secondValue();
        int thirdValue = operandResult.thirdValue();

        // Add symbolic information if available
        if (def.show() != null) {
            s.append("  ; ");
            appendSymbolicInfo(s, pc, displayValue, secondValue, thirdValue, def.show, instructions);
        }

        s.append("\n");
        return nextPc;
    }

    private OperandResult parseOperands(StringBuilder s, int pc, OperandType type, byte[] instructions) {
        return switch (type) {
            case NONE -> new OperandResult(-1, pc + 1);
            case BYTE -> {
                int value = appendByte(s, pc, instructions);
                yield new OperandResult(value, pc + 2);
            }
            case SHORT, TWO_BYTES -> {
                int value = appendShort(s, pc, instructions);
                yield new OperandResult(value, pc + 3);
            }
            case BYTE_SHORT -> {
                s.append(" ");
                int b1 = appendByte(s, pc, instructions);
                int b2 = appendShort(s, pc + 1, instructions);
                yield new OperandResult(b1, pc + 4, b2);
            }
            case THREE_BYTES -> {
                s.append(" ");
                int b1 = appendByte(s, pc, instructions);
                int b2 = appendByte(s, pc + 1, instructions);
                int b3 = appendByte(s, pc + 2, instructions);
                yield new OperandResult(b1, pc + 4, b2, b3);
            }
        };
    }

    private void appendSymbolicInfo(
            StringBuilder s,
            int pc,
            int value,
            int secondValue,
            int thirdValue,
            Show show,
            byte[] instructions
    ) {
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
                s.append("start=")
                        .append(value)
                        .append(", end=")
                        .append(secondValue)
                        .append(", reverse=")
                        .append(thirdValue != 0);
            }
            case REG_PROPERTY -> {
                if (value >= 0 && value < bytecode.getRegisterDefinitions().length) {
                    s.append(bytecode.getRegisterDefinitions()[value].name());
                    s.append(".");
                    if (secondValue >= 0 && secondValue < bytecode.getConstantPoolCount()) {
                        Object prop = bytecode.getConstant(secondValue);
                        if (prop instanceof String) {
                            s.append(prop);
                        }
                    }
                }
            }
            case REG_INDEX -> {
                if (value >= 0 && value < bytecode.getRegisterDefinitions().length) {
                    s.append(bytecode.getRegisterDefinitions()[value].name());
                    s.append("[").append(secondValue).append("]");
                }
            }
            case JUMP_OFFSET -> {
                s.append("-> ").append(pc + 3 + value); // pc + 3 because offset is relative to after instruction
            }
        }
    }

    private int appendByte(StringBuilder s, int pc, byte[] instructions) {
        if (instructions.length <= pc + 1) {
            s.append("?? (out of bounds at ").append(pc + 1).append(")");
            return -1;
        } else {
            int result = instructions[pc + 1] & 0xFF;
            s.append(String.format("%3d", result));
            return result;
        }
    }

    private int appendShort(StringBuilder s, int pc, byte[] instructions) {
        if (instructions.length <= pc + 2) {
            s.append("?? (out of bounds at ").append(pc + 1).append(")");
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
        } else if (value instanceof List<?> list) {
            return "List[" + list.size() + " items]";
        } else if (value instanceof Map<?, ?> map) {
            return "Map[" + map.size() + " entries]";
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
