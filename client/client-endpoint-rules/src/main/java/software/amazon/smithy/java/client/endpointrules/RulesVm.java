/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

final class RulesVm {

    private final RulesProgram program;
    private final List<Object> stack = new ArrayList<>();
    private final BiFunction<String, Context, Object> builtinProvider;

    RulesVm(
            Context context,
            RulesProgram program,
            Map<String, Object> parameters,
            BiFunction<String, Context, Object> builtinProvider
    ) {
        this.program = program;
        this.builtinProvider = builtinProvider;

        for (var entry : parameters.entrySet()) {
            var index = program.registryIndex.get(entry.getKey());
            if (index != null) {
                program.registry[index].push(entry.getValue());
            }
        }

        // Validate required parameters, fill in defaults, and grab builtins.
        for (var i = 0; i < program.registry.length; i++) {
            var register = program.registry[i];
            if (register.get() == null) {
                initializeRegister(context, i, register);
            }
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

    Endpoint evaluate() {
        try {
            return run();
        } catch (ClassCastException e) {
            throw new RulesEvaluationError("Unexpected value encountered while evaluating rules engine", e);
        }
    }

    private Object pop() {
        return stack.remove(stack.size() - 1);
    }

    private Object peek() {
        return stack.get(stack.size() - 1);
    }

    private Endpoint run() {
        for (var pointer = 0; pointer < program.instructions.length; pointer++) {
            var instruction = program.instructions[pointer];
            switch (instruction.opcode()) {
                case PUSH -> stack.add(((Instruction.Push) instruction).value());
                case LOAD_REGISTER -> {
                    int regIndex = ((Instruction.LoadRegister) instruction).register();
                    stack.add(program.registry[regIndex].get());
                }
                case PUSH_REGISTER -> {
                    var pushRegister = (Instruction.PushRegister) instruction;
                    program.registry[pushRegister.register()].push(peek());
                }
                case POP_REGISTER -> {
                    int regIndex = ((Instruction.PopRegister) instruction).register();
                    program.registry[regIndex].pop();
                }
                case JUMP -> {
                    pointer = ((Instruction.Jump) instruction).target() - 1; // -1 because loop will increment
                }
                case JUMP_IF_FALSEY -> {
                    Object value = pop();
                    if (value == null || (value instanceof Boolean b && !b)) {
                        int target = ((Instruction.JumpIfFalsey) instruction).target();
                        pointer = target - 1; // -1 because loop will increment
                    }
                }
                case NOT -> {
                    Object value = pop();
                    if (value == null) {
                        stack.add(true);
                    } else if (value instanceof Boolean b) {
                        stack.add(!b);
                    } else {
                        stack.add(false);
                    }
                }
                case ISSET -> {
                    Object value = pop();
                    if (value == null) {
                        stack.add(false);
                    } else if (value instanceof Boolean b) {
                        stack.add(b);
                    } else {
                        stack.add(true);
                    }
                }
                case IS_TRUE -> stack.add((pop() instanceof Boolean b) ? b : false);
                case FN -> {
                    var fnInst = (Instruction.Fn) instruction;
                    int fIndex = fnInst.functionIndex();
                    var fn = program.functions[fIndex];
                    // Pop arguments from stack in reverse order.
                    Object[] args = new Object[fn.getOperandCount()];
                    for (int i = fn.getOperandCount() - 1; i >= 0; i--) {
                        args[i] = pop();
                    }
                    stack.add(fn.apply(args));
                }
                case SET_ERROR -> {
                    var error = (String) pop();
                    throw new RulesEvaluationError(error);
                }
                case SET_ENDPOINT -> {
                    return setEndpoint((Instruction.SetEndpoint) instruction);
                }
                case CREATE_MAP -> createMap((Instruction.CreateMap) instruction);
                case CREATE_LIST -> {
                    var ins = (Instruction.CreateList) instruction;
                    List<Object> headers = new ArrayList<>(ins.listSize());
                    for (var i = 0; i < ins.listSize(); i++) {
                        headers.add(pop());
                    }
                    stack.add(headers);
                }
                case RESOLVE_TEMPLATE -> resolveTemplate((Instruction.ResolveTemplate) instruction);
                case GET_ATTR -> {
                    var getAttr = (Instruction.GetAttr) instruction;
                    var target = pop();
                    stack.add(getAttr.expression().apply(target));
                }
                default -> {
                    throw new IllegalStateException("Unknown endpoint instruction: " + instruction);
                }
            }
        }

        throw new IllegalStateException("No endpoint returned from rules engine");
    }

    private void createMap(Instruction.CreateMap ins) {
        if (ins.mapSize() == 0) {
            stack.add(Collections.emptyMap());
        } else {
            Map<String, Object> headers = new HashMap<>(ins.mapSize());
            for (var i = 0; i < ins.mapSize(); i++) {
                var value = pop();
                var key = pop();
                if (key instanceof String s) {
                    headers.put(s, value);
                } else {
                    throw new RulesEvaluationError(
                            "Rules engine map key expected to be string, but found " + key);
                }
            }
            stack.add(headers);
        }
    }

    @SuppressWarnings("unchecked")
    private Endpoint setEndpoint(Instruction.SetEndpoint setEndpoint) {
        var urlString = (String) pop();
        var properties = (Map<String, Object>) (setEndpoint.hasProperties() ? pop() : Map.of());
        var headers = (Map<String, List<String>>) (setEndpoint.hasHeaders() ? pop() : Map.of());
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

    private void resolveTemplate(Instruction.ResolveTemplate ins) {
        if (ins.template().expressionCount() == 0) {
            stack.add(ins.template().resolve());
        }
        String[] dynamicValues = new String[ins.template().expressionCount()];
        for (var i = 0; i < ins.template().expressionCount(); i++) {
            dynamicValues[i] = (String) pop();
        }
        stack.add(ins.template().resolve(dynamicValues));
    }
}
