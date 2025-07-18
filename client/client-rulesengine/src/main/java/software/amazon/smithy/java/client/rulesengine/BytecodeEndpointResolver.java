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
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;

/**
 * Endpoint resolver that uses a compiled endpoint rules program from a BDD.
 */
final class BytecodeEndpointResolver implements EndpointResolver {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(BytecodeEndpointResolver.class);
    private static final CompletableFuture<Endpoint> NULL_RESULT = CompletableFuture.completedFuture(null);

    private final Bytecode bytecode;
    private final Bdd bdd;
    private final RulesExtension[] extensions;
    private final RegisterFiller registerFiller;
    private final ContextProvider ctxProvider = new ContextProvider.OrchestratingProvider();
    private final ThreadLocal<BytecodeEvaluator> threadLocalEvaluator;

    BytecodeEndpointResolver(
            Bytecode bytecode,
            List<RulesExtension> extensions,
            Map<String, Function<Context, Object>> builtinProviders
    ) {
        this.bytecode = bytecode;
        this.extensions = extensions.toArray(new RulesExtension[0]);
        this.registerFiller = RegisterFiller.of(bytecode, builtinProviders);
        this.bdd = bytecode.getBdd();

        this.threadLocalEvaluator = ThreadLocal.withInitial(() -> {
            return new BytecodeEvaluator(bytecode, this.extensions);
        });
    }

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        try {
            var evaluator = threadLocalEvaluator.get();
            var operation = params.operation();
            var ctx = params.context();
            // Get reusable params array and clear it
            var inputParams = evaluator.paramsCache;
            inputParams.clear();
            // Prep the input parameters by grabbing them from the input and from other traits.
            ContextProvider.createEndpointParams(inputParams, ctxProvider, ctx, operation, params.inputValue());

            // Use RegisterFiller to set up registers
            Object[] registers = evaluator.getRegisters();
            registerFiller.fillRegisters(registers, ctx, inputParams);
            evaluator.resetWithFilledRegisters(ctx);

            LOGGER.debug("Resolving endpoint of {} using VM with params: {}", operation, inputParams);

            var resultIndex = bdd.evaluate(evaluator);
            if (resultIndex < 0) {
                return NULL_RESULT;
            }
            return CompletableFuture.completedFuture(evaluator.resolveResult(resultIndex));
        } catch (RulesEvaluationError e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
