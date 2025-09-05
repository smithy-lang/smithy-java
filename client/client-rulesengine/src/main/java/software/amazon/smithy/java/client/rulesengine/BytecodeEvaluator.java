/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsValidHostLabel;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.ParseUrl;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Split;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Substring;
import software.amazon.smithy.rulesengine.logic.ConditionEvaluator;

/**
 * Evaluates bytecode for a single specific condition or result per evaluation.
 */
final class BytecodeEvaluator implements ConditionEvaluator {

    final Map<String, Object> paramsCache = new HashMap<>();

    private final Bytecode bytecode;
    private final Object[] registers;
    private final RulesExtension[] extensions;
    private Object[] tempArray = new Object[8];
    private int tempArraySize = 8;
    private Object[] stack = new Object[16];
    private int stackPosition = 0;
    private int pc;
    private final StringBuilder stringBuilder = new StringBuilder(64);
    private final UriFactory uriFactory = new UriFactory();
    private final RegisterFiller registerFiller;
    private Context context;

    BytecodeEvaluator(Bytecode bytecode, RulesExtension[] extensions, RegisterFiller registerFiller) {
        this.bytecode = bytecode;
        this.extensions = extensions;
        this.registers = new Object[bytecode.getRegisterDefinitions().length];
        this.registerFiller = registerFiller;
    }

    /**
     * Reset the evaluator and registers of the evaluator so it can be reused, using the given context and
     * input parameters.
     *
     * @param context Context to get context from.
     * @param parameters Parameters to get input from.
     */
    void reset(Context context, Map<String, Object> parameters) {
        this.context = context;
        this.stackPosition = 0;
        registerFiller.fillRegisters(registers, context, parameters);
    }

    @Override
    public boolean test(int conditionIndex) {
        // Reset stack position for fresh evaluation
        stackPosition = 0;
        Object result = run(bytecode.getConditionStartOffset(conditionIndex));
        return result != null && result != Boolean.FALSE;
    }

    public Endpoint resolveResult(int resultIndex) {
        if (resultIndex <= -1) {
            return null;
        } else {
            stackPosition = 0;
            return (Endpoint) run(bytecode.getResultOffset(resultIndex));
        }
    }

    private void push(Object value) {
        if (stackPosition == stack.length) {
            resizeStack();
        }
        stack[stackPosition++] = value;
    }

    private void resizeStack() {
        Object[] newStack = new Object[stack.length + (stack.length >> 1)];
        System.arraycopy(stack, 0, newStack, 0, stack.length);
        stack = newStack;
    }

    private Object[] getTempArray(int requiredSize) {
        return tempArraySize >= requiredSize ? tempArray : resizeTempArray(requiredSize);
    }

    private Object[] resizeTempArray(int requiredSize) {
        // Resize and round up to next power of two
        int newSize = Integer.highestOneBit(requiredSize - 1) << 1;
        tempArray = new Object[newSize];
        tempArraySize = newSize;
        return tempArray;
    }

