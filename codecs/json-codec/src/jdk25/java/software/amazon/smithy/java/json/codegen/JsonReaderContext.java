/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.util.concurrent.atomic.AtomicReferenceArray;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.json.smithy.JsonParseState;

/**
 * Mutable read context passed to generated deserializers.
 * Extends {@link JsonParseState} to provide parsing result storage,
 * and carries the input buffer, position, end, and codec registry.
 *
 * <p>Fields are public for direct access from generated code.
 * Instances are pooled via striped pooling to avoid allocation.
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public final class JsonReaderContext extends JsonParseState {

    public byte[] buf;
    public int pos;
    public int end;
    public boolean useJsonName;
    public SpecializedCodecRegistry registry;
    public software.amazon.smithy.java.json.JsonSettings jsonSettings;

    private static final int POOL_SIZE;
    private static final AtomicReferenceArray<JsonReaderContext> POOL;

    static {
        int procs = Runtime.getRuntime().availableProcessors();
        POOL_SIZE = Integer.highestOneBit(procs * 4 - 1) << 1;
        POOL = new AtomicReferenceArray<>(POOL_SIZE);
    }

    public JsonReaderContext(byte[] buf, int pos, int end, SpecializedCodecRegistry registry) {
        this.buf = buf;
        this.pos = pos;
        this.end = end;
        this.registry = registry;
    }

    public static JsonReaderContext acquire(byte[] buf, int pos, int end, SpecializedCodecRegistry registry) {
        int idx = (int) (Thread.currentThread().threadId() & (POOL_SIZE - 1));
        JsonReaderContext ctx = POOL.getAndSet(idx, null);
        if (ctx != null) {
            ctx.buf = buf;
            ctx.pos = pos;
            ctx.end = end;
            ctx.registry = registry;
            ctx.parsedString = null;
            return ctx;
        }
        return new JsonReaderContext(buf, pos, end, registry);
    }

    public static void release(JsonReaderContext ctx) {
        ctx.buf = null;
        ctx.parsedString = null;
        int idx = (int) (Thread.currentThread().threadId() & (POOL_SIZE - 1));
        POOL.lazySet(idx, ctx);
    }
}
