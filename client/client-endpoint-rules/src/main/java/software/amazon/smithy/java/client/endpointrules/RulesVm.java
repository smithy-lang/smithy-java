/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

final class RulesVm {

    private final RulesProgram program;
    private final RulesProgram.Register[] registers;
    private final BiFunction<String, Context, Object> builtinProvider;
    private final byte[] instructions;
    private Object[] stack = new Object[8];
    private int stackPosition = 0;

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
        registers = new RulesProgram.Register[program.registry.length];
        for (var i = 0; i < program.registry.length; i++) {
            registers[i] = program.registry[i].getCopy();
        }

        for (var entry : parameters.entrySet()) {
            var index = program.registryIndex.get(entry.getKey());
            if (index != null) {
                registers[index].push(entry.getValue());
            }
        }

        // Validate required parameters, fill in defaults, and grab builtins.
        for (var i = 0; i < registers.length; i++) {
            var register = registers[i];
            if (register.get() == null) {
                initializeRegister(context, i, register);
            }
        }
    }

    Endpoint evaluate() {
        try {
            return run();
        } catch (ClassCastException e) {
            throw new RulesEvaluationError("Unexpected value encountered while evaluating rules engine", e);
        }
    }

    void initializeRegister(Context context, int i, RulesProgram.Register register) {
        if (register.getDefault() != null) {
            register.push(register.getDefault());
            return;
        }

        if (register.getBuiltin() != null) {
            var builtinValue = builtinProvider.apply(register.getBuiltin(), context);
            if (builtinValue != null) {
                register.push(builtinValue);
                return;
            }
        }

        if (register.isRequired()) {
            String name = "?";
            for (var entry : program.registryIndex.entrySet()) {
                if (entry.getValue() == i) {
                    name = entry.getKey();
                    break;
                }
            }
            throw new IllegalArgumentException("Required rules engine parameter missing: " + name);
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

    private Endpoint run() {
        var instructionSize = program.instructionSize;
        for (var pointer = 0; pointer < instructionSize; pointer++) {
            switch (instructions[pointer]) {
                case RulesProgram.LOAD_CONST -> {
                    int constant = instructions[++pointer] & 0xFF; // read unsigned byte
                    push(program.constantPool[constant]);
                }
                case RulesProgram.LOAD_CONST_W -> {
                    push(program.constantPool[readUnsignedShort(pointer + 1)]); // read unsigned short
                    pointer += 2;
                }
                case RulesProgram.PUSH_REGISTER -> {
                    int register = instructions[++pointer] & 0xFF; // read unsigned byte
                    registers[register].push(peek());
                }
                case RulesProgram.POP_REGISTER -> {
                    int register = instructions[++pointer] & 0xFF; // read unsigned byte
                    registers[register].pop();
                }
                case RulesProgram.LOAD_REGISTER -> {
                    int register = instructions[++pointer] & 0xFF; // read unsigned byte
                    push(registers[register].get());
                }
                case RulesProgram.JUMP_IF_FALSEY -> {
                    Object value = pop();
                    if (value == null || Boolean.FALSE.equals(value)) {
                        pointer = readUnsignedShort(pointer + 1) - 1; // -1 because loop will increment
                    } else {
                        pointer += 2;
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
                    int register = instructions[++pointer] & 0xFF; // read unsigned byte
                    var value = registers[register].get();
                    push(value != null && (!(value instanceof Boolean) || (Boolean) value));
                }
                case RulesProgram.SET_ERROR -> {
                    throw new RulesEvaluationError((String) pop());
                }
                case RulesProgram.SET_ENDPOINT -> {
                    short packed = instructions[++pointer];
                    boolean hasHeaders = (packed & 1) != 0;
                    boolean hasProperties = (packed & 2) != 0;
                    return setEndpoint(hasProperties, hasHeaders);
                }
                case RulesProgram.CREATE_LIST -> {
                    int size = instructions[++pointer] & 0xFF; // read unsigned byte
                    List<Object> headers = new ArrayList<>(size);
                    for (var i = 0; i < size; i++) {
                        headers.add(pop());
                    }
                    push(headers);
                }
                case RulesProgram.CREATE_MAP -> {
                    int size = instructions[++pointer] & 0xFF; // read unsigned byte
                    createMap(size);
                }
                case RulesProgram.RESOLVE_TEMPLATE -> {
                    var constant = readUnsignedShort(pointer + 1);
                    resolveTemplate((StringTemplate) program.constantPool[constant]);
                    pointer += 2;
                }
                case RulesProgram.FN -> {
                    int fIndex = instructions[++pointer] & 0xFF; // read unsigned byte
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
                    var constant = readUnsignedShort(pointer + 1);
                    AttrExpression getAttr = (AttrExpression) program.constantPool[constant];
                    var target = pop();
                    push(getAttr.apply(target));
                    pointer += 2;
                }
                case RulesProgram.IS_TRUE -> push(pop() instanceof Boolean b && b);
                case RulesProgram.TEST_REGISTER_IS_TRUE -> {
                    int register = instructions[++pointer] & 0xFF; // read unsigned byte
                    push(registers[register].get() instanceof Boolean b && b);
                }
                default -> {
                    throw new IllegalStateException("Unknown endpoint instruction: " + instructions[pointer]);
                }
            }
        }

        throw new IllegalStateException("No endpoint returned from rules engine");
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
        var builder = Endpoint.builder().uri(urlString);
        if (!headers.isEmpty()) {
            builder.putProperty(Endpoint.HEADERS, headers);
        }
        for (var _e : properties.entrySet()) {
            // TODO: map properties to endpoint properties.
        }
        // TODO: Add auth schemes and figure out how to map properties there too.
        return builder.build();
    }

    private void resolveTemplate(StringTemplate template) {
        if (template.expressionCount() == 0) {
            push(template.resolve());
        }
        String[] dynamicValues = new String[template.expressionCount()];
        for (var i = 0; i < template.expressionCount(); i++) {
            dynamicValues[i] = (String) pop();
        }
        push(template.resolve(dynamicValues));
    }
}
