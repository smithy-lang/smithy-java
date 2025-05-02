/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

/**
 * A compiled and ready to run rules engine program.
 *
 * <p>A RulesProgram can be run any number of times and is thread-safe. A program can be serialized and later restored
 * using {@code ToString}. A RulesProgram is created using a {@link RulesEngine}.
 */
public final class RulesProgram {
    /**
     * Push a value onto the stack.
     */
    static final byte PUSH = 0;

    /**
     * Peeks the value at the top of the stack and pushes it onto the register stack of a register.
     */
    static final byte PUSH_REGISTER = 1;

    /**
     * Pop a register value off its register stack.
     */
    static final byte POP_REGISTER = 2;

    /**
     * Get the value of a register and push it onto the stack.
     */
    static final byte LOAD_REGISTER = 3;

    /**
     * Jumps to an opcode index if the top of the stack is null or false.
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
     * Sets an error on the VM and exits.
     *
     * <p>Pops a single value that provides the error string to set.
     */
    static final byte SET_ERROR = 7;

    /**
     * Sets the endpoint result of the VM and exits.
     *
     * <p>Pops two values:
     * <ol>
     *     <li>The headers of the endpoint in the form of {@code Map<String, List<String>>}.</li>
     *     <li>The endpoint URL as a String that is parsed into a URI.</li>
     * </ol>
     */
    static final byte SET_ENDPOINT = 8;

    /**
     * Pops N values off the stack and pushes a list of those values onto the stack.
     */
    static final byte CREATE_LIST = 9;

    /**
     * Pops N*2 values off the stack (key then value), creates a map of those values, and pushes the map onto the
     * stack. Each popped key must be a string.
     */
    static final byte CREATE_MAP = 10;

    /**
     * Resolves a template string.
     *
     * <p>The corresponding instruction has a StringTemplate that tells the VM how many values to pop off the stack.
     * The popped values fill in values into the template. The resolved template value as a string is then pushed onto
     * the stack.
     */
    static final byte RESOLVE_TEMPLATE = 11;

    /**
     * Calls a function.
     *
     * <p>The function pops zero or more values off the stack based on the VmFunction registered for the index,
     * and then pushes the Object result onto the stack.
     */
    static final byte FN = 12;

    /**
     * Pops the top level value and applies a getAttr expression on it, pushing the result onto the stack.
     */
    static final byte GET_ATTR = 13;

    /**
     * Pops a value and pushes true if the value is boolean true, false if not.
     */
    static final byte IS_TRUE = 14;

    final Object[] instructions;
    final Register[] registry;
    final Map<String, Integer> registryIndex;
    final VmFunction[] functions;
    final Map<String, Integer> functionIndex;
    private final BiFunction<String, Context, Object> builtinProvider;

    RulesProgram(
            Object[] instructions,
            Register[] registry,
            Map<String, Integer> registryIndex,
            VmFunction[] functions,
            Map<String, Integer> functionIndex,
            BiFunction<String, Context, Object> builtinProvider
    ) {
        this.instructions = instructions;
        this.registry = registry;
        this.registryIndex = registryIndex;
        this.functions = functions;
        this.functionIndex = functionIndex;
        this.builtinProvider = builtinProvider;
    }

    /**
     * Runs the rules engine program.
     *
     * @param context Context used during evaluation.
     * @param parameters Rules engine parameters.
     * @return the resolved Endpoint.
     * @throws RulesEvaluationError if the program fails during evaluation.
     */
    public Endpoint run(Context context, Map<String, Object> parameters) {
        for (var e : parameters.entrySet()) {
            EndpointUtils.verifyObject(e.getValue());
        }
        var vm = new RulesVm(context, this, parameters, builtinProvider);
        return vm.evaluate();
    }

    public String printDebug() {

    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        // Write the instructions.
        s.append("{\n  \"instructions\": [\n");
        boolean isFirst = true;
        for (var ins : instructions) {
            if (!isFirst) {
                s.append(",\n");
            } else {
                isFirst = false;
            }
            s.append("    ");
            EndpointUtils.serializeObject(ins, s);
        }
        s.append("\n  ],\n");

        // Write the required function names, in index order.
        if (functions.length > 0) {
            s.append("  \"functions\": [\n");
            isFirst = true;
            for (var f : functions) {
                if (!isFirst) {
                    s.append(",\n");
                } else {
                    isFirst = false;
                }
                s.append("    \"").append(f.getFunctionName()).append('"');
            }
            s.append("\n  ],\n");
        }

        // Write the registry values in index order.
        if (registry.length > 0) {
            s.append("  \"registry\": [\n");
            isFirst = true;
            for (var r : registry) {
                if (!isFirst) {
                    s.append(',').append('\n');
                } else {
                    isFirst = false;
                }
                s.append("    ");
                r.serialize(s);
            }
            s.append("\n  ]\n");
        }

        s.append('}');
        return s.toString();
    }

    static final class Register {
        private final String name;
        private final boolean required;
        private final Object defaultValue;
        private final String builtin;
        private Object value;
        private Deque<Object> stack;

        public Register(String name, boolean required, Object defaultValue, String builtin) {
            this.name = name;
            this.required = required;
            this.defaultValue = defaultValue;
            this.builtin = builtin;
        }

        public boolean isRequired() {
            return required;
        }

        public String getName() {
            return name;
        }

        public Object getDefault() {
            return defaultValue;
        }

        public String getBuiltin() {
            return builtin;
        }

        public Object get() {
            return value;
        }

        public void push(Object value) {
            // Only deal with stacks when there actually needs to be a stack.
            // Most rules don't end up actually needing the stack.
            if (this.value != null) {
                if (stack == null) {
                    stack = new ArrayDeque<>();
                }
                stack.push(this.value);
            }
            this.value = value;
        }

        public Object pop() {
            stack.pop();
            value = stack.peek();
            return value;
        }

        private void serialize(StringBuilder sink) {
            sink.append("{\"name\":\"").append(name).append('"');
            if (required) {
                sink.append(",\"required\":true");
            }
            if (builtin != null) {
                sink.append(",\"builtin\":\"").append(builtin).append('"');
            }
            if (defaultValue != null) {
                sink.append(",\"default\":");
                EndpointUtils.serializeObject(defaultValue, sink);
            }
            sink.append('}');
        }
    }
}
