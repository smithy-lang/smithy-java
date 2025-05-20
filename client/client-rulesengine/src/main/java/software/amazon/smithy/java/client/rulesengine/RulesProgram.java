/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A compiled and ready to run rules engine program.
 *
 * <p>A RulesProgram can be run any number of times and is thread-safe. A program can be serialized and later restored
 * using {@code ToString}. A RulesProgram is created using a {@link RulesEngine}.
 */
public final class RulesProgram {
    /**
     * The version that a rules engine program was compiled with. The version of a program must be less than or equal
     * to this version number. That is, older code can be run, but newer code cannot. The version is only incremented
     * when things like new opcodes are added. This is a single byte that appears as the first byte in the rules
     * engine bytecode. The version is a negative number to prevent accidentally treating another opcode as the version.
     */
    public static final byte VERSION = -1;

    /**
     * Push a value onto the stack. Must be followed by one unsigned byte representing the constant pool index.
     */
    static final byte LOAD_CONST = 0;

    /**
     * Push a value onto the stack. Must be followed by two bytes representing the (short) constant pool index.
     */
    static final byte LOAD_CONST_W = 1;

    /**
     * Peeks the value at the top of the stack and pushes it onto the register stack of a register. Must be followed
     * by the one byte register index.
     */
    static final byte SET_REGISTER = 2;

    /**
     * Get the value of a register and push it onto the stack. Must be followed by the one byte register index.
     */
    static final byte LOAD_REGISTER = 3;

    /**
     * Jumps to an opcode index if the top of the stack is null or false. Must be followed by two bytes representing
     * a short index position of the bytecode address.
     */
    static final byte JUMP_IF_FALSEY = 4;

    /**
     * Pops a value off the stack and pushes true if it is falsey (null or false), or false if not.
     *
     * <p>This implements the "not" function as an opcode.
     */
    static final byte NOT = 5;

    /**
     * Pops a value off the stack and pushes true if it is set (that is, not null).
     *
     * <p>This implements the "isset" function as an opcode.
     */
    static final byte ISSET = 6;

    /**
     * Checks if a register is set to a non-null value.
     *
     * <p>Must be followed by an unsigned byte that represents the register to check.
     */
    static final byte TEST_REGISTER_ISSET = 7;

    /**
     * Checks if a register is not set or set to a null value.
     *
     * <p>Must be followed by an unsigned byte that represents the register to check.
     */
    static final byte TEST_REGISTER_NOT_SET = 8;

    /**
     * Sets an error on the VM and exits.
     *
     * <p>Pops a single value that provides the error string to set.
     */
    static final byte RETURN_ERROR = 9;

    /**
     * Sets the endpoint result of the VM and exits. Pops the top of the stack, expecting a string value. The opcode
     * must be followed by a byte where the first bit of the byte is on if the endpoint has headers, and the second
     * bit is on if the endpoint has properties.
     */
    static final byte RETURN_ENDPOINT = 10;

    /**
     * Pops N values off the stack and pushes a list of those values onto the stack. Must be followed by an unsigned
     * byte that defines the number of elements in the list.
     */
    static final byte CREATE_LIST = 11;

    /**
     * Pops N*2 values off the stack (key then value), creates a map of those values, and pushes the map onto the
     * stack. Each popped key must be a string. Must be followed by an unsigned byte that defines the
     * number of entries in the map.
     */
    static final byte CREATE_MAP = 12;

    /**
     * Resolves a template string. Must be followed by two bytes, a short, that represents the constant pool index
     * that stores the StringTemplate.
     *
     * <p>The corresponding instruction has a StringTemplate that tells the VM how many values to pop off the stack.
     * The popped values fill in values into the template. The resolved template value as a string is then pushed onto
     * the stack.
     */
    static final byte RESOLVE_TEMPLATE = 13;

    /**
     * Calls a function. Must be followed by a byte to provide the function index to call.
     *
     * <p>The function pops zero or more values off the stack based on the RulesFunction registered for the index,
     * and then pushes the Object result onto the stack.
     */
    static final byte FN = 14;

    /**
     * Pops the top level value and applies a getAttr expression on it, pushing the result onto the stack.
     *
     * <p>Must be followed by two bytes, a short, that represents the constant pool index that stores the
     * AttrExpression.
     */
    static final byte GET_ATTR = 15;

    /**
     * Pops a value and pushes true if the value is boolean true, false if not.
     */
    static final byte IS_TRUE = 16;

    /**
     * Checks if a register is boolean true and pushes the result onto the stack.
     *
     * <p>Must be followed by a byte that represents the register to check.
     */
    static final byte TEST_REGISTER_IS_TRUE = 17;

    /**
     * Checks if a register is boolean false and pushes the result onto the stack.
     *
     * <p>Must be followed by a byte that represents the register to check.
     */
    static final byte TEST_REGISTER_IS_FALSE = 18;

    /**
     * Pops the value at the top of the stack and returns it from the VM. This can be used for testing purposes or
     * for returning things other than endpoint values.
     */
    static final byte RETURN_VALUE = 19;

