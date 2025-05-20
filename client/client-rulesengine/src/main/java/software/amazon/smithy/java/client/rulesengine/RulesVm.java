/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Substring;

final class RulesVm {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(RulesVm.class);

    // Make number of URIs to cache in the thread-local cache.
    private static final int MAX_CACHE_SIZE = 32;

    // Caches up to 32 previously parsed URIs in a thread-local LRU cache.
    private static final ThreadLocal<Map<String, URI>> URI_LRU_CACHE = ThreadLocal.withInitial(() -> {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, URI> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    });

    // Minimum size for temp arrays when it's lazily allocated.
    private static final int MIN_TEMP_ARRAY_SIZE = 8;

    // Temp array used during evaluation.
    private Object[] tempArray = new Object[8];
    private int tempArraySize = 8;

    private final Context context;
    private final RulesProgram program;
    private final Object[] registers;
    private final BiFunction<String, Context, Object> builtinProvider;
    private final byte[] instructions;
    private Object[] stack = new Object[8];
    private int stackPosition = 0;
    private int pc;
    private final boolean debugLoggingEnabled = LOGGER.isDebugEnabled();

    RulesVm(
            Context context,
            RulesProgram program,
            Map<String, Object> parameters,
            BiFunction<String, Context, Object> builtinProvider
    ) {
        this.context = context;
        this.program = program;
        this.instructions = program.instructions;
        this.builtinProvider = builtinProvider;

        // Copy the registers to not continuously push to their stack.
        registers = new Object[program.registerDefinitions.length];
        for (var i = 0; i < program.registerDefinitions.length; i++) {
            var definition = program.registerDefinitions[i];
            var provided = parameters.get(definition.name());
            if (provided != null) {
                registers[i] = provided;
            } else {
                initializeRegister(context, i, definition);
            }
        }
    }

