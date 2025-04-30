/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;

/**
 * Compiles and loads a rules engine used to resolve endpoints based on Smithy's rules engine traits.
 *
 * TODO: Add service loader for extensions.
 */
public final class RulesEngine {

    private final List<VmFunction> functions = new ArrayList<>();
    private final List<BiFunction<String, Context, Object>> builtinProviders = new ArrayList<>();
    private boolean performOptimizations = true;

    /**
     * Register a function with the rules engine.
     *
     * @param fn Function to register.
     * @return the RulesEngine.
     */
    public RulesEngine addFunction(VmFunction fn) {
        functions.add(fn);
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
        this.builtinProviders.add(builtinProvider);
        return this;
    }

    /**
     * Manually add a RulesEngineExtension to the engine that injects functions and builtins.
     *
     * @param extension Extension to register.
     * @return the RulesEngine.
     */
    public RulesEngine addExtension(RulesEngineExtension extension) {
        var provider = extension.getBuiltinProvider();
        if (provider != null) {
            builtinProviders.add(provider);
        }
        functions.addAll(extension.getFunctions());
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

    /**
     * Loads a pre-compiled {@link RulesProgram} that was serialized using {@link RulesProgram#toString()}.
     *
     * @param program Program instructions to load.
     * @return the loaded RulesProgram.
     * @throws RulesEvaluationError if the program is invalid or cannot be loaded.
     */
    public RulesProgram loadProgram(String program) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Compile rules into a {@link RulesProgram}.
     *
     * @param rules Rules to compile.
     * @return the compiled program.
     */
    public RulesProgram compile(EndpointRuleSet rules) {
        // Always include the standard builtins, but after any explicitly given builtins.
        builtinProviders.add(Stdlib::standardBuiltins);
        // Create an aggregate builtin provider BiFunction.
        BiFunction<String, Context, Object> builtinProvider = (name, ctx) -> {
            for (var provider : builtinProviders) {
                var result = provider.apply(name, ctx);
                if (result != null) {
                    return result;
                }
            }
            return null;
        };
        return new RulesCompiler(rules, functions, builtinProvider, performOptimizations).compile();
    }
}
