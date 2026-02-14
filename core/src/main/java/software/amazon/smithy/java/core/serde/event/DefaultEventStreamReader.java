/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Implementation of {@link InternalEventStreamReader} that reads from
 * an InputStream and decodes frames into events of type T. This class extends
 * {@link EventStreamReader} which is be the user facing aspect of it, and
 * adds an {@link #readInitialEvent} method for protocol implementations to be
 * able to read the initial event. If the protocol does not send an initial
 * event, then the class must be constructed passing {@code expectInitialEvent}
 * as false. When {@code expectInitialEvent} is true, the {@link #read()} method
 * will throw {@link IllegalStateException} unless {@link #readInitialEvent()}
 * has been previously called.
 *
 * <p>Thread Safety: This class is NOT thread-safe. Only one thread should call
 * read methods at a time.
 *
 * <p>Frame Handling: The underlying FrameDecoder must be stateful and handle
 * partial frames that span multiple reads from the InputStream. The decoder
 * returns an empty list when it doesn't have enough bytes to construct a
 * complete frame.
 *
 * @param <IE> the initial event type
 * @param <T>  the event type
 * @param <F>  the frame type
 */
final class DefaultEventStreamReader<IE extends SerializableStruct, T extends SerializableStruct, F extends Frame<?>>
        implements InternalEventStreamReader<IE, T> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(DefaultEventStreamReader.class);
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    // We need a queue to keep extra events since the decoder might return more
    // than one event at the time.
    private final Deque<T> queue;
    private final InputStream inputStream;
    private final EventDecoder<F> eventDecoder;
    private final FrameDecoder<F> frameDecoder;
    private volatile boolean inputConsumed = false;
    private volatile boolean closed = false;
    private volatile boolean initialEventConsumed;

    /**
     * Creates a new DefaultEventStreamReader.
     *
     * @param dataStream          the input stream to read from
     * @param eventDecoderFactory factory for creating decoders
     * @param expectInitialEvent  true if the protocol expects an initial event
     * @throws NullPointerException if any parameter is null
     */
    public DefaultEventStreamReader(
            DataStream dataStream,
            EventDecoderFactory<F> eventDecoderFactory,
            boolean expectInitialEvent
    ) {
        this.inputStream = Objects.requireNonNull(dataStream, "dataStream").asInputStream();
        Objects.requireNonNull(eventDecoderFactory, "eventDecoderFactory");

        this.initialEventConsumed = !expectInitialEvent;
        this.queue = new ArrayDeque<>();
        this.eventDecoder = eventDecoderFactory.newEventDecoder();
        this.frameDecoder = eventDecoderFactory.newFrameDecoder();
    }

    /**
     * Reads the next event from the stream. This method blocks until an event is available
     * or the stream ends.
     *
     * @return the next event, or null if end of stream or error occurred
     * @throws IllegalStateException if the reader is closed
     * @throws IllegalStateException if an initial event is expected but not yet read
     * @throws RuntimeException      if a previous read error occurred
     * @throws UncheckedIOException  if an I/O error occurs while reading
     */
    @Override
    public T read() {
        if (closed) {
            throw new IllegalStateException("Reader is closed");
        }
        if (!initialEventConsumed) {
            throw new IllegalStateException("Initial event is expected but readInitialEvent was not called");
        }
        if (inputConsumed) {
            return null;
        }

        if (queue.isEmpty()) {
            if (!enqueueEvents()) {
                LOGGER.debug("No more events available, end of stream reached");
                return null;
            }
        }

        return queue.pollFirst();
    }

    /**
     * Reads the initial response from the event stream.
     *
     * <p>Some protocols encode the initial response as the first event in the stream.
     * This method reads and decodes that first event as an initial response of type IE.
     * Any additional events read during this process are queued for subsequent {@link #read} calls.
     *
     * @return the initial response
     * @throws IllegalStateException if the reader is closed
     * @throws RuntimeException      if end of stream is reached before getting initial response
     * @throws UncheckedIOException  if an I/O error occurs
     * @throws ClassCastException    if the first event cannot be cast to type IR
     */
    @SuppressWarnings("unchecked")
    public IE readInitialEvent() {
        if (closed) {
            throw new IllegalStateException("Reader is closed");
        }

        if (initialEventConsumed) {
            throw new IllegalStateException("Initial event was not expected of readInitialEvent has been called");
        }

        IE result = null;

        while (result == null) {
            try {
                int read = inputStream.read(buffer);

                if (read == -1) {
                    inputConsumed = true;
                    throw new RuntimeException("Unexpected end of stream while reading initial event");
                }

                LOGGER.debug("Read {} bytes for initial event", read);
                if (read == 0) {
                    continue;
                }

                // Decode frames from the bytes read
                // Note: FrameDecoder is stateful and handles partial frames
                var frames = frameDecoder.decode(ByteBuffer.wrap(buffer, 0, read));

                if (frames.isEmpty()) {
                    // Partial frame, need more data
                    LOGGER.debug("No complete frames decoded, reading more data");
                    continue;
                }

                // First frame is the initial response
                result = (IE) eventDecoder.decodeInitialEvent(frames.getFirst(), this);

                // Queue any additional frames as regular events
                for (var idx = 1; idx < frames.size(); idx++) {
                    T event = (T) eventDecoder.decode(frames.get(idx));
                    queue.add(event);
                }
            } catch (IOException e) {
                closeWithError(e);
                throw new UncheckedIOException("I/O error reading initial event", e);
            } catch (ClassCastException e) {
                closeWithError(e);
                throw new RuntimeException("Initial event type mismatch", e);
            }
        }
        initialEventConsumed = true;
        return result;
    }

    @Override
    public EventStreamReader<T> asReader() {
        return this;
    }

    @Override
    public EventStreamWriter<T> asWriter() {
        throw new UnsupportedOperationException("This reader cannot be used as a writer");
    }

    /**
     * Closes the underlying input stream and releases resources.
     *
     * @throws UncheckedIOException if an I/O error occurs while closing
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        inputConsumed = true;
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Error closing input stream", e);
        } finally {
            queue.clear();
        }
    }

    /**
     * Closes the reader due to an error condition.
     *
     * @param e the error that caused the closure
     * @throws NullPointerException if e is null
     */
    @Override
    public void closeWithError(Exception e) {
        Objects.requireNonNull(e, "exception must not be null");

        if (closed) {
            return;
        }

        LOGGER.error("Closing reader due to error", e);
        close();
    }

    /**
     * Reads from the input stream and enqueues decoded events.
     *
     * @return true if events were enqueued, false if end of stream
     * @throws UncheckedIOException if an I/O error occurs
     */
    @SuppressWarnings("unchecked")
    private boolean enqueueEvents() {
        if (inputConsumed) {
            return false;
        }

        try {
            // Read until we have at least one event in the queue
            while (queue.isEmpty()) {
                LOGGER.debug("Reading from input stream to enqueue events");

                int read = inputStream.read(buffer);

                if (read == -1) {
                    LOGGER.debug("End of stream reached");
                    inputConsumed = true;
                    return false;
                }

                LOGGER.debug("Read {} bytes from input stream", read);
                if (read == 0) {
                    continue;
                }

                // Decode frames from the bytes read
                // FrameDecoder is stateful, accumulates bytes until complete frames available
                List<F> frames = frameDecoder.decode(ByteBuffer.wrap(buffer, 0, read));

                // Decode each frame into an event and add to queue
                for (F frame : frames) {
                    T event = (T) eventDecoder.decode(frame);
                    queue.add(event);
                }
            }

            return true;

        } catch (IOException e) {
            closeWithError(e);
            throw new UncheckedIOException("I/O error reading events", e);
        } catch (ClassCastException e) {
            closeWithError(e);
            throw new RuntimeException("Event type mismatch during decode", e);
        }
    }
}
