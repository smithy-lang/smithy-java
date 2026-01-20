/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.rulesengine.logic.bdd.BddFormatter;

final class BytecodeDisassembler {

    private static final Map<Byte, InstructionDef> INSTRUCTION_DEFS = Map.ofEntries(
            // Basic stack operations
            Map.entry(Opcodes.LOAD_CONST, new InstructionDef("LOAD_CONST", Show.CONST)),
            Map.entry(Opcodes.LOAD_CONST_W, new InstructionDef("LOAD_CONST_W", Show.CONST)),
            Map.entry(Opcodes.SET_REGISTER, new InstructionDef("SET_REGISTER", Show.REGISTER)),
            Map.entry(Opcodes.LOAD_REGISTER, new InstructionDef("LOAD_REGISTER", Show.REGISTER)),

            // Boolean operations
            Map.entry(Opcodes.NOT, new InstructionDef("NOT")),
            Map.entry(Opcodes.ISSET, new InstructionDef("ISSET")),
            Map.entry(Opcodes.TEST_REGISTER_ISSET, new InstructionDef("TEST_REGISTER_ISSET", Show.REGISTER)),
            Map.entry(Opcodes.TEST_REGISTER_NOT_SET, new InstructionDef("TEST_REGISTER_NOT_SET", Show.REGISTER)),

            // List operations
            Map.entry(Opcodes.LIST0, new InstructionDef("LIST0")),
            Map.entry(Opcodes.LIST1, new InstructionDef("LIST1")),
            Map.entry(Opcodes.LIST2, new InstructionDef("LIST2")),
            Map.entry(Opcodes.LISTN, new InstructionDef("LISTN", Show.NUMBER)),

            // Map operations
            Map.entry(Opcodes.MAP0, new InstructionDef("MAP0")),
            Map.entry(Opcodes.MAP1, new InstructionDef("MAP1")),
            Map.entry(Opcodes.MAP2, new InstructionDef("MAP2")),
            Map.entry(Opcodes.MAP3, new InstructionDef("MAP3")),
            Map.entry(Opcodes.MAP4, new InstructionDef("MAP4")),
            Map.entry(Opcodes.MAPN, new InstructionDef("MAPN", Show.NUMBER)),

            // Template operation
            Map.entry(Opcodes.RESOLVE_TEMPLATE, new InstructionDef("RESOLVE_TEMPLATE", Show.ARG_COUNT)),

            // Function operations
            Map.entry(Opcodes.FN0, new InstructionDef("FN0", Show.FN)),
            Map.entry(Opcodes.FN1, new InstructionDef("FN1", Show.FN)),
            Map.entry(Opcodes.FN2, new InstructionDef("FN2", Show.FN)),
            Map.entry(Opcodes.FN3, new InstructionDef("FN3", Show.FN)),
            Map.entry(Opcodes.FN, new InstructionDef("FN", Show.FN)),

            // Property access operations
            Map.entry(Opcodes.GET_PROPERTY, new InstructionDef("GET_PROPERTY", Show.PROPERTY)),
            Map.entry(Opcodes.GET_INDEX, new InstructionDef("GET_INDEX", Show.NUMBER)),
            Map.entry(Opcodes.GET_PROPERTY_REG, new InstructionDef("GET_PROPERTY_REG", Show.REG_PROPERTY)),
            Map.entry(Opcodes.GET_INDEX_REG, new InstructionDef("GET_INDEX_REG", Show.REG_INDEX)),

            // Boolean test operations
            Map.entry(Opcodes.IS_TRUE, new InstructionDef("IS_TRUE")),
            Map.entry(Opcodes.TEST_REGISTER_IS_TRUE, new InstructionDef("TEST_REGISTER_IS_TRUE", Show.REGISTER)),
            Map.entry(Opcodes.TEST_REGISTER_IS_FALSE, new InstructionDef("TEST_REGISTER_IS_FALSE", Show.REGISTER)),

            // Comparison operations
            Map.entry(Opcodes.EQUALS, new InstructionDef("EQUALS")),
            Map.entry(Opcodes.STRING_EQUALS, new InstructionDef("STRING_EQUALS")),
            Map.entry(Opcodes.BOOLEAN_EQUALS, new InstructionDef("BOOLEAN_EQUALS")),

            // String operations
            Map.entry(Opcodes.SUBSTRING, new InstructionDef("SUBSTRING", Show.SUBSTRING)),
            Map.entry(Opcodes.IS_VALID_HOST_LABEL, new InstructionDef("IS_VALID_HOST_LABEL")),
            Map.entry(Opcodes.PARSE_URL, new InstructionDef("PARSE_URL")),
            Map.entry(Opcodes.URI_ENCODE, new InstructionDef("URI_ENCODE")),
            Map.entry(Opcodes.SPLIT, new InstructionDef("SPLIT")),

            // Return operations
            Map.entry(Opcodes.RETURN_ERROR, new InstructionDef("RETURN_ERROR")),
            Map.entry(Opcodes.RETURN_ENDPOINT, new InstructionDef("RETURN_ENDPOINT", Show.ENDPOINT_FLAGS)),
            Map.entry(Opcodes.RETURN_VALUE, new InstructionDef("RETURN_VALUE")),

            // Control flow
            Map.entry(Opcodes.JNN_OR_POP, new InstructionDef("JNN_OR_POP", Show.JUMP_OFFSET)),
            Map.entry(Opcodes.GET_NEGATIVE_INDEX, new InstructionDef("GET_NEGATIVE_INDEX", Show.NUMBER)),
            Map.entry(Opcodes.GET_NEGATIVE_INDEX_REG, new InstructionDef("GET_NEGATIVE_INDEX_REG", Show.REG_INDEX)),
            Map.entry(Opcodes.JMP_IF_FALSE, new InstructionDef("JMP_IF_FALSE", Show.JUMP_OFFSET)),
            Map.entry(Opcodes.JUMP, new InstructionDef("JUMP", Show.JUMP_OFFSET)));

