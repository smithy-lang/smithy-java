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

    private static final software.amazon.smithy.java.json.JsonSettings DEFAULT_SETTINGS =
            software.amazon.smithy.java.json.JsonSettings.builder().build();

    public software.amazon.smithy.java.json.JsonSettings jsonSettings = DEFAULT_SETTINGS;
    public final Schubfach.DoubleToDecimal dtd = Schubfach.createDoubleToDecimal();
    public final Schubfach.FloatToDecimal ftd = Schubfach.createFloatToDecimal();

    private static final int DEFAULT_BUF_SIZE = 512;
    private static final int MAX_POOLED_CAPACITY = DEFAULT_BUF_SIZE * 64;
    private static final int POOL_SLOTS;
    private static final int POOL_MASK;
    private static final AtomicReferenceArray<JsonWriterContext> POOL;
    private static final int MAX_PROBE = 3;

    static {
        int procs = Runtime.getRuntime().availableProcessors();
        int raw = procs * 4;
        POOL_SLOTS = Integer.highestOneBit(raw - 1) << 1;
        POOL_MASK = POOL_SLOTS - 1;
        POOL = new AtomicReferenceArray<>(POOL_SLOTS);
    }

    public JsonWriterContext(SpecializedCodecRegistry registry) {
        super(registry);
    }

    public static JsonWriterContext acquire(SpecializedCodecRegistry registry) {
        if (!Thread.currentThread().isVirtual()) {
            int base = poolProbe();
            for (int i = 0; i < MAX_PROBE; i++) {
                int idx = (base + i) & POOL_MASK;
                JsonWriterContext ctx = POOL.getPlain(idx);
                if (ctx != null && POOL.compareAndExchangeAcquire(idx, ctx, null) == ctx) {
                    ctx.pos = 0;
                    ctx.registry = registry;
                    return ctx;
                }
            }
        }
        return new JsonWriterContext(registry);
    }

    public static void release(JsonWriterContext ctx) {
        if (Thread.currentThread().isVirtual()) {
            return;
        }
        if (ctx.buf.length > MAX_POOLED_CAPACITY) {
            ctx.buf = new byte[DEFAULT_BUF_SIZE];
        }
        int base = poolProbe();
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = (base + i) & POOL_MASK;
            if (POOL.getPlain(idx) == null
                    && POOL.compareAndExchangeRelease(idx, null, ctx) == null) {
                return;
            }
        }
    }

    private static int poolProbe() {
        long id = Thread.currentThread().threadId();
        return (int) (id ^ (id >>> 16)) & POOL_MASK;
    }
}
