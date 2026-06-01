/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
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

    private static final int MAX_PROBE = 3;

    private final Bytecode bytecode;
    private final RulesExtension[] extensions;
    private final RegisterFiller registerFiller;
    private final ContextProvider ctxProvider = new ContextProvider.OrchestratingProvider();
    private final AtomicReferenceArray<BytecodeEvaluator> pool;
    private final int poolMask;

    public BytecodeEndpointResolver(
            Bytecode bytecode,
            List<RulesExtension> extensions,
            Map<String, Function<Context, Object>> builtinProviders
    ) {
        this.bytecode = bytecode;
        this.extensions = extensions.toArray(new RulesExtension[0]);

        // Create and reuse this register filler across pooled evaluators.
        this.registerFiller = RegisterFiller.of(bytecode, builtinProviders);

        // Slots = next power of two >= cores*4, matching the JSON serializer pool sizing so a
        // burst of concurrent resolves rarely overflows to allocation.
        int raw = Runtime.getRuntime().availableProcessors() * 4;
        int slots = Integer.highestOneBit(Math.max(raw - 1, 1)) << 1;
        this.pool = new AtomicReferenceArray<>(slots);
        this.poolMask = slots - 1;
    }

    public Bytecode getBytecode() {
        return bytecode;
    }

    @Override
    public Endpoint resolveEndpoint(EndpointResolverParams params) {
        var operation = params.operation();
        var ctx = params.context();

        // Per-thread, contention-free probe base (final-field read), shared by acquire and release.
        int base = (int) Thread.currentThread().threadId();

        BytecodeEvaluator evaluator = acquire(base);
        try {
            // Write endpoint params into the register sink
            var sink = evaluator.registerSink;
            ContextProvider.createEndpointParams(sink, ctxProvider, ctx, operation, params.inputValue());

            // Reset the evaluator and prepare new registers from the sink.
            evaluator.resetFromSink(ctx);

            LOGGER.debug("Resolving endpoint of {} using VM", operation);

            return evaluator.evaluateBdd();
        } finally {
            // Recycle for the next resolve. resetFromSink fully reinitializes per-resolve state, so a
            // stale evaluator carries nothing across uses; the Endpoint just returned holds no
            // reference into it.
            release(evaluator, base);
        }
    }

    private BytecodeEvaluator acquire(int base) {
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = (base + i) & poolMask;
            BytecodeEvaluator e = pool.getPlain(idx);
            // Acquire semantics: ensure we see the evaluator's fully-written state from its releaser.
            if (e != null && pool.compareAndExchangeAcquire(idx, e, null) == e) {
                return e;
            }
        }
        return new BytecodeEvaluator(bytecode, extensions, registerFiller);
    }

    private void release(BytecodeEvaluator evaluator, int base) {
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = (base + i) & poolMask;
            // Release semantics: publish all evaluator state to a thread that later acquires it.
            if (pool.getPlain(idx) == null && pool.compareAndExchangeRelease(idx, null, evaluator) == null) {
                return;
            }
        }
        // Pool full — let GC collect this evaluator.
    }
}
