/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Per-stream inbound body state backed by borrowed DATA-frame buffers.
 *
 * <p>The connection/reader side offers borrowed DATA-frame buffers directly to this queue.
 * The response consumer side takes them and, when fully consumed, returns the
 * underlying pooled buffer and releases flow-control credit through the supplied
 * releaser.
 */
final class H2StreamBody {
    interface ChunkReleaser {
        int release(ByteBuffer buffer, int flowControlBytes);
    }

    static final class ChunkSlot {
        ByteBuffer data;
        int flowControlBytes;

        void set(ByteBuffer data, int flowControlBytes) {
            this.data = data;
            this.flowControlBytes = flowControlBytes;
        }

        void clear() {
            this.data = null;
            this.flowControlBytes = 0;
        }
    }

    private final ByteBuffer[] buffers;
    private final int[] flowControlBytes;
    private final ChunkReleaser releaser;
    private int head;
    private int tail;
    private int size;
    private boolean completed;
    private IOException failure;

    H2StreamBody(int capacity, ChunkReleaser releaser) {
        this.buffers = new ByteBuffer[capacity];
        this.flowControlBytes = new int[capacity];
        this.releaser = releaser;
    }

    synchronized int offer(ByteBuffer data, int chunkFlowControlBytes, Consumer<ByteBuffer> onClosed) {
        while (failure == null && !completed && size == buffers.length) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onClosed.accept(data);
                return chunkFlowControlBytes;
            }
        }
        if (failure != null || completed) {
            onClosed.accept(data);
            return chunkFlowControlBytes;
        }
        buffers[tail] = data;
        flowControlBytes[tail] = chunkFlowControlBytes;
        tail = (tail + 1) % buffers.length;
        size++;
        notifyAll();
        return 0;
    }

    synchronized boolean take(ChunkSlot dest) throws IOException {
        while (size == 0 && failure == null && !completed) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for response data", e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        if (size == 0) {
            return false;
        }
        dest.set(buffers[head], flowControlBytes[head]);
        buffers[head] = null;
        flowControlBytes[head] = 0;
        head = (head + 1) % buffers.length;
        size--;
        notifyAll();
        return true;
    }

    synchronized int takeBulk(ChunkSlot[] dest, int maxChunks) throws IOException {
        while (size == 0 && failure == null && !completed) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for response data", e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        if (size == 0) {
            return -1;
        }

        int drained = 0;
        while (drained < maxChunks && size > 0) {
            dest[drained].set(buffers[head], flowControlBytes[head]);
            buffers[head] = null;
            flowControlBytes[head] = 0;
            head = (head + 1) % buffers.length;
            size--;
            drained++;
        }
        notifyAll();
        return drained;
    }

    synchronized void complete() {
        completed = true;
        notifyAll();
    }

    synchronized void fail(IOException error) {
        failure = error;
        notifyAll();
    }

    synchronized boolean isEmpty() {
        return size == 0;
    }

    synchronized int close() {
        completed = true;
        int released = 0;
        while (size > 0) {
            ByteBuffer data = buffers[head];
            int chunkFlowControlBytes = flowControlBytes[head];
            buffers[head] = null;
            flowControlBytes[head] = 0;
            head = (head + 1) % buffers.length;
            size--;
            released += releaser.release(data, chunkFlowControlBytes);
        }
        notifyAll();
        return released;
    }
}