    /**
     * Pops the top two values off the stack and performs Objects.equals on them, pushing the result onto the stack.
     */
    static final byte EQUALS = 20;

    /**
     * Pops the top value off the stack, expecting a string, and extracts a substring of it, pushing the result onto
     * the stack.
     *
     * <p>Must be followed by three bytes: the start position in the string, the end position in the string, and
     * a byte set to 1 if the substring is "reversed" (from the end) or not.
     */
    static final byte SUBSTRING = 21;

    final List<RulesExtension> extensions;
    final Object[] constantPool;
    final byte[] instructions;
    final int instructionOffset;
    final int instructionSize;
    final ParamDefinition[] registerDefinitions;
    final RulesFunction[] functions;
    private final BiFunction<String, Context, Object> builtinProvider;
    private final int paramCount; // number of provided params.

    RulesProgram(
            List<RulesExtension> extensions,
            byte[] instructions,
            int instructionOffset,
            int instructionSize,
            List<ParamDefinition> params,
            RulesFunction[] functions,
            BiFunction<String, Context, Object> builtinProvider,
            Object[] constantPool
    ) {
        this.extensions = extensions;
        this.instructions = instructions;
        this.instructionOffset = instructionOffset;
        this.instructionSize = instructionSize;
        this.functions = functions;
        this.builtinProvider = builtinProvider;
        this.constantPool = constantPool;

        if (instructionSize < 3) {
            throw new IllegalArgumentException("Invalid rules engine bytecode: too short");
        }

        var versionByte = instructions[instructionOffset];
        if (versionByte >= 0) {
            throw new IllegalArgumentException("Invalid rules engine bytecode: missing version byte.");
        }

        if (versionByte < VERSION) {
            throw new IllegalArgumentException(String.format(
                    "Invalid rules engine bytecode: unsupported bytecode version %d. Up to version %d is supported."
                            + "Perhaps you need to update the client-rulesengine package.",
                    -versionByte,
                    -VERSION));
        }

        paramCount = instructions[instructionOffset + 1] & 0xFF;
        var syntheticParamCount = instructions[instructionOffset + 2] & 0xFF;
        var totalParams = paramCount + syntheticParamCount;
        registerDefinitions = new ParamDefinition[totalParams];

        if (params.size() == paramCount) {
            // Given just params and not registers too.
            params.toArray(registerDefinitions);
            for (var i = 0; i < syntheticParamCount; i++) {
                registerDefinitions[paramCount + i] = new ParamDefinition("r" + i);
            }
        } else if (params.size() == totalParams) {
            // Given exactly the required number of parameters. Assume it was given the params and registers.
            params.toArray(registerDefinitions);
        } else {
            throw new IllegalArgumentException("Invalid rules engine bytecode: bytecode requires " + paramCount
                    + " parameters, but provided " + params.size());
        }
    }

    /**
     * Runs the rules engine program and resolves an endpoint.
     *
     * @param context Context used during evaluation.
     * @param parameters Rules engine parameters.
     * @return the resolved Endpoint.
     * @throws RulesEvaluationError if the program fails during evaluation.
     */
    public Endpoint resolveEndpoint(Context context, Map<String, Object> parameters) {
        return run(context, parameters);
    }

    /**
     * Runs the rules engine program.
     *
     * @param context Context used during evaluation.
     * @param parameters Rules engine parameters.
     * @return the rules engine result.
     * @throws RulesEvaluationError if the program fails during evaluation.
     */
    public <T> T run(Context context, Map<String, Object> parameters) {
        for (var e : parameters.entrySet()) {
            EndpointUtils.verifyObject(e.getValue());
        }
        var vm = new RulesVm(context, this, parameters, builtinProvider);
        return vm.evaluate();
    }

    /**
     * Get the program's content pool.
     *
     * @return the constant pool. Do not modify.
     */
    @SmithyUnstableApi
    public Object[] getConstantPool() {
        return constantPool;
    }

    /**
     * Get the program's parameters.
     *
     * @return the parameters.
     */
    @SmithyUnstableApi
    public List<ParamDefinition> getParamDefinitions() {
        List<ParamDefinition> result = new ArrayList<>();
        for (var i = 0; i < paramCount; i++) {
            result.add(registerDefinitions[i]);
        }
        return result;
    }

    private enum Show {
        CONST, FN, REGISTER
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        // Write the registry values in index order.
        if (registerDefinitions.length > 0) {
            s.append("Registers:\n");
            int i = 0;
            for (var r : registerDefinitions) {
                s.append("  ").append(i++).append(": ");
                s.append(r);
                s.append("\n");
            }
            s.append("\n");
        }

        if (constantPool.length > 0) {
            s.append("Constants:\n");
            var i = 0;
            for (var c : constantPool) {
                s.append("  ").append(i++).append(": ");
                if (c instanceof StringTemplate) {
                    s.append("Template");
                } else if (c instanceof AttrExpression) {
                    s.append("AttrExpression");
                } else {
                    s.append(c.getClass().getSimpleName());
                }
                s.append(": ").append(c).append("\n");
            }
            s.append("\n");
        }

