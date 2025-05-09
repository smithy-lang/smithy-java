/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

final class RulesVm {

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
            int newCapacity = stack.length + (stack.length >> 1);
            Object[] newStack = new Object[newCapacity];
            System.arraycopy(stack, 0, newStack, 0, stack.length);
            stack = newStack;
        }
        stack[stackPosition++] = value;
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
        // Skip version, params, and register bytes.
        for (pc = program.instructionOffset + 3; pc < instructionSize; pc++) {
            switch (instructions[pc]) {
                case RulesProgram.LOAD_CONST -> {
                    int constant = instructions[++pc] & 0xFF; // read unsigned byte
                    push(program.constantPool[constant]);
                }
                case RulesProgram.LOAD_CONST_W -> {
                    push(program.constantPool[readUnsignedShort(pc + 1)]); // read unsigned short
                    pc += 2;
                }
                case RulesProgram.SET_REGISTER -> {
                    int register = instructions[++pc] & 0xFF; // read unsigned byte
                    registers[register] = peek();
                }
                case RulesProgram.LOAD_REGISTER -> {
                    int register = instructions[++pc] & 0xFF; // read unsigned byte
                    push(registers[register]);
                }
                case RulesProgram.JUMP_IF_FALSEY -> {
                    Object value = pop();
                    if (value == null || Boolean.FALSE.equals(value)) {
                        pc = readUnsignedShort(pc + 1) - 1; // -1 because loop will increment
                    } else {
                        pc += 2;
                    }
                }
                case RulesProgram.NOT -> {
                    Object value = pop();
                    push(!(value instanceof Boolean b && b));
                }
                case RulesProgram.ISSET -> {
                    Object value = pop();
                    // Push true if it's set and not a boolean, or boolean true.
                    push(value != null && (!(value instanceof Boolean) || (Boolean) value));
                }
                case RulesProgram.TEST_REGISTER_ISSET -> {
                    int register = instructions[++pc] & 0xFF; // read unsigned byte
                    var value = registers[register];
                    push(value != null && (!(value instanceof Boolean) || (Boolean) value));
                }
                case RulesProgram.RETURN_ERROR -> {
                    throw new RulesEvaluationError((String) pop());
                }
                case RulesProgram.RETURN_ENDPOINT -> {
                    short packed = instructions[++pc];
                    boolean hasHeaders = (packed & 1) != 0;
                    boolean hasProperties = (packed & 2) != 0;
                    return setEndpoint(hasProperties, hasHeaders);
                }
                case RulesProgram.CREATE_LIST -> {
                    int size = instructions[++pc] & 0xFF; // read unsigned byte
                    List<Object> list = new ArrayList<>(size);
                    for (var i = 0; i < size; i++) {
                        list.add(pop());
                    }
                    Collections.reverse(list);
                    push(list);
                }
                case RulesProgram.CREATE_MAP -> {
                    int size = instructions[++pc] & 0xFF; // read unsigned byte
                    createMap(size);
                }
                case RulesProgram.RESOLVE_TEMPLATE -> {
                    var constant = readUnsignedShort(pc + 1);
                    resolveTemplate((StringTemplate) program.constantPool[constant]);
                    pc += 2;
                }
                case RulesProgram.FN -> {
                    int fIndex = instructions[++pc] & 0xFF; // read unsigned byte
                    var fn = program.functions[fIndex];
                    Object result = switch (fn.getOperandCount()) {
                        case 0 -> fn.apply0();
                        case 1 -> fn.apply1(pop());
                        case 2 -> {
                            Object b = pop();
                            Object a = pop();
                            yield fn.apply2(a, b);
                        }
                        default -> {
                            // Pop arguments from stack in reverse order.
                            Object[] args = new Object[fn.getOperandCount()];
                            for (int i = fn.getOperandCount() - 1; i >= 0; i--) {
                                args[i] = pop();
                            }
                            yield fn.apply(args);
                        }
                    };
                    push(result);
                }
                case RulesProgram.GET_ATTR -> {
                    var constant = readUnsignedShort(pc + 1);
                    AttrExpression getAttr = (AttrExpression) program.constantPool[constant];
                    var target = pop();
                    push(getAttr.apply(target));
                    pc += 2;
                }
                case RulesProgram.IS_TRUE -> push(pop() instanceof Boolean b && b);
                case RulesProgram.TEST_REGISTER_IS_TRUE -> {
                    int register = instructions[++pc] & 0xFF; // read unsigned byte
                    push(registers[register] instanceof Boolean b && b);
                }
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
        Map<String, Object> headers = new HashMap<>(size);
        for (var i = 0; i < size; i++) {
            var value = pop();
            var key = pop();
            headers.put((String) key, value);
        }
        push(headers);
    }

    @SuppressWarnings("unchecked")
    private Endpoint setEndpoint(boolean hasProperties, boolean hasHeaders) {
        var urlString = (String) pop();
        var properties = (Map<String, Object>) (hasProperties ? pop() : Map.of());
        var headers = (Map<String, List<String>>) (hasHeaders ? pop() : Map.of());
        var builder = Endpoint.builder().uri(createUri(urlString));

        if (!headers.isEmpty()) {
            builder.putProperty(Endpoint.HEADERS, headers);
        }

        for (var _e : properties.entrySet()) {
            // TODO: map properties to endpoint properties.
        }

        // TODO: Add auth schemes and figure out how to map properties there too.
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

    private void resolveTemplate(StringTemplate template) {
        if (template.expressionCount() == 0) {
            push(template.resolve());
        } else {
            String[] dynamicValues = new String[template.expressionCount()];
            for (var i = 0; i < template.expressionCount(); i++) {
                dynamicValues[i] = (String) pop();
            }
            push(template.resolve(dynamicValues));
        }
    }
}
