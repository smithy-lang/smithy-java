/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.util.concurrent.atomic.AtomicReferenceArray;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.codegen.rt.WriterContext;

/**
 * JSON-specific writer context that extends {@link WriterContext} with Schubfach float/double
 * converters and striped pooling.
 */
public final class JsonWriterContext extends WriterContext {

    public final Schubfach.DoubleToDecimal dtd = Schubfach.createDoubleToDecimal();
    public final Schubfach.FloatToDecimal ftd = Schubfach.createFloatToDecimal();

    private static final int MAX_POOLED_CAPACITY = 64 * 1024;
    private static final int POOL_SIZE;
    private static final AtomicReferenceArray<JsonWriterContext> POOL;

    static {
        int procs = Runtime.getRuntime().availableProcessors();
        POOL_SIZE = Integer.highestOneBit(procs * 4 - 1) << 1;
        POOL = new AtomicReferenceArray<>(POOL_SIZE);
    }

    public JsonWriterContext(SpecializedCodecRegistry registry) {
        super(registry);
    }

    public static JsonWriterContext acquire(SpecializedCodecRegistry registry) {
        int idx = (int) (Thread.currentThread().threadId() & (POOL_SIZE - 1));
        JsonWriterContext ctx = POOL.getAndSet(idx, null);
        if (ctx != null) {
            ctx.pos = 0;
            ctx.useJsonName = false;
            ctx.registry = registry;
            return ctx;
        }
        return new JsonWriterContext(registry);
    }

    public static void release(JsonWriterContext ctx) {
        if (ctx.buf.length <= MAX_POOLED_CAPACITY) {
            int idx = (int) (Thread.currentThread().threadId() & (POOL_SIZE - 1));
            POOL.lazySet(idx, ctx);
        }
    }
}
