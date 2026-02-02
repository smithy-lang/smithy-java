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
    // Dense arrays for key-based builtins
    protected final int[] keyBuiltinIndices; // Register indices that use keys
    protected final Context.Key<?>[] keyBuiltins; // Parallel array of keys
    protected final Object[] keyDefaults; // Parallel array of defaults

    // Dense arrays for provider-based builtins  
    protected final int[] providerBuiltinIndices; // Register indices that use providers
    protected final Function<Context, Object>[] providerBuiltins; // Parallel array of providers
    protected final Object[] providerDefaults; // Parallel array of defaults

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

        // Count key vs provider builtins
        int keyCount = 0, providerCount = 0;
        for (int regIndex : builtinIndices) {
            if (registerDefinitions[regIndex].builtinKey() != null) {
                keyCount++;
            } else {
                providerCount++;
            }
        }

        // Allocate dense arrays
        this.keyBuiltinIndices = new int[keyCount];
        this.keyBuiltins = new Context.Key[keyCount];
        this.keyDefaults = new Object[keyCount];
        this.providerBuiltinIndices = new int[providerCount];
        this.providerBuiltins = new Function[providerCount];
        this.providerDefaults = new Object[providerCount];

        // Fill dense arrays
        int ki = 0;
        int pi = 0;
        for (int regIndex : builtinIndices) {
            RegisterDefinition def = registerDefinitions[regIndex];
            if (def.builtinKey() != null) {
                keyBuiltinIndices[ki] = regIndex;
                keyBuiltins[ki] = def.builtinKey();
                keyDefaults[ki] = def.defaultValue();
                ki++;
            } else {
                Function<Context, Object> provider = builtinProviders.get(def.builtin());
                if (provider == null) {
                    throw new IllegalStateException("Missing builtin provider: " + def.builtin());
                }
                providerBuiltinIndices[pi] = regIndex;
                providerBuiltins[pi] = provider;
                providerDefaults[pi] = def.defaultValue();
                pi++;
            }
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

    // Fast implementation for < 64 registers using single long bitmasks.
    private static final class FastRegisterFiller extends RegisterFiller {
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

            // Apply key-based builtins
            for (int j = 0; j < keyBuiltinIndices.length; j++) {
                int regIdx = keyBuiltinIndices[j];
                if ((filled & (1L << regIdx)) == 0) {
                    Object result = context.get(keyBuiltins[j]);
                    if (result != null) {
                        sink[regIdx] = result;
                        filled |= 1L << regIdx;
                    } else if (keyDefaults[j] != null) {
                        sink[regIdx] = keyDefaults[j];
                        filled |= 1L << regIdx;
                    }
                }
            }

            // Apply provider-based builtins
            for (int j = 0; j < providerBuiltinIndices.length; j++) {
                int regIdx = providerBuiltinIndices[j];
                if ((filled & (1L << regIdx)) == 0) {
                    Object result = providerBuiltins[j].apply(context);
                    if (result != null) {
                        sink[regIdx] = result;
                        filled |= 1L << regIdx;
                    } else if (providerDefaults[j] != null) {
                        sink[regIdx] = providerDefaults[j];
                        filled |= 1L << regIdx;
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

    private static final class LargeRegisterFiller extends RegisterFiller {
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

            // Track what's been filled
            boolean[] filled = Arrays.copyOf(hasDefault, hasDefault.length);

            // Apply parameters (overrides defaults and will override builtins)
            for (var e : parameters.entrySet()) {
                Integer i = inputRegisterMap.get(e.getKey());
                if (i != null) {
                    sink[i] = e.getValue();
                    filled[i] = true;
                }
            }

            // Fill key-based builtins using dense arrays
            for (int j = 0; j < keyBuiltinIndices.length; j++) {
                int regIdx = keyBuiltinIndices[j];
                if (!filled[regIdx]) {
                    Object result = context.get(keyBuiltins[j]);
                    if (result != null) {
                        sink[regIdx] = result;
                        filled[regIdx] = true;
                    } else if (keyDefaults[j] != null) {
                        sink[regIdx] = keyDefaults[j];
                        filled[regIdx] = true;
                    }
                }
            }

            // Fill provider-based builtins using dense arrays
            for (int j = 0; j < providerBuiltinIndices.length; j++) {
                int regIdx = providerBuiltinIndices[j];
                if (!filled[regIdx]) {
                    Object result = providerBuiltins[j].apply(context);
                    if (result != null) {
                        sink[regIdx] = result;
                        filled[regIdx] = true;
                    } else if (providerDefaults[j] != null) {
                        sink[regIdx] = providerDefaults[j];
                        filled[regIdx] = true;
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
