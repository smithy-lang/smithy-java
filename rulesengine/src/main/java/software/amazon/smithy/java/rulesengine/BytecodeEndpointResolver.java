/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.endpoints.EndpointResolverParams;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Endpoint resolver that uses a compiled endpoint rules program from a BDD.
 */
public final class BytecodeEndpointResolver implements EndpointResolver {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(BytecodeEndpointResolver.class);

    private final Bytecode bytecode;
    private final RulesExtension[] extensions;
    private final RegisterFiller registerFiller;
    private final ContextProvider ctxProvider = new ContextProvider.OrchestratingProvider();
    private final ThreadLocal<BytecodeEvaluator> threadLocalEvaluator;

    public BytecodeEndpointResolver(
            Bytecode bytecode,
            List<RulesExtension> extensions,
            Map<String, Function<Context, Object>> builtinProviders
    ) {
        this.bytecode = bytecode;
        this.extensions = extensions.toArray(new RulesExtension[0]);

        // Create and reuse this register filler across thread local evaluators.
        this.registerFiller = RegisterFiller.of(bytecode, builtinProviders);
        this.threadLocalEvaluator = ThreadLocal.withInitial(() -> {
            return new BytecodeEvaluator(bytecode, this.extensions, registerFiller);
        });
    }

    public Bytecode getBytecode() {
        return bytecode;
    }

    @Override
    public Endpoint resolveEndpoint(EndpointResolverParams params) {
        var evaluator = threadLocalEvaluator.get();
        var operation = params.operation();
        var ctx = params.context();

        // Write endpoint params into the register sink
        var sink = evaluator.registerSink;
        ContextProvider.createEndpointParams(sink, ctxProvider, ctx, operation, params.inputValue());

        // Reset the evaluator and prepare new registers from the sink.
        evaluator.resetFromSink(ctx);

        LOGGER.debug("Resolving endpoint of {} using VM", operation);

        return evaluator.evaluateBdd();
    }
}
