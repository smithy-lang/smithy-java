/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Mutable output buffer context for generated serializers.
 * Fields are public for direct access from generated code (JIT promotes to registers).
 *
 * <p>Instances are pooled via thread-local striped pooling to avoid allocation.
 */
@SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"},
        justification = "Public fields intentional for JIT optimization in generated code")
public final class WriterContext {

    public byte[] buf;
    public int pos;
    public SpecializedCodecRegistry registry;

    private static final int INITIAL_CAPACITY = 512;
    private static final int MAX_POOLED_CAPACITY = 64 * 1024;
    private static final int POOL_SIZE;
    private static final AtomicReferenceArray<WriterContext> POOL;

    static {
        int procs = Runtime.getRuntime().availableProcessors();
        POOL_SIZE = Integer.highestOneBit(procs * 4 - 1) << 1;
        POOL = new AtomicReferenceArray<>(POOL_SIZE);
    }

    public WriterContext(SpecializedCodecRegistry registry) {
        this.buf = new byte[INITIAL_CAPACITY];
        this.pos = 0;
        this.registry = registry;
    }

    public void ensureCapacity(int needed) {
        if (pos + needed > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + needed));
        }
    }

    /**
     * Sets pos, ensures capacity, and returns buf. Reduces the 3-line pattern
     * {@code ctx.pos = pos; ctx.ensureCapacity(N); buf = ctx.buf; pos = ctx.pos;}
     * to {@code buf = ctx.ensure(pos, N); pos = ctx.pos;}.
     */
    public byte[] ensure(int pos, int needed) {
        this.pos = pos;
        if (pos + needed > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + needed));
        }
        return buf;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, pos);
    }

    public static WriterContext acquire(SpecializedCodecRegistry registry) {
        int idx = (int) (Thread.currentThread().threadId() & (POOL_SIZE - 1));
        WriterContext ctx = POOL.getAndSet(idx, null);
        if (ctx != null) {
            ctx.pos = 0;
            ctx.registry = registry;
            return ctx;
        }
        return new WriterContext(registry);
    }

    public static void release(WriterContext ctx) {
        if (ctx.buf.length <= MAX_POOLED_CAPACITY) {
            int idx = (int) (Thread.currentThread().threadId() & (POOL_SIZE - 1));
            POOL.lazySet(idx, ctx);
        }
    }
}
