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

    final Instruction[] instructions;
    final Register[] registry;
    final Map<String, Integer> registryIndex;
    final VmFunction[] functions;
    final Map<String, Integer> functionIndex;
    private final BiFunction<String, Context, Object> builtinProvider;

    RulesProgram(
            Instruction[] instructions,
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
            ins.serialize(s);
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