    @SuppressWarnings("unchecked")
    private Object run(int start) {
        pc = start;

        var instructions = bytecode.getBytecode();
        var functions = bytecode.getFunctions();
        var constantPool = bytecode.getConstantPool();

        while (pc < instructions.length) {
            int opcode = instructions[pc++] & 0xFF;
            switch (opcode) {
                case Opcodes.LOAD_CONST -> {
                    push(constantPool[instructions[pc++] & 0xFF]);
                }
                case Opcodes.LOAD_CONST_W -> {
                    int constIdx = ((instructions[pc] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    push(constantPool[constIdx]);
                    pc += 2;
                }
                case Opcodes.SET_REGISTER -> {
                    int index = instructions[pc++] & 0xFF;
                    registers[index] = stack[stackPosition - 1];
                }
                case Opcodes.LOAD_REGISTER -> {
                    int index = instructions[pc++] & 0xFF;
                    push(registers[index]);
                }
                case Opcodes.NOT -> {
                    // In-place operation
                    int idx = stackPosition - 1;
                    stack[idx] = stack[idx] == Boolean.FALSE ? Boolean.TRUE : Boolean.FALSE;
                }
                case Opcodes.ISSET -> {
                    // In-place operation
                    int idx = stackPosition - 1;
                    stack[idx] = stack[idx] != null ? Boolean.TRUE : Boolean.FALSE;
                }
                case Opcodes.TEST_REGISTER_ISSET -> {
                    push(registers[instructions[pc++] & 0xFF] != null ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.TEST_REGISTER_NOT_SET -> {
                    push(registers[instructions[pc++] & 0xFF] == null ? Boolean.TRUE : Boolean.FALSE);
                }
                // List operations
                case Opcodes.LIST0 -> push(Collections.emptyList());
                case Opcodes.LIST1 -> {
                    // Pops 1, pushes 1: reuse position
                    int idx = stackPosition - 1;
                    stack[idx] = List.of(stack[idx]);
                }
                case Opcodes.LIST2 -> {
                    // Pops 2, pushes 1
                    int idx = stackPosition - 2;
                    stack[idx] = List.of(stack[idx], stack[idx + 1]);
                    stackPosition = idx + 1;
                }
                case Opcodes.LISTN -> {
                    var size = instructions[pc++] & 0xFF;
                    var values = new Object[size];
                    for (var i = size - 1; i >= 0; i--) {
                        values[i] = stack[--stackPosition];
                    }
                    push(Arrays.asList(values)); // dynamic size
                }
                // Map operations
                case Opcodes.MAP0 -> push(Map.of());
                case Opcodes.MAP1 -> {
                    // Pops 2, pushes 1
                    int idx = stackPosition - 2;
                    stack[idx] = Map.of((String) stack[idx + 1], stack[idx]);
                    stackPosition = idx + 1;
                }
                case Opcodes.MAP2 -> {
                    // Pops 4, pushes 1
                    int idx = stackPosition - 4;
                    stack[idx] = Map.of(
                            (String) stack[idx + 1], // key
                            stack[idx], // value
                            (String) stack[idx + 3], // key
                            stack[idx + 2]); // value
                    stackPosition = idx + 1;
                }
                case Opcodes.MAP3 -> {
                    // Pops 6, pushes 1
                    int idx = stackPosition - 6;
                    stack[idx] = Map.of(
                            (String) stack[idx + 2], // key
                            stack[idx + 1], // value
                            (String) stack[idx + 4], // key
                            stack[idx + 3], // value
                            (String) stack[idx + 5], // key
                            stack[idx]);
                    stackPosition = idx + 1;
                }
                case Opcodes.MAP4 -> {
                    // Pops 8, pushes 1
                    int idx = stackPosition - 8;
                    stack[idx] = Map.of(
                            (String) stack[idx + 1], // key
                            stack[idx], // value
                            (String) stack[idx + 3], // key
                            stack[idx + 2], // value
                            (String) stack[idx + 5], // key
                            stack[idx + 4], // value
                            (String) stack[idx + 7], // key
                            stack[idx + 6]); // value
                    stackPosition = idx + 1;
                }
                case Opcodes.MAPN -> {
                    var size = instructions[pc++] & 0xFF;
                    Map<String, Object> map = new HashMap<>(size + 1, 1.0f);
                    for (var i = 0; i < size; i++) {
                        map.put((String) stack[--stackPosition], stack[--stackPosition]);
                    }
                    push(map); // dynamic size
                }
                case Opcodes.RESOLVE_TEMPLATE -> {
                    int argCount = instructions[pc++] & 0xFF;
                    stringBuilder.setLength(0);
                    // Calculate where the first argument is on the stack
                    int firstArgPosition = stackPosition - argCount;
                    for (int i = 0; i < argCount; i++) {
                        stringBuilder.append(stack[firstArgPosition + i]);
                    }
                    // Result goes where first arg was
                    stack[firstArgPosition] = stringBuilder.toString();
                    stackPosition = firstArgPosition + 1;
                }
                case Opcodes.FN0 -> {
                    var fn = functions[instructions[pc++] & 0xFF];
                    push(fn.apply0());
                }
                case Opcodes.FN1 -> {
                    // Pops 1, pushes 1 - reuse position
                    var fn = functions[instructions[pc++] & 0xFF];
                    int idx = stackPosition - 1;
                    stack[idx] = fn.apply1(stack[idx]);
                }
                case Opcodes.FN2 -> {
                    // Pops 2, pushes 1
                    var fn = functions[instructions[pc++] & 0xFF];
                    int idx = stackPosition - 2;
                    stack[idx] = fn.apply2(stack[idx], stack[idx + 1]);
                    stackPosition = idx + 1;
                }
                case Opcodes.FN3 -> {
                    // Pops 3, pushes 1
                    var fn = functions[instructions[pc++] & 0xFF];
                    int idx = stackPosition - 3;
                    stack[idx] = fn.apply(stack[idx], stack[idx + 1], stack[idx + 2]);
                    stackPosition = idx + 1;
                }
                case Opcodes.FN -> {
                    var fn = functions[instructions[pc++] & 0xFF];
                    var temp = getTempArray(fn.getArgumentCount());
                    for (int i = fn.getArgumentCount() - 1; i >= 0; i--) {
                        temp[i] = stack[--stackPosition];
                    }
                    push(fn.apply(temp)); // dynamic size
                }
                case Opcodes.GET_PROPERTY -> {
                    int propertyIdx = ((instructions[pc] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    var propertyName = (String) constantPool[propertyIdx];
                    int idx = stackPosition - 1;
                    stack[idx] = getProperty(stack[idx], propertyName);
                    pc += 2;
                }
                case Opcodes.GET_INDEX -> {
                    int index = instructions[pc++] & 0xFF;
                    int idx = stackPosition - 1;
                    stack[idx] = getIndex(stack[idx], index);
                }
                case Opcodes.GET_PROPERTY_REG -> {
                    int regIndex = instructions[pc++] & 0xFF;
                    int propertyIdx = ((instructions[pc] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    var propertyName = (String) constantPool[propertyIdx];
                    var target = registers[regIndex];
                    push(getProperty(target, propertyName));
                    pc += 2;
                }
                case Opcodes.GET_INDEX_REG -> {
                    int regIndex = instructions[pc++] & 0xFF;
                    int index = instructions[pc++] & 0xFF;
                    var target = registers[regIndex];
                    push(getIndex(target, index));
                }
                case Opcodes.IS_TRUE -> {
                    // In-place operation
                    int idx = stackPosition - 1;
                    stack[idx] = stack[idx] == Boolean.TRUE ? Boolean.TRUE : Boolean.FALSE;
                }
                case Opcodes.TEST_REGISTER_IS_TRUE -> {
                    push(registers[instructions[pc++] & 0xFF] == Boolean.TRUE ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.TEST_REGISTER_IS_FALSE -> {
                    push(registers[instructions[pc++] & 0xFF] == Boolean.FALSE ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.EQUALS -> {
                    // Pops 2, pushes 1
                    int idx = stackPosition - 2;
                    stack[idx] = Objects.equals(stack[idx], stack[idx + 1]) ? Boolean.TRUE : Boolean.FALSE;
                    stackPosition = idx + 1;
                }
                case Opcodes.STRING_EQUALS -> {
                    // Pops 2, pushes 1
                    int idx = stackPosition - 2;
                    var a = (String) stack[idx];
                    var b = (String) stack[idx + 1];
                    stack[idx] = a != null && a.equals(b) ? Boolean.TRUE : Boolean.FALSE;
                    stackPosition = idx + 1;
                }
                case Opcodes.BOOLEAN_EQUALS -> {
                    // Pops 2, pushes 1
                    int idx = stackPosition - 2;
                    var a = (Boolean) stack[idx];
                    var b = (Boolean) stack[idx + 1];
                    stack[idx] = a != null && a.equals(b) ? Boolean.TRUE : Boolean.FALSE;
                    stackPosition = idx + 1;
                }
                case Opcodes.SUBSTRING -> {
                    int idx = stackPosition - 1;
                    var string = (String) stack[idx];
                    var startPos = instructions[pc++] & 0xFF;
                    var endPos = instructions[pc++] & 0xFF;
                    var reverse = (instructions[pc++] & 0xFF) != 0;
                    stack[idx] = Substring.getSubstring(string, startPos, endPos, reverse);
                }
                case Opcodes.IS_VALID_HOST_LABEL -> {
                    // Pops 2, pushes 1
                    int idx = stackPosition - 2;
                    var hostLabel = (String) stack[idx];
                    var allowDots = (Boolean) stack[idx + 1];
                    stack[idx] = IsValidHostLabel.isValidHostLabel(hostLabel, Boolean.TRUE.equals(allowDots))
                            ? Boolean.TRUE
                            : Boolean.FALSE;
                    stackPosition = idx + 1;
                }
                case Opcodes.PARSE_URL -> {
                    int idx = stackPosition - 1;
                    var urlString = (String) stack[idx];
                    stack[idx] = urlString == null ? null : uriFactory.createUri(urlString);
                }
                case Opcodes.URI_ENCODE -> {
                    int idx = stackPosition - 1;
                    var string = (String) stack[idx];
                    stack[idx] = URLEncoding.encodeUnreserved(string, false);
                }
                case Opcodes.RETURN_ERROR -> throw new RulesEvaluationError((String) stack[--stackPosition], pc);
                case Opcodes.RETURN_ENDPOINT -> {
                    var packed = instructions[pc++];
                    boolean hasHeaders = (packed & 1) != 0;
                    boolean hasProperties = (packed & 2) != 0;
                    var urlString = (String) stack[--stackPosition];
                    var properties = (Map<String, Object>) (hasProperties ? stack[--stackPosition] : Map.of());
                    var headers = (Map<String, List<String>>) (hasHeaders ? stack[--stackPosition] : Map.of());
                    var builder = Endpoint.builder().uri(uriFactory.createUri(urlString));
                    if (!headers.isEmpty()) {
                        builder.putProperty(EndpointContext.HEADERS, headers);
                    }
                    for (var extension : extensions) {
                        extension.extractEndpointProperties(builder, context, properties, headers);
                    }
                    return builder.build();
                }
                case Opcodes.RETURN_VALUE -> {
                    return stack[--stackPosition];
                }
                case Opcodes.JNN_OR_POP -> {
                    Object value = stack[stackPosition - 1];
                    // Read as unsigned 16-bit value (0-65535)
                    int offset = ((instructions[pc] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    pc += 2;
                    if (value != null) {
                        pc += offset; // Jump forward, keeping value on stack
                    } else {
                        stackPosition--; // Pop the null value
                    }
                }
                case Opcodes.SPLIT -> {
                    // Pops 3, pushes 1
                    int idx = stackPosition - 3;
                    var string = (String) stack[idx];
                    var delimiter = (String) stack[idx + 1];
                    var limit = ((Number) stack[idx + 2]).intValue();
                    stack[idx] = Split.split(string, delimiter, limit);
                    stackPosition = idx + 1;
                }
                default -> throw new RulesEvaluationError("Unknown rules engine instruction: " + opcode, pc);
            }
        }

        throw new IllegalArgumentException("Expected to return a value during evaluation");
    }

    // Get a property from a map or URI, or return null.
    private Object getProperty(Object target, String propertyName) {
        if (target instanceof Map<?, ?> m) {
            return m.get(propertyName);
        } else if (target instanceof URI u) {
            return switch (propertyName) {
                case "scheme" -> u.getScheme();
                case "path" -> u.getRawPath();
                case "normalizedPath" -> ParseUrl.normalizePath(u.getRawPath());
                case "authority" -> u.getAuthority();
                case "isIp" -> ParseUrl.isIpAddr(u.getHost());
                default -> null;
            };
        }
        return null;
    }

    // Get a value by index from an object. If not an array, returns null.
    private Object getIndex(Object target, int index) {
        if (target instanceof List<?> l) {
            if (index >= 0 && index < l.size()) {
                return l.get(index);
            }
        }
        return null;
    }
}