        // Write the required function names, in index order.
        if (functions.length > 0) {
            var i = 0;
            s.append("Functions:\n");
            for (var f : functions) {
                s.append("  ").append(i++).append(": ").append(f.getFunctionName()).append("\n");
            }
            s.append("\n");
        }

        // Write the instructions.
        s.append("Instructions: (version=").append(-instructions[instructionOffset]).append(")\n");

        // Skip version, param count, synthetic param count bytes.
        for (var i = instructionOffset + 3; i < instructionSize; i++) {
            i = writeInstruction(s, i);
        }

        return s.toString();
    }

    /**
     * Allows dumping out a specific instruction at the given bytecode position.
     *
     * @param pc Bytecode instruction position.
     * @return the instruction as a debug string.
     */
    public String writeInstruction(int pc) {
        StringBuilder s = new StringBuilder();
        writeInstruction(s, pc);
        return s.toString();
    }

    private int writeInstruction(StringBuilder s, int pc) {
        s.append("  ");
        s.append(String.format("%03d", pc));
        s.append(": ");

        var skip = 0;
        Show show = null;
        var name = switch (instructions[pc]) {
            case LOAD_CONST -> {
                skip = 1;
                show = Show.CONST;
                yield "LOAD_CONST";
            }
            case LOAD_CONST_W -> {
                skip = 2;
                show = Show.CONST;
                yield "LOAD_CONST_W";
            }
            case SET_REGISTER -> {
                skip = 1;
                show = Show.REGISTER;
                yield "SET_REGISTER";
            }
            case LOAD_REGISTER -> {
                skip = 1;
                show = Show.REGISTER;
                yield "LOAD_REGISTER";
            }
            case JUMP_IF_FALSEY -> {
                skip = 2;
                yield "JUMP_IF_FALSEY";
            }
            case NOT -> "NOT";
            case ISSET -> "ISSET";
            case TEST_REGISTER_ISSET -> {
                skip = 1;
                show = Show.REGISTER;
                yield "TEST_REGISTER_ISSET";
            }
            case TEST_REGISTER_NOT_SET -> {
                skip = 1;
                show = Show.REGISTER;
                yield "TEST_REGISTER_NOT_SET";
            }
            case RETURN_ERROR -> "RETURN_ERROR";
            case RETURN_ENDPOINT -> {
                skip = 1;
                yield "RETURN_ENDPOINT";
            }
            case CREATE_LIST -> {
                skip = 1;
                yield "CREATE_LIST";
            }
            case CREATE_MAP -> {
                skip = 1;
                yield "CREATE_MAP";
            }
            case RESOLVE_TEMPLATE -> {
                skip = 2;
                show = Show.CONST;
                yield "RESOLVE_TEMPLATE";
            }
            case FN -> {
                skip = 1;
                show = Show.FN;
                yield "FN";
            }
            case GET_ATTR -> {
                skip = 2;
                show = Show.CONST;
                yield "GET_ATTR";
            }
            case IS_TRUE -> "IS_TRUE";
            case TEST_REGISTER_IS_TRUE -> {
                skip = 1;
                show = Show.REGISTER;
                yield "TEST_REGISTER_IS_TRUE";
            }
            case TEST_REGISTER_IS_FALSE -> {
                skip = 1;
                show = Show.REGISTER;
                yield "TEST_REGISTER_IS_FALSE";
            }
            case RETURN_VALUE -> "RETURN_VALUE";
            case EQUALS -> "EQUALS";
            case SUBSTRING -> {
                skip = 3;
                yield "SUBSTRING";
            }
            default -> "?" + instructions[pc];
        };

        appendName(s, name);

        int positionToShow = -1;
        if (skip == 1) {
            positionToShow = appendByte(s, pc);
            pc++;
        } else if (skip == 2) {
            positionToShow = appendShort(s, pc);
            pc += 2;
        } else if (skip == 3) {
            appendByte(s, pc);
            appendByte(s, pc + 1);
            appendByte(s, pc + 2);
            pc += 3;
        }

        if (positionToShow > -1 && show != null) {
            switch (show) {
                case CONST -> {
                    s.append("  ");
                    s.append(constantPool[positionToShow]);
                }
                case FN -> {
                    s.append("  ");
                    s.append(functions[positionToShow].getFunctionName());
                }
                case REGISTER -> {
                    s.append("  ");
                    s.append(registerDefinitions[positionToShow].name());
                }
            }
        }

        s.append("\n");
        return pc;
    }

    private void appendName(StringBuilder s, String name) {
        s.append(String.format("%-22s  ", name));
    }

    private int appendByte(StringBuilder s, int i) {
        int result = -1;
        if (instructions.length > i + 1) {
            result = instructions[i + 1] & 0xFF;
            s.append(String.format("%-8d  ", result));
        } else {
            s.append("      ??");
        }
        return result;
    }

    private int appendShort(StringBuilder s, int i) {
        var result = -1;
        // it's a two-byte unsigned short.
        if (instructions.length > i + 2) {
            result = EndpointUtils.bytesToShort(instructions, i + 1);
            s.append(String.format("%-8d  ", result));
        } else {
            s.append("      ??");
        }
        return result;
    }
}