    private enum Show {
        CONST,
        FN,
        REGISTER,
        NUMBER,
        ENDPOINT_FLAGS,
        SUBSTRING,
        REG_PROPERTY,
        REG_INDEX,
        JUMP_OFFSET,
        PROPERTY,
        ARG_COUNT
    }

    private record InstructionDef(String name, Show show) {
        InstructionDef(String name) {
            this(name, null);
        }
    }

    private final Bytecode bytecode;

    BytecodeDisassembler(Bytecode bytecode) {
        this.bytecode = bytecode;
    }

    String disassemble() {
        StringBuilder s = new StringBuilder();

        s.append("=== Bytecode Program ===\n");
        s.append("Version: ").append(bytecode.getVersion()).append("\n");
        s.append("Conditions: ").append(bytecode.getConditionCount()).append("\n");
        s.append("Results: ").append(bytecode.getResultCount()).append("\n");
        s.append("Registers: ").append(bytecode.getRegisterDefinitions().length).append("\n");
        s.append("Functions: ").append(bytecode.getFunctions().length).append("\n");
        s.append("Constants: ").append(bytecode.getConstantPoolCount()).append("\n");
        s.append("BDD Nodes: ").append(bytecode.getBddNodes().length / 3).append("\n");
        s.append("BDD Root: ").append(BddFormatter.formatReference(bytecode.getBddRootRef())).append("\n");

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

        if (bytecode.getBddNodes().length > 0) {
            s.append("=== BDD Structure ===\n");

            try {
                StringWriter sw = new StringWriter();
                BddFormatter formatter = new BddFormatter(bytecode.getBdd(), sw, "");
                formatter.format();
                s.append(sw);
            } catch (Exception e) {
                s.append("Error formatting BDD nodes: ").append(e.getMessage()).append("\n");
            }

            s.append("\n");
        }

        if (bytecode.getConstantPoolCount() > 0) {
            s.append("=== Constant Pool ===\n");
            for (int i = 0; i < bytecode.getConstantPoolCount(); i++) {
                s.append(String.format("  %3d: ", i));
                s.append(formatConstant(bytecode.getConstant(i))).append("\n");
            }
            s.append("\n");
        }

        if (bytecode.getConditionCount() > 0) {
            s.append("=== Conditions ===\n");
            for (int i = 0; i < bytecode.getConditionCount(); i++) {
                s.append(String.format("Condition %d:%n", i));
                int startOffset = bytecode.getConditionStartOffset(i);
                disassembleSection(s, startOffset, "  ");
                s.append("\n");
            }
        }

        if (bytecode.getResultCount() > 0) {
            s.append("=== Results ===\n");
            for (int i = 0; i < bytecode.getResultCount(); i++) {
                s.append(String.format("Result %d:%n", i));
                int startOffset = bytecode.getResultOffset(i);
                disassembleSection(s, startOffset, "  ");
                s.append("\n");
            }
        }

        return s.toString();
    }

    private void disassembleSection(StringBuilder s, int startOffset, String indent) {
        BytecodeWalker walker = new BytecodeWalker(bytecode.getBytecode(), startOffset);

        if (!walker.hasNext()) {
            s.append(indent).append("(section starts beyond bytecode end)\n");
            return;
        }

        while (walker.hasNext()) {
            writeInstruction(s, walker, indent);
            if (walker.isReturnOpcode()) {
                break;
            }
            if (!walker.advance()) {
                break;
            }
        }
    }

