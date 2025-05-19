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
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;

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
        }
    }

    private RulesEvaluationError createError(String message, RuntimeException e) {
        var report = message + ". Encountered at address " + pc + " of program:\n" + program;
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

    private Object pop() {
        return stack[--stackPosition]; // no need to clear out the memory since it's tied to lifetime of the VM.
    }

    private Object peek() {
        return stack[stackPosition - 1];
    }

    // Reads the next two bytes in little-endian order.
    private int readUnsignedShort(int position) {
        return EndpointUtils.bytesToShort(instructions, position);
    }

    private Object run() {
        var instructionSize = program.instructionSize;
        var constantPool = program.constantPool;
        var instructions = this.instructions;
        var registers = this.registers;

        // Skip version, params, and register bytes.
        for (pc = program.instructionOffset + 3; pc < instructionSize; pc++) {
            switch (instructions[pc]) {
                case RulesProgram.LOAD_CONST -> push(constantPool[instructions[++pc] & 0xFF]); // read unsigned byte
                case RulesProgram.LOAD_CONST_W -> {
                    push(constantPool[readUnsignedShort(pc + 1)]); // read unsigned short
                    pc += 2;
                }
                case RulesProgram.SET_REGISTER -> registers[instructions[++pc] & 0xFF] = peek(); // read unsigned byte
                case RulesProgram.LOAD_REGISTER -> push(registers[instructions[++pc] & 0xFF]); // read unsigned byte
                case RulesProgram.JUMP_IF_FALSEY -> {
                    Object value = pop();
                    if (value == null || value == Boolean.FALSE) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("VM jumping from {} to {}", pc, readUnsignedShort(pc + 1));
                            LOGGER.debug("    - Stack ({}): {}", stackPosition, Arrays.toString(stack));
                            LOGGER.debug("    - Registers: {}", Arrays.toString(registers));
                        }
                        pc = readUnsignedShort(pc + 1) - 1; // -1 because loop will increment
                    } else {
                        pc += 2;
                    }
                }
                case RulesProgram.NOT -> push(pop() == Boolean.FALSE);
                case RulesProgram.ISSET -> push(pop() != null);
                case RulesProgram.TEST_REGISTER_ISSET -> push(registers[instructions[++pc] & 0xFF] != null);
                case RulesProgram.RETURN_ERROR -> {
                    throw new RulesEvaluationError((String) pop(), pc);
                }
                case RulesProgram.RETURN_ENDPOINT -> {
                    return setEndpoint(instructions[++pc]);
                }
                case RulesProgram.CREATE_LIST -> createList(instructions[++pc] & 0xFF); // read unsigned byte
                case RulesProgram.CREATE_MAP -> createMap(instructions[++pc] & 0xFF); // read unsigned byte
                case RulesProgram.RESOLVE_TEMPLATE -> {
                    resolveTemplate((StringTemplate) constantPool[readUnsignedShort(pc + 1)]);
                    pc += 2;
                }
                case RulesProgram.FN -> {
                    var fn = program.functions[instructions[++pc] & 0xFF]; // read unsigned byte
                    push(switch (fn.getOperandCount()) {
                        case 0 -> fn.apply0();
                        case 1 -> fn.apply1(pop());
                        case 2 -> {
                            Object b = pop();
                            Object a = pop();
                            yield fn.apply2(a, b);
                        }
                        default -> {
                            // Pop arguments from stack in reverse order.
                            var temp = getTempArray(fn.getOperandCount());
                            for (int i = fn.getOperandCount() - 1; i >= 0; i--) {
                                temp[i] = pop();
                            }
                            yield fn.apply(temp);
                        }
                    });
                }
                case RulesProgram.GET_ATTR -> {
                    var constant = readUnsignedShort(pc + 1);
                    AttrExpression getAttr = (AttrExpression) constantPool[constant];
                    var target = pop();
                    push(getAttr.apply(target));
                    pc += 2;
                }
                case RulesProgram.IS_TRUE -> push(pop() == Boolean.TRUE);
                case RulesProgram.TEST_REGISTER_IS_TRUE -> push(registers[instructions[++pc] & 0xFF] == Boolean.TRUE);
                case RulesProgram.RETURN_VALUE -> {
                    return pop();
                }
                default -> {
                    throw new RulesEvaluationError("Unknown rules engine instruction: " + instructions[pc]);
                }
            }
        }

        throw new RulesEvaluationError("No value returned from rules engine");
    }

    private void createMap(int size) {
        push(switch (size) {
            case 0 -> Map.of();
            case 1 -> Map.of((String) pop(), pop());
            case 2 -> Map.of((String) pop(), pop(), (String) pop(), pop());
            case 3 -> Map.of((String) pop(), pop(), (String) pop(), pop(), (String) pop(), pop());
            default -> {
                Map<String, Object> map = new HashMap<>((int) (size / 0.75f) + 1); // Avoid rehashing
                for (var i = 0; i < size; i++) {
                    map.put((String) pop(), pop());
                }
                yield map;
            }
        });
    }

    private void createList(int size) {
        push(switch (size) {
            case 0 -> List.of();
            case 1 -> Collections.singletonList(pop());
            default -> {
                var values = new Object[size];
                for (var i = size - 1; i >= 0; i--) {
                    values[i] = pop();
                }
                yield Arrays.asList(values);
            }
        });
    }

    private void resolveTemplate(StringTemplate template) {
        var expressionCount = template.expressionCount();
        var temp = getTempArray(expressionCount);
        for (var i = 0; i < expressionCount; i++) {
            temp[i] = pop();
        }
        push(template.resolve(expressionCount, temp));
    }

    @SuppressWarnings("unchecked")
    private Endpoint setEndpoint(byte packed) {
        boolean hasHeaders = (packed & 1) != 0;
        boolean hasProperties = (packed & 2) != 0;
        var urlString = (String) pop();
        var properties = (Map<String, Object>) (hasProperties ? pop() : Map.of());
        var headers = (Map<String, List<String>>) (hasHeaders ? pop() : Map.of());
        var builder = Endpoint.builder().uri(createUri(urlString));

        if (!headers.isEmpty()) {
            builder.putProperty(EndpointContext.HEADERS, headers);
        }

        for (var extension : program.extensions) {
            extension.extractEndpointProperties(builder, context, properties, headers);
        }

        return builder.build();
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
