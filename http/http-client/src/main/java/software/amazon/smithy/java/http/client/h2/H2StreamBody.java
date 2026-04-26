/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Per-stream inbound body state backed by borrowed DATA-frame buffers.
 *
 * <p>The connection/reader side offers {@link DataChunk}s directly to this queue.
 * The response consumer side takes them and, when fully consumed, returns the
 * underlying pooled buffer and releases flow-control credit through the supplied
 * releaser.
 */
final class H2StreamBody {
    private final DataChunk[] elements;
    private final ToIntFunction<DataChunk> releaser;
    private int head;
    private int tail;
    private int size;
    private boolean completed;
    private IOException failure;

    H2StreamBody(int capacity, ToIntFunction<DataChunk> releaser) {
        this.elements = new DataChunk[capacity];
        this.releaser = releaser;
    }

    synchronized void offer(DataChunk chunk, Consumer<DataChunk> onClosed) {
        while (failure == null && !completed && size == elements.length) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onClosed.accept(chunk);
                return;
            }
        }
        if (failure != null || completed) {
            onClosed.accept(chunk);
            return;
        }
        elements[tail] = chunk;
        tail = (tail + 1) % elements.length;
        size++;
        notifyAll();
    }

    synchronized DataChunk take() throws IOException {
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
            return null;
        }
        DataChunk chunk = elements[head];
        elements[head] = null;
        head = (head + 1) % elements.length;
        size--;
        notifyAll();
        return chunk;
    }

    synchronized int takeBulk(DataChunk[] dest, int maxChunks) throws IOException {
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
            DataChunk chunk = elements[head];
            elements[head] = null;
            head = (head + 1) % elements.length;
            size--;
            dest[drained++] = chunk;
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
            DataChunk chunk = elements[head];
            elements[head] = null;
            head = (head + 1) % elements.length;
            size--;
            released += releaser.applyAsInt(chunk);
        }
        notifyAll();
        return released;
    }
}
