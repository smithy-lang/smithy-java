/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.EndpointRuleSet;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Compiles and loads a rules engine used to resolve endpoints based on Smithy's rules engine traits.
 */
public final class RulesEngine {

    static final List<RulesEngineExtension> EXTENSIONS = new ArrayList<>();
    static {
        for (var ext : ServiceLoader.load(RulesEngineExtension.class)) {
            EXTENSIONS.add(ext);
        }
    }

    private final List<VmFunction> functions = new ArrayList<>();
    private final List<BiFunction<String, Context, Object>> builtinProviders = new ArrayList<>();
    private boolean performOptimizations = true;

    RulesEngine() {
        // Always include the standard builtins, but after any explicitly given builtins.
        builtinProviders.add(Stdlib::standardBuiltins);

        for (var ext : EXTENSIONS) {
            functions.addAll(ext.getFunctions());
            var fp = ext.getBuiltinProvider();
            if (fp != null) {
                builtinProviders.add(fp);
            }
        }
    }

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
        return new RulesCompiler(rules, functions, createBuiltinProvider(), performOptimizations).compile();
    }

    /**
     * Loads a pre-compiled {@link RulesProgram}.
     *
     * <p>Warning: this method does little to no validation of the given program, the constant pool, or registers.
     * It is up to you to ensure that these values are all correctly provided or else the rule evaluator will fail
     * during evaluation, or provide unpredictable results.
     *
     * @param program Program instructions to load.
     * @param constantPool Array indexed constant pool.
     * @param registers Array indexed registers.
     * @return the loaded RulesProgram.
     * @throws RulesEvaluationError if the program is invalid or cannot be loaded.
     */
    @SmithyUnstableApi
    public RulesProgram fromPrecompiled(ByteBuffer program, Object[] constantPool, RegisterDefinition[] registers) {
        if (registers.length > 255) {
            throw new IndexOutOfBoundsException("The number of register must fit into a byte");
        }
        Map<String, Byte> registryIndex = new HashMap<>(registers.length);
        for (var i = 0; i < registers.length; i++) {
            registryIndex.put(registers[i].name(), (byte) i);
        }

        var arrayFunctions = new VmFunction[functions.size()];
        functions.toArray(arrayFunctions);

        return new RulesProgram(
                program.array(),
                program.arrayOffset() + program.position(),
                program.remaining(),
                registers,
                registryIndex,
                arrayFunctions,
                createBuiltinProvider(),
                constantPool);
    }
}
