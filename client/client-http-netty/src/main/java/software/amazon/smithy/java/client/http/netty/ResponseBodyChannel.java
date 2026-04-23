/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Blocking {@link InputStream} that receives {@link ByteBuf} chunks from a Netty event loop
 * and delivers bytes to a caller (virtual) thread with a single-slot inline-handoff fast path
 * plus an unbounded fallback deque.
 *
 * <p>Fast path: when the caller is parked in {@link #read(byte[], int, int)}, the producer
 * copies bytes directly into the caller's buffer, bypassing a queue operation and the extra
 * {@code ByteBuf}→{@code byte[]} copy at the consumer. Backpressure is applied by toggling
 * Netty's autoRead when the fallback deque crosses configured watermarks.
 *
 * <p>Threading contract:
 * <ul>
 *   <li>Producer ({@link #publish}, {@link #publishEos}, {@link #publishError}) is called only
 *       from the Netty event loop for the owning stream, and never blocks.</li>
 *   <li>Consumer ({@link #read}, {@link #close}) is called only from the owning caller thread.</li>
 * </ul>
 *
 * <p>State machine: a {@code VarHandle}-backed int transitions
 * IDLE → WAITING → (HANDED_OFF | IDLE) → IDLE. CLOSED is terminal.
 */
final class ResponseBodyChannel extends InputStream {

    private static final ByteBuf EOS = Unpooled.EMPTY_BUFFER;
    private static final int IDLE = 0;
    private static final int WAITING = 1;
    private static final int HANDED_OFF = 2;
    private static final int CLOSED = 3;

    private static final int PENDING_READ_UNSET = -1;

    private static final VarHandle STATE;
    private static final VarHandle PENDING_READ;
    static {
        try {
            var l = MethodHandles.lookup();
            STATE = l.findVarHandle(ResponseBodyChannel.class, "state", int.class);
            PENDING_READ = l.findVarHandle(ResponseBodyChannel.class, "pendingRead", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Fallback deque (guarded by `this`). Unbounded; backpressure via autoRead.
    private final Deque<ByteBuf> fallback = new ArrayDeque<>();

    private final AtomicReference<Throwable> error;
    private final Consumer<Boolean> autoReadToggle; // true = resume reads, false = pause
    private final Runnable onClose;
    private final int highWater;
    private final int lowWater;

    @SuppressWarnings("unused") // via VarHandle
    private volatile int state = IDLE;

    // Handoff slot.
    // pendingBuf/off/len written by consumer before state=WAITING and read by producer after
    // it CASes WAITING→HANDED_OFF. pendingRead is written by producer after the byte copy and
    // before returning; read by consumer after observing state=HANDED_OFF.
    private byte[] pendingBuf;
    private int pendingOff;
    private int pendingLen;
    @SuppressWarnings("unused") // via VarHandle
    private volatile int pendingRead = PENDING_READ_UNSET;
    private volatile Thread consumerThread;

    // Consumer-private
    private ByteBuf current;
    private boolean eos;
    private boolean closedLocal;

    // Producer-private view of autoRead state to avoid redundant toggles
    private boolean autoReadPaused;

    ResponseBodyChannel(
            AtomicReference<Throwable> error,
            Consumer<Boolean> autoReadToggle,
            Runnable onClose,
            int highWater,
            int lowWater
    ) {
        this.error = error;
        this.autoReadToggle = autoReadToggle;
        this.onClose = onClose;
        this.highWater = Math.max(1, highWater);
        this.lowWater = Math.max(0, Math.min(lowWater, this.highWater - 1));
    }

    // ---- Producer API (event loop; never blocks) ----

    /**
     * Publish a body chunk. Takes ownership of {@code buf} (releases on consumption/close).
     */
    void publish(ByteBuf buf) {
        if (!buf.isReadable()) {
            buf.release();
            return;
        }
        while (true) {
            int s = (int) STATE.getOpaque(this);
            if (s == CLOSED) {
                buf.release();
                return;
            }
            if (s == WAITING && STATE.compareAndSet(this, WAITING, HANDED_OFF)) {
                // We have exclusive access to pendingBuf now. Copy, then publish pendingRead.
                int n = Math.min(buf.readableBytes(), pendingLen);
                buf.readBytes(pendingBuf, pendingOff, n);
                PENDING_READ.setRelease(this, n); // publish count; pairs with consumer's getAcquire
                Thread t = consumerThread;
                if (buf.isReadable()) {
                    enqueue(buf);
                } else {
                    buf.release();
                }
                LockSupport.unpark(t);
                return;
            }
            if (s == IDLE || s == HANDED_OFF) {
                enqueue(buf);
                return;
            }
            // s == WAITING and CAS failed — lost race (can happen if consumer cancelled); retry.
        }
    }

    /** Publish end-of-stream. */
    void publishEos() {
        enqueueEos();
        wakeWaiter();
    }

    /** Publish terminal error. */
    void publishError(Throwable cause) {
        error.compareAndSet(null, cause);
        enqueueEos();
        wakeWaiter();
    }

    private void enqueue(ByteBuf buf) {
        synchronized (this) {
            fallback.add(buf);
            if (!autoReadPaused && fallback.size() >= highWater) {
                autoReadPaused = true;
                // Submit while holding the monitor so submission order matches state-transition order.
                autoReadToggle.accept(false);
            }
        }
    }

    private void enqueueEos() {
        synchronized (this) {
            fallback.add(EOS);
        }
    }

    private void wakeWaiter() {
        if (STATE.compareAndSet(this, WAITING, IDLE)) {
            LockSupport.unpark(consumerThread);
        }
    }

    // ---- Consumer API ----

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : (one[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0)
            return 0;
        if (closedLocal)
            throw new IOException("Stream closed");

        if (current != null && current.isReadable()) {
            return copyFromCurrent(b, off, len);
        }
        releaseCurrent();

        while (true) {
            if (eos) {
                Throwable t = error.get();
                if (t != null)
                    throw new IOException("Response stream failed", t);
                return -1;
            }

            ByteBuf next = pollFallback();
            if (next != null) {
                if (next == EOS) {
                    eos = true;
                    continue;
                }
                current = next;
                return copyFromCurrent(b, off, len);
            }

            // Arm handoff.
            pendingBuf = b;
            pendingOff = off;
            pendingLen = len;
            PENDING_READ.setRelease(this, PENDING_READ_UNSET);
            consumerThread = Thread.currentThread();
            STATE.setRelease(this, WAITING);

            // Close race with a producer that published immediately before we transitioned to WAITING.
            ByteBuf raced = pollFallback();
            if (raced != null) {
                if (STATE.compareAndSet(this, WAITING, IDLE)) {
                    clearPending();
                    if (raced == EOS) {
                        eos = true;
                        continue;
                    }
                    current = raced;
                    return copyFromCurrent(b, off, len);
                }
                // CAS failed. Either a producer did a handoff (state=HANDED_OFF) or
                // publishEos/publishError wakeWaiter'd us (state=IDLE).
                int after = (int) STATE.getAcquire(this);
                if (after == HANDED_OFF) {
                    int n = awaitPendingRead();
                    clearPending();
                    STATE.setRelease(this, IDLE);
                    if (raced == EOS) {
                        eos = true;
                    } else {
                        current = raced;
                    }
                    maybeResumeAutoRead();
                    return n;
                }
                // state is IDLE (wakeWaiter) or CLOSED. Handle `raced` as a normal poll result.
                clearPending();
                if (after == CLOSED) {
                    if (raced != EOS) {
                        raced.release();
                    }
                    throw new IOException("Stream closed");
                }
                if (raced == EOS) {
                    eos = true;
                    continue;
                }
                current = raced;
                return copyFromCurrent(b, off, len);
            }

            // Park until handoff, EOS, or close.
            while ((int) STATE.getAcquire(this) == WAITING) {
                if (closedLocal) {
                    clearPending();
                    throw new IOException("Stream closed");
                }
                LockSupport.park(this);
            }

            int s = (int) STATE.getAcquire(this);
            if (s == HANDED_OFF) {
                int n = awaitPendingRead();
                clearPending();
                STATE.setRelease(this, IDLE);
                maybeResumeAutoRead();
                return n;
            }
            if (s == CLOSED) {
                clearPending();
                throw new IOException("Stream closed");
            }
            // IDLE: producer enqueued/EOS'd and woke us. Loop and re-poll.
            clearPending();
        }
    }

    @Override
    public int available() {
        return current == null ? 0 : current.readableBytes();
    }

    @Override
    public void close() {
        if (closedLocal)
            return;
        closedLocal = true;
        STATE.setRelease(this, CLOSED);
        releaseCurrent();
        synchronized (this) {
            while (true) {
                ByteBuf b = fallback.poll();
                if (b == null)
                    break;
                if (b != EOS)
                    b.release();
            }
        }
        if (onClose != null) {
            try {
                onClose.run();
            } catch (RuntimeException ignored) {}
        }
    }

    // ---- Internals ----

    private int awaitPendingRead() {
        // Producer writes PENDING_READ immediately after the successful CAS; brief spin expected.
        // If it doesn't appear within a bounded budget, something went wrong — fail loudly rather
        // than spin forever.
        int n;
        int spins = 0;
        while ((n = (int) PENDING_READ.getAcquire(this)) == PENDING_READ_UNSET) {
            if (++spins > 1_000_000) {
                throw new IllegalStateException(
                        "awaitPendingRead spun past budget; state=" + STATE.getAcquire(this)
                                + " consumerThread=" + consumerThread);
            }
            Thread.onSpinWait();
        }
        return n;
    }

    private ByteBuf pollFallback() {
        ByteBuf b;
        synchronized (this) {
            b = fallback.poll();
            if (b != null && autoReadPaused && fallback.size() <= lowWater) {
                autoReadPaused = false;
                // Submit while holding the monitor so submission order matches state-transition order.
                autoReadToggle.accept(true);
            }
        }
        return b;
    }

    private void maybeResumeAutoRead() {
        synchronized (this) {
            if (autoReadPaused && fallback.size() <= lowWater) {
                autoReadPaused = false;
                autoReadToggle.accept(true);
            }
        }
    }

    private int copyFromCurrent(byte[] b, int off, int len) {
        int n = Math.min(len, current.readableBytes());
        current.readBytes(b, off, n);
        if (!current.isReadable())
            releaseCurrent();
        return n;
    }

    private void releaseCurrent() {
        if (current != null) {
            current.release();
            current = null;
        }
    }

    private void clearPending() {
        pendingBuf = null;
        pendingOff = 0;
        pendingLen = 0;
        consumerThread = null;
    }
}