    private void writeInstruction(StringBuilder s, BytecodeWalker walker, String indent) {
        int pc = walker.getPosition();
        byte opcode = walker.currentOpcode();
        s.append(indent).append(String.format("%04d: ", pc));

        InstructionDef def = INSTRUCTION_DEFS.get(opcode);
        if (def == null) {
            s.append(String.format("UNKNOWN_OPCODE(0x%02X)%n", opcode));
            return;
        }

        s.append(String.format("%-22s", def.name()));

        // Format operands
        int operandCount = walker.getOperandCount();
        if (operandCount > 0) {
            s.append(" ");
            for (int i = 0; i < operandCount; i++) {
                if (i > 0) {
                    s.append(walker.getInstructionLength() == 4 ? " " : "");
                }
                int value = walker.getOperand(i);
                // Format based on operand width
                if (opcode == Opcodes.LOAD_CONST_W || opcode == Opcodes.GET_PROPERTY
                        ||
                        opcode == Opcodes.JNN_OR_POP
                        || (opcode == Opcodes.GET_PROPERTY_REG && i == 1)
                        ||
                        (opcode == Opcodes.RESOLVE_TEMPLATE && i == 1)) {
                    s.append(String.format("%5d", value));
                } else {
                    s.append(String.format("%3d", value));
                }
            }
        }

        // Add symbolic information
        if (def.show() != null) {
            s.append("  ; ");
            appendSymbolicInfo(s, walker, def.show());
        }

        s.append("\n");
    }

    private void appendSymbolicInfo(StringBuilder s, BytecodeWalker walker, Show show) {
        switch (show) {
            case CONST -> {
                int index = walker.getOperandCount() == 1 ? walker.getOperand(0) : -1;
                if (index >= 0 && index < bytecode.getConstantPoolCount()) {
                    s.append(formatConstant(bytecode.getConstant(index)));
                }
            }
            case FN -> {
                int index = walker.getOperand(0);
                if (index >= 0 && index < bytecode.getFunctions().length) {
                    var fn = bytecode.getFunctions()[index];
                    s.append(fn.getFunctionName()).append("(").append(fn.getArgumentCount()).append(" args)");
                }
            }
            case REGISTER -> {
                int index = walker.getOperand(0);
                if (index >= 0 && index < bytecode.getRegisterDefinitions().length) {
                    s.append(bytecode.getRegisterDefinitions()[index].name());
                }
            }
            case NUMBER -> s.append(walker.getOperand(0));
            case ARG_COUNT -> s.append("args=").append(walker.getOperand(0));
            case ENDPOINT_FLAGS -> {
                int flags = walker.getOperand(0);
                boolean hasHeaders = (flags & 1) != 0;
                boolean hasProperties = (flags & 2) != 0;
                s.append("headers=").append(hasHeaders).append(", properties=").append(hasProperties);
            }
            case SUBSTRING -> {
                s.append("start=")
                        .append(walker.getOperand(0))
                        .append(", end=")
                        .append(walker.getOperand(1))
                        .append(", reverse=")
                        .append(walker.getOperand(2) != 0);
            }
            case PROPERTY -> {
                int index = walker.getOperand(0);
                if (index >= 0 && index < bytecode.getConstantPoolCount()) {
                    Object prop = bytecode.getConstant(index);
                    if (prop instanceof String) {
                        s.append("\"").append(prop).append("\"");
                    }
                }
            }
            case REG_PROPERTY -> {
                int regIndex = walker.getOperand(0);
                int propIndex = walker.getOperand(1);
                if (regIndex >= 0 && regIndex < bytecode.getRegisterDefinitions().length) {
                    s.append(bytecode.getRegisterDefinitions()[regIndex].name());
                    s.append(".");
                    if (propIndex >= 0 && propIndex < bytecode.getConstantPoolCount()) {
                        Object prop = bytecode.getConstant(propIndex);
                        if (prop instanceof String) {
                            s.append(prop);
                        }
                    }
                }
            }
            case REG_INDEX -> {
                int regIndex = walker.getOperand(0);
                int index = walker.getOperand(1);
                if (regIndex >= 0 && regIndex < bytecode.getRegisterDefinitions().length) {
                    s.append(bytecode.getRegisterDefinitions()[regIndex].name());
                    s.append("[").append(index).append("]");
                }
            }
            case JUMP_OFFSET -> {
                s.append("-> ").append(walker.getJumpTarget());
            }
        }
    }

    private String formatConstant(Object value) {
        return switch (value) {
            case null -> "null";
            case String s -> "\"" + escapeString(s) + "\"";
            case List<?> list -> "List[" + list.size() + " items]";
            case Map<?, ?> map -> "Map[" + map.size() + " entries]";
            default -> value.getClass().getSimpleName() + "[" + value + "]";
        };
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
