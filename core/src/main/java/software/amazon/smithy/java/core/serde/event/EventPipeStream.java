/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Bridges an event stream producer to an InputStream consumer.
 *
 * <p>Thread Safety: The {@link #write(byte[])} and {@link #complete()} methods
 * can be called from one or more producer threads. The {@link #read()} methods
 * must only be called from a single consumer thread.
 *
 * <p>Backpressure: The internal queue has a fixed capacity (default 64). If the
 * queue fills up, {@code write()} will block until the consumer reads data.
 *
 * <p>Termination: The writer must call {@link #complete()} to signal the
 * consumer that the writing has finished. Failing to do so will block the reader
 * indefinitely
 */
final class EventPipeStream extends InputStream {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(EventPipeStream.class);
    /**
     * Default queue size, intentionally small and not configurable. This class is designed
     * to work with virtual threads, so let threads block cheaply rather than
     * buffering excessively.
     */
    private static final int DEFAULT_QUEUE_SIZE = 64;
    /**
     * Poison pill used to signal the end of the stream.
     */
    private static final byte[] POISON_PILL = new byte[0];

    /**
     * A bounded queue to connect the thread making the event writes to the thread
     * consuming them.
     */
    private final BlockingQueue<byte[]> queue;
    private volatile byte[] current = null;
    private volatile int offset = 0;
    private volatile boolean completed = false;
    private volatile Throwable lastError = null;
    private volatile boolean closed = false;

    /**
     * Creates a new EventInputStream with the default queue size of 64.
     */
    public EventPipeStream() {
        this.queue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_SIZE);
    }

    /**
     * Writes a message to the stream. This method blocks if the internal queue is full.
     *
     * @param message the byte array to write (must not be null or empty)
     * @throws NullPointerException     if message is null
     * @throws IllegalArgumentException if message is empty
     * @throws IllegalStateException    if stream is already completed or closed
     * @throws RuntimeException         wrapping InterruptedException if interrupted while waiting
     */
    public void write(byte[] message) {
        Objects.requireNonNull(message, "message");
        if (message.length == 0) {
            throw new IllegalArgumentException("message must not be empty");
        }
        if (completed || closed) {
            throw new IllegalStateException("Stream is already completed or closed");
        }

        LOGGER.debug("Writing event to stream with {} bytes", message.length);

        try {
            queue.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while writing", e);
        }
    }

    /**
     * Signals that the producer has finished successfully. Subsequent reads will
     * return -1 (EOF) once all queued data has been consumed.
     *
     * <p>This method is idempotent, calling it multiple times has no additional effect.
     *
     * @throws RuntimeException wrapping InterruptedException if interrupted
     */
    public void complete() {
        if (completed) {
            return;
        }
        completed = true;

        try {
            queue.put(POISON_PILL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while completing", e);
        }
    }

    /**
     * Signals that the producer encountered an error. Subsequent reads will
     * throw an IOException wrapping the provided error once all queued data
     * has been consumed.
     *
     * @param error the error that occurred (must not be null)
     * @throws NullPointerException if error is null
     */
    public void completeWithError(Throwable error) {
        Objects.requireNonNull(error, "error must not be null");
        if (completed) {
            return;
        }

        this.lastError = error;
        complete();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);

        if (len == 0) {
            return 0;
        }

        LOGGER.debug("Read requested, polling");
        if (!ensureCurrent()) {
            LOGGER.debug("Read requested, EOF reached");
            return -1;
        }

        int available = Math.min(len, current.length - offset);
        System.arraycopy(current, offset, b, off, available);
        advanceOffset(available);
        LOGGER.debug("Read fulfilled, bytes {} (req: {})", available, (len - off));
        return available;
    }

    @Override
    public int read() throws IOException {
        if (!ensureCurrent()) {
            return -1;
        }
        LOGGER.debug("Read requested");
        byte b = current[offset];
        advanceOffset(1);
        return b & 0xFF;
    }

    @Override
    public int available() throws IOException {
        checkError();

        if (current != null && current != POISON_PILL) {
            return current.length - offset;
        }

        return 0;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        // Signal completion if not already done
        if (!completed) {
            complete();
        }

        // Drain the queue to unblock any waiting producers
        queue.clear();
    }

    /**
     * Ensures that {@code current} contains data to read, or returns false if EOF/error.
     *
     * @return true if data is available, false if EOF
     * @throws IOException if an error was signaled via {@link #completeWithError(Throwable)}
     */
    private boolean ensureCurrent() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        if (current == null) {
            try {
                current = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading", e);
            }
        }

        // Check for poison pill (EOF)
        if (current == POISON_PILL) {
            checkError(); // Throw if error was set
            return false;
        }

        return true;
    }

    /**
     * Checks if an error was signaled and throws it if present.
     */
    private void checkError() throws IOException {
        if (lastError != null) {
            throw new IOException("Producer failed", lastError);
        }
    }

    /**
     * Advances the read position and resets state if current buffer is exhausted.
     */
    private void advanceOffset(int consumed) {
        var tmp = offset;
        offset = tmp + consumed;
        if (offset >= current.length) {
            current = null;
            offset = 0;
        }
    }
}