    @SuppressWarnings("unchecked")
    <T> T evaluate() {
        try {
            return (T) run();
        } catch (ClassCastException e) {
            throw createError("Unexpected value type encountered while evaluating rules engine", e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw createError("Malformed bytecode encountered while evaluating rules engine", e);
        } catch (NullPointerException e) {
            throw createError("Rules engine encountered an unexpected null value", e);
        }
    }

    private RulesEvaluationError createError(String message, RuntimeException e) {
        var report = message + ". Encountered at address " + pc + " of program";
        throw new RulesEvaluationError(report, e);
    }

    void initializeRegister(Context context, int index, ParamDefinition definition) {
        if (definition.defaultValue() != null) {
            registers[index] = definition.defaultValue();
            return;
        }

        if (definition.builtin() != null) {
            var builtinValue = builtinProvider.apply(definition.builtin(), context);
            if (builtinValue != null) {
                registers[index] = builtinValue;
                return;
            }
        }

        if (definition.required()) {
            throw new RulesEvaluationError("Required rules engine parameter missing: " + definition.name());
        }
    }

    private void push(Object value) {
        if (stackPosition == stack.length) {
            resizeStack();
        }
        stack[stackPosition++] = value;
    }

    private void resizeStack() {
        int newCapacity = stack.length + (stack.length >> 1);
        Object[] newStack = new Object[newCapacity];
        System.arraycopy(stack, 0, newStack, 0, stack.length);
        stack = newStack;
    }

    /*
     * Implementation notes:
     * 1. Read an unsigned short into an int from two bytes:
     *    ((instructions[pc + 2] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF)
     * 2. Read an unsigned byte into an int from a byte:
     *    X & 0xFF. For example instructions[++pc] & 0xFF.
     * 3. The program counter, pc, is often incremented using "++" while reading it.
     * 4. Avoid auto-boxing booleans and instead use `b ? Boolean.TRUE : Boolean.FALSE`. This eliminates the implicit
     *    call to Boolean.valueOf.
     */
    @SuppressWarnings("unchecked")
    private Object run() {
        final var instructionSize = program.instructionSize;
        final var constantPool = program.constantPool;
        final var instructions = this.instructions;
        final var registers = this.registers;

        // Skip version, params, and register bytes.
        for (pc = program.instructionOffset + 3; pc < instructionSize; pc++) {
            switch (instructions[pc]) {
                case RulesProgram.LOAD_CONST -> {
                    push(constantPool[instructions[++pc] & 0xFF]); // read unsigned byte
                }
                case RulesProgram.LOAD_CONST_W -> {
                    // Read a two-byte unsigned short.
                    final int constIdx = ((instructions[pc + 2] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    push(constantPool[constIdx]);
                    pc += 2;
                }
                case RulesProgram.SET_REGISTER -> {
                    registers[instructions[++pc] & 0xFF] = stack[stackPosition - 1];
                }
                case RulesProgram.LOAD_REGISTER -> {
                    push(registers[instructions[++pc] & 0xFF]); // read unsigned byte
                }
                case RulesProgram.JUMP_IF_FALSEY -> {
                    final Object value = stack[--stackPosition];
                    if (value == null || value == Boolean.FALSE) {
                        // Read a two-byte unsigned short.
                        final int jumpTarget = ((instructions[pc + 2] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                        pc = jumpTarget - 1;
                        if (debugLoggingEnabled) {
                            logDebugJump(jumpTarget);
                        }
                    } else {
                        pc += 2;
                    }
                }
                case RulesProgram.NOT -> {
                    push(stack[--stackPosition] == Boolean.FALSE ? Boolean.TRUE : Boolean.FALSE);
                }
                case RulesProgram.ISSET -> {
                    push(stack[--stackPosition] != null ? Boolean.TRUE : Boolean.FALSE);
                }
                case RulesProgram.TEST_REGISTER_ISSET -> {
                    push(registers[instructions[++pc] & 0xFF] != null ? Boolean.TRUE : Boolean.FALSE);
                }
                case RulesProgram.RETURN_ERROR -> {
                    throw new RulesEvaluationError((String) stack[--stackPosition], pc);
                }
                case RulesProgram.RETURN_ENDPOINT -> {
                    final var packed = instructions[++pc];
                    final boolean hasHeaders = (packed & 1) != 0;
                    final boolean hasProperties = (packed & 2) != 0;
                    final var urlString = (String) stack[--stackPosition];
                    final var properties = (Map<String, Object>) (hasProperties ? stack[--stackPosition] : Map.of());
                    final var headers = (Map<String, List<String>>) (hasHeaders ? stack[--stackPosition] : Map.of());
                    final var builder = Endpoint.builder().uri(createUri(urlString));
                    if (!headers.isEmpty()) {
                        builder.putProperty(EndpointContext.HEADERS, headers);
                    }
                    for (var extension : program.extensions) {
                        extension.extractEndpointProperties(builder, context, properties, headers);
                    }
                    return builder.build();
                }
                case RulesProgram.CREATE_LIST -> {
                    final var size = instructions[++pc] & 0xFF;
                    push(switch (size) {
                        case 0 -> List.of();
                        case 1 -> Collections.singletonList(stack[--stackPosition]);
                        default -> {
                            var values = new Object[size];
                            for (var i = size - 1; i >= 0; i--) {
                                values[i] = stack[--stackPosition];
                            }
                            yield Arrays.asList(values);
                        }
                    });
                }
                case RulesProgram.CREATE_MAP -> {
                    final var size = instructions[++pc] & 0xFF;
                    push(switch (size) {
                        case 0 -> Map.of();
                        case 1 -> Map.of((String) stack[--stackPosition], stack[--stackPosition]);
                        default -> {
                            Map<String, Object> map = new HashMap<>((int) (size / 0.75f) + 1); // Avoid rehashing
                            for (var i = 0; i < size; i++) {
                                map.put((String) stack[--stackPosition], stack[--stackPosition]);
                            }
                            yield map;
                        }
                    });
                }
                case RulesProgram.RESOLVE_TEMPLATE -> {
                    // Read a two-byte unsigned short.
                    final int constIdx = ((instructions[pc + 2] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    final var template = (StringTemplate) constantPool[constIdx];
                    final var expressionCount = template.expressionCount();
                    final var temp = getTempArray(expressionCount);
                    for (var i = 0; i < expressionCount; i++) {
                        temp[i] = stack[--stackPosition];
                    }
                    push(template.resolve(expressionCount, temp));
                    pc += 2;
                }
                case RulesProgram.FN -> {
                    final var fn = program.functions[instructions[++pc] & 0xFF]; // read unsigned byte
                    push(switch (fn.getOperandCount()) {
                        case 0 -> fn.apply0();
                        case 1 -> fn.apply1(stack[--stackPosition]);
                        case 2 -> {
                            Object b = stack[--stackPosition];
                            Object a = stack[--stackPosition];
                            yield fn.apply2(a, b);
                        }
                        default -> {
                            // Pop arguments from stack in reverse order.
                            var temp = getTempArray(fn.getOperandCount());
                            for (int i = fn.getOperandCount() - 1; i >= 0; i--) {
                                temp[i] = stack[--stackPosition];
                            }
                            yield fn.apply(temp);
                        }
                    });
                }
                case RulesProgram.GET_ATTR -> {
                    // Read a two-byte unsigned short.
                    final int constIdx = ((instructions[pc + 2] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    AttrExpression getAttr = (AttrExpression) program.constantPool[constIdx];
                    final var target = stack[--stackPosition];
                    push(getAttr.apply(target));
                    pc += 2;
                }
                case RulesProgram.IS_TRUE -> {
                    push(stack[--stackPosition] == Boolean.TRUE ? Boolean.TRUE : Boolean.FALSE);
                }
                case RulesProgram.TEST_REGISTER_IS_TRUE -> {
                    push(registers[instructions[++pc] & 0xFF] == Boolean.TRUE ? Boolean.TRUE : Boolean.FALSE);
                }
                case RulesProgram.TEST_REGISTER_IS_FALSE -> {
                    push(registers[instructions[++pc] & 0xFF] == Boolean.FALSE ? Boolean.TRUE : Boolean.FALSE);
                }
                case RulesProgram.RETURN_VALUE -> {
                    return stack[--stackPosition];
                }
                case RulesProgram.EQUALS -> {
                    push(Objects.equals(stack[--stackPosition], stack[--stackPosition]));
                }
                case RulesProgram.SUBSTRING -> {
                    final var string = (String) stack[--stackPosition];
                    final var start = instructions[++pc] & 0xFF;
                    final var end = instructions[++pc] & 0xFF;
                    final var reverse = (instructions[++pc] & 0xFF) != 0 ? Boolean.TRUE : Boolean.FALSE;
                    push(Substring.getSubstring(string, start, end, reverse));
                }
                default -> {
                    throw new RulesEvaluationError("Unknown rules engine instruction: " + instructions[pc]);
                }
            }
        }

        throw new RulesEvaluationError("No value returned from rules engine");
    }

    private void logDebugJump(int jumpTarget) {
        LOGGER.debug("VM jumping from {} to {}", pc, jumpTarget);
        LOGGER.debug("    - Stack ({}): {}", stackPosition, Arrays.toString(stack));
        LOGGER.debug("    - Registers: {}", Arrays.toString(registers));
    }

    public static URI createUri(String uriStr) {
        var cache = URI_LRU_CACHE.get();
        var uri = cache.get(uriStr);
        if (uri == null) {
            try {
                uri = new URI(uriStr);
            } catch (URISyntaxException e) {
                throw new RulesEvaluationError("Error creating URI: " + e.getMessage(), e);
            }
            cache.put(uriStr, uri);
        }
        return uri;
    }

    private Object[] getTempArray(int requiredSize) {
        if (tempArraySize < requiredSize) {
            resizeTempArray(requiredSize);
        }
        return tempArray;
    }

    private void resizeTempArray(int requiredSize) {
        // Resize to a power of two.
        int newSize = MIN_TEMP_ARRAY_SIZE;
        while (newSize < requiredSize) {
            newSize <<= 1;
        }

        tempArray = new Object[newSize];
        tempArraySize = newSize;
    }
}
