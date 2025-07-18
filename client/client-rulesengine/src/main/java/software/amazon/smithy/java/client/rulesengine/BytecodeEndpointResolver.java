/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.java.client.rulesengine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.rulesengine.logic.bdd.BddEvaluator;

/**
 * Endpoint resolver that uses a compiled endpoint rules program from a BDD.
 */
final class BytecodeEndpointResolver implements EndpointResolver {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(BytecodeEndpointResolver.class);

    private final Bytecode bytecode;
    private final BddEvaluator bddEvaluator;
    private final List<RulesExtension> extensions;
    private final Map<String, Function<Context, Object>> builtinProviders;
    private final ContextProvider ctxProvider = new ContextProvider.OrchestratingProvider();

    BytecodeEndpointResolver(Bytecode bytecode, List<RulesExtension> extensions,
            Map<String, Function<Context, Object>> builtinProviders) {
        this.bytecode = bytecode;
        this.extensions = extensions;
        this.builtinProviders = builtinProviders;
        bddEvaluator = BddEvaluator.from(
                bytecode.getBddNodes(), bytecode.getBddRootRef(), bytecode.getConditionCount());
    }

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        try {
            var operation = params.operation();
            var ctx = params.context();
            // Prep the input parameters by grabbing them from the input and from other traits.
            var inputParams = ContextProvider.createEndpointParams(ctxProvider, ctx, operation, params.inputValue());
            LOGGER.debug("Resolving endpoint of {} using VM with params: {}", operation, inputParams);
            // Create the bytecode condition and result evaluator. It also creates necessary registers, so can't reuse.
            var condEvaluator = new BytecodeEvaluator(bytecode, ctx, inputParams, builtinProviders, extensions);
            var resultIndex = bddEvaluator.evaluate(condEvaluator);
            if (resultIndex < 0) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(condEvaluator.resolveResult(resultIndex));
        } catch (RulesEvaluationError e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
