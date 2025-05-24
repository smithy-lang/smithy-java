/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Compiles and loads a rules engine used to resolve endpoints based on Smithy's rules engine traits.
 */
public final class RulesEngine {

    static final List<RulesExtension> EXTENSIONS = new ArrayList<>();
    static {
        for (var ext : ServiceLoader.load(RulesExtension.class)) {
            EXTENSIONS.add(ext);
        }
    }

    private final List<RulesExtension> extensions = new ArrayList<>();
    private final Map<String, RulesFunction> functions = new LinkedHashMap<>();
    private final List<BiFunction<String, Context, Object>> builtinProviders = new ArrayList<>();
    private boolean performOptimizations = true;

    public RulesEngine() {
        // Always include the standard builtins, but after any explicitly given builtins.
        builtinProviders.add(Stdlib::standardBuiltins);

        // Always include standard library functions.
        for (var fn : Stdlib.values()) {
            this.functions.put(fn.getFunctionName(), fn);
        }

        for (var ext : EXTENSIONS) {
            addExtension(ext);
        }
    }

    /**
     * Register a function with the rules engine.
     *
     * @param fn Function to register.
     * @return the RulesEngine.
     */
    public RulesEngine addFunction(RulesFunction fn) {
        functions.put(fn.getFunctionName(), fn);
        return this;
    }

    /**
     * Register a builtin provider with the rules engine.
     *
     * <p>Providers that do not implement support for a builtin by name must return null, to allow for composing
     * multiple providers and calling them one after the other.
     *
     * @param builtinProvider Provider to register.
     * @return the RulesEngine.
     */
    public RulesEngine addBuiltinProvider(BiFunction<String, Context, Object> builtinProvider) {
        if (builtinProvider != null) {
            this.builtinProviders.add(builtinProvider);
        }
        return this;
    }

    /**
     * Manually add a RulesEngineExtension to the engine that injects functions and builtins.
     *
     * @param extension Extension to register.
     * @return the RulesEngine.
     */
    public RulesEngine addExtension(RulesExtension extension) {
        extensions.add(extension);
        addBuiltinProvider(extension.getBuiltinProvider());
        for (var f : extension.getFunctions()) {
            addFunction(f);
        }
        return this;
    }

    /**
     * Call this method to disable optional optimizations, like eliminating common subexpressions.
     *
     * <p>This might be useful if the client will only make a single call on a simple ruleset.
     *
     * @return the RulesEngine.
     */
    public RulesEngine disableOptimizations() {
        performOptimizations = false;
        return this;
    }

    private BiFunction<String, Context, Object> createBuiltinProvider() {
        return (name, ctx) -> {
            for (var provider : builtinProviders) {
                var result = provider.apply(name, ctx);
                if (result != null) {
                    return result;
                }
            }
            return null;
        };
    }

    /**
     * Compile rules into a {@link RulesProgram}.
     *
     * @param rules Rules to compile.
     * @return the compiled program.
     */
    public RulesProgram compile(EndpointRuleSet rules) {
        return new RulesCompiler(extensions, rules, functions, createBuiltinProvider(), performOptimizations).compile();
    }

    /**
     * Creates a builder used to create a pre-compiled {@link RulesProgram}.
     *
     * <p>Warning: this method does little to no validation of the given program, the constant pool, or registers.
     * It is up to you to ensure that these values are all correctly provided or else the rule evaluator will fail
     * during evaluation, or provide unpredictable results.
     *
     * @return the builder.
     */
    @SmithyUnstableApi
    public PrecompiledBuilder precompiledBuilder() {
        return new PrecompiledBuilder();
    }

    @SmithyUnstableApi
    public final class PrecompiledBuilder {
        private ByteBuffer bytecode;
        private Object[] constantPool;
        private List<ParamDefinition> parameters = List.of();
        private String[] functionNames;

        public PrecompiledBuilder bytecode(ByteBuffer bytecode) {
            this.bytecode = bytecode;
            return this;
        }

        public PrecompiledBuilder bytecode(byte... bytes) {
            return bytecode(ByteBuffer.wrap(bytes));
        }

        public PrecompiledBuilder constantPool(Object... constantPool) {
            this.constantPool = constantPool;
            return this;
        }

        public PrecompiledBuilder parameters(ParamDefinition... paramDefinitions) {
            this.parameters = Arrays.asList(paramDefinitions);
            return this;
        }

        public PrecompiledBuilder functionNames(String... functionNames) {
            this.functionNames = functionNames;
            return this;
        }

        public RulesProgram build() {
            Objects.requireNonNull(bytecode, "Missing bytecode for program");
            if (constantPool == null) {
                constantPool = new Object[0];
            }

            RulesFunction[] indexedFunctions;
            if (functionNames == null) {
                indexedFunctions = new RulesFunction[0];
            } else {
                // Load the ordered list of functions and fail if any are missing.
                indexedFunctions = new RulesFunction[functionNames.length];
                int i = 0;
                for (var f : functionNames) {
                    var func = functions.get(f);
                    if (func == null) {
                        throw new UnsupportedOperationException("Rules engine program requires missing function: " + f);
                    }
                    indexedFunctions[i++] = func;
                }
            }

            return new RulesProgram(
                    extensions,
                    bytecode.array(),
                    bytecode.arrayOffset() + bytecode.position(),
                    bytecode.remaining(),
                    parameters,
                    indexedFunctions,
                    createBuiltinProvider(),
                    constantPool);
        }
    }
}
