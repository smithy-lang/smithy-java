/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;

/**
 * Fills register arrays with parameter values, builtin providers, and validates required parameters.
 */
abstract class RegisterFiller {
    protected final Function<Context, Object>[] providersByRegister;
    protected final Map<String, Integer> inputRegisterMap;
    protected final RegisterDefinition[] registerDefinitions;
    protected final Object[] registerTemplate;

    @SuppressWarnings("unchecked")
    protected RegisterFiller(
            RegisterDefinition[] registerDefinitions,
            Map<String, Integer> inputRegisterMap,
            Map<String, Function<Context, Object>> builtinProviders,
            int[] builtinIndices,
            Object[] registerTemplate
    ) {
        this.registerDefinitions = registerDefinitions;
        this.inputRegisterMap = inputRegisterMap;
        this.registerTemplate = registerTemplate;

        if (registerTemplate.length != registerDefinitions.length) {
            throw new IllegalArgumentException(String.format(
                    "Template length (%d) must match register definitions length (%d)",
                    registerTemplate.length,
                    registerDefinitions.length));
        }

        // Align providers by register index for O(1) access
        this.providersByRegister = new Function[registerDefinitions.length];
        for (int regIndex : builtinIndices) {
            String builtinName = registerDefinitions[regIndex].builtin();
            Function<Context, Object> provider = builtinProviders.get(builtinName);
            if (provider == null) {
                throw new IllegalStateException("Missing builtin provider: " + builtinName);
            }
            this.providersByRegister[regIndex] = provider;
        }
    }

    /**
     * Fill the register array with parameter values, builtin providers, and validate required parameters.
     *
     * <p>First copies the register template to set up defaults and clear old state, then fills
     * parameters and builtins, and finally validates required parameters.
     *
     * @param sink the register array to fill
     * @param context the context for builtin providers
     * @param parameters the input parameters
     * @return the filled register array
     * @throws RulesEvaluationError if a required parameter is missing
     */
    abstract Object[] fillRegisters(Object[] sink, Context context, Map<String, Object> parameters);

    /**
     * Factory method to create the appropriate RegisterFiller implementation.
     *
     * @param bytecode the bytecode containing register definitions and indices
     * @param builtinProviders map from builtin names to provider functions
     * @return the appropriate RegisterFiller implementation
     */
    static RegisterFiller of(Bytecode bytecode, Map<String, Function<Context, Object>> builtinProviders) {
        RegisterDefinition[] registerDefinitions = bytecode.getRegisterDefinitions();
        int[] builtinIndices = bytecode.getBuiltinIndices();
        int[] hardRequiredIndices = bytecode.getHardRequiredIndices();
        Map<String, Integer> inputRegisterMap = bytecode.getInputRegisterMap();
        Object[] registerTemplate = bytecode.getRegisterTemplate();

        if (registerDefinitions.length - 1 < 64) {
            return new FastRegisterFiller(registerDefinitions,
                    builtinIndices,
                    hardRequiredIndices,
                    inputRegisterMap,
                    builtinProviders,
                    registerTemplate);
        } else {
            return new LargeRegisterFiller(registerDefinitions,
                    builtinIndices,
                    hardRequiredIndices,
                    inputRegisterMap,
                    builtinProviders,
                    registerTemplate);
        }
    }

    // Fast implementation for <= 64 registers using single long bitmasks.
    private static final class FastRegisterFiller extends RegisterFiller {
        private final long builtinMask;
        private final long requiredMask;

        FastRegisterFiller(
                RegisterDefinition[] registerDefinitions,
                int[] builtinIndices,
                int[] hardRequiredIndices,
                Map<String, Integer> inputRegisterMap,
                Map<String, Function<Context, Object>> builtinProviders,
                Object[] registerTemplate
        ) {
            super(registerDefinitions, inputRegisterMap, builtinProviders, builtinIndices, registerTemplate);
            this.builtinMask = makeMask(builtinIndices);
            this.requiredMask = makeMask(hardRequiredIndices);
        }

        private static long makeMask(int[] indices) {
            long mask = 0L;
            for (int i : indices) {
                mask |= 1L << i;
            }
            return mask;
        }

        @Override
        Object[] fillRegisters(Object[] sink, Context context, Map<String, Object> parameters) {
            // Copy template to set up defaults and clear old state
            System.arraycopy(registerTemplate, 0, sink, 0, registerTemplate.length);

            long filled = 0L;

            // Fill parameters
            for (var e : parameters.entrySet()) {
                Integer i = inputRegisterMap.get(e.getKey());
                if (i != null) {
                    sink[i] = e.getValue();
                    filled |= 1L << i;
                }
            }

            // Fill builtins, and early exit if all filled
            if ((filled & builtinMask) != builtinMask) {
                long unfilled = builtinMask & ~filled;
                while (unfilled != 0) {
                    int i = Long.numberOfTrailingZeros(unfilled);
                    unfilled &= unfilled - 1; // Clear lowest set bit
                    var result = providersByRegister[i].apply(context);
                    if (result != null) {
                        sink[i] = result;
                        filled |= 1L << i;
                    }
                }
            }

            // Validate required parameters
            long missingRequired = requiredMask & ~filled;
            if (missingRequired != 0) {
                int i = Long.numberOfTrailingZeros(missingRequired);
                throw new RulesEvaluationError("Required parameter missing: " + registerDefinitions[i].name());
            }

            return sink;
        }
    }

    // Fallback implementation for > 64 registers using simple array-based approach.
    private static final class LargeRegisterFiller extends RegisterFiller {
        private final int[] builtinIndices;
        private final int[] hardRequiredIndices;

        LargeRegisterFiller(
                RegisterDefinition[] registerDefinitions,
                int[] builtinIndices,
                int[] hardRequiredIndices,
                Map<String, Integer> inputRegisterMap,
                Map<String, Function<Context, Object>> builtinProviders,
                Object[] registerTemplate
        ) {
            super(registerDefinitions, inputRegisterMap, builtinProviders, builtinIndices, registerTemplate);
            this.builtinIndices = builtinIndices;
            this.hardRequiredIndices = hardRequiredIndices;
        }

        @Override
        Object[] fillRegisters(Object[] sink, Context context, Map<String, Object> parameters) {
            // Copy template to set up defaults and clear old state
            System.arraycopy(registerTemplate, 0, sink, 0, registerTemplate.length);

            // Fill parameters
            for (var e : parameters.entrySet()) {
                Integer i = inputRegisterMap.get(e.getKey());
                if (i != null) {
                    sink[i] = e.getValue();
                }
            }

            // Fill builtins (simple null check)
            for (int regIndex : builtinIndices) {
                if (sink[regIndex] == null) {
                    var provider = providersByRegister[regIndex];
                    if (provider != null) {
                        sink[regIndex] = provider.apply(context);
                    }
                }
            }

            // Validate required parameters
            for (int regIndex : hardRequiredIndices) {
                if (sink[regIndex] == null) {
                    throw new RulesEvaluationError(
                            "Required parameter missing: " + registerDefinitions[regIndex].name());
                }
            }

            return sink;
        }
    }
}
