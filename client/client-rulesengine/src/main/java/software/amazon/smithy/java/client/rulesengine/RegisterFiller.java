/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;

/**
 * Fills register arrays with parameter values, builtin providers, and validates required parameters.
 *
 * <p>This class optimizes for the common case where endpoint rules have fewer than 64 registers (parameters + temp
 * variables). For small register counts, we use bitmask operations in {@link FastRegisterFiller} which provides O(1)
 * checks for which registers need filling and validation.
 *
 * <p>For the rare case of 64+ registers, we fall back to {@link LargeRegisterFiller} which uses simple array
 * iteration. The selection is made at construction time based on the register count.
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

        if (registerDefinitions.length < 64) {
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
        private final long defaultMask;

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
            this.defaultMask = makeDefaultMask(registerTemplate);
        }

        private static long makeMask(int[] indices) {
            long mask = 0L;
            for (int i : indices) {
                mask |= 1L << i;
            }
            return mask;
        }

        private static long makeDefaultMask(Object[] registerTemplate) {
            long mask = 0L;
            for (int i = 0; i < registerTemplate.length && i < 64; i++) {
                if (registerTemplate[i] != null) {
                    mask |= 1L << i;
                }
            }
            return mask;
        }

        @Override
        Object[] fillRegisters(Object[] sink, Context context, Map<String, Object> parameters) {
            // Copy template to set up defaults and clear old state
            System.arraycopy(registerTemplate, 0, sink, 0, registerTemplate.length);

            // Start with defaults already marked as filled
            long filled = defaultMask;

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
                throw new RulesEvaluationError("Missing required parameter: " + registerDefinitions[i].name());
            }

            return sink;
        }
    }

    // Fallback implementation for > 64 registers using simple array-based approach.
    private static final class LargeRegisterFiller extends RegisterFiller {
        private final int[] builtinIndices;
        private final int[] hardRequiredIndices;
        private final boolean[] hasDefault;

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

            // Precompute what registers have defaults
            this.hasDefault = new boolean[registerTemplate.length];
            for (int i = 0; i < registerTemplate.length; i++) {
                hasDefault[i] = registerTemplate[i] != null;
            }
        }

        @Override
        Object[] fillRegisters(Object[] sink, Context context, Map<String, Object> parameters) {
            // Copy template to set up defaults and clear old state
            System.arraycopy(registerTemplate, 0, sink, 0, registerTemplate.length);

            // Track what registers have been filled (defaults are already in sink)
            boolean[] filled = Arrays.copyOf(hasDefault, hasDefault.length);

            // Fill parameters
            for (var e : parameters.entrySet()) {
                Integer i = inputRegisterMap.get(e.getKey());
                if (i != null) {
                    sink[i] = e.getValue();
                    filled[i] = true;
                }
            }

            // Fill builtins (only if not already filled)
            for (int regIndex : builtinIndices) {
                if (!filled[regIndex]) {
                    Object result = providersByRegister[regIndex].apply(context);
                    if (result != null) {
                        sink[regIndex] = result;
                        filled[regIndex] = true;
                    }
                }
            }

            // Validate required parameters
            for (int regIndex : hardRequiredIndices) {
                if (!filled[regIndex]) {
                    var name = registerDefinitions[regIndex].name();
                    throw new RulesEvaluationError("Missing required parameter: " + name);
                }
            }

            return sink;
        }
    }
}
