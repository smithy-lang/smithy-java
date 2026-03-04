/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Default implementation of EventStreamWriter that encodes events and writes them
 * to an internal EventPipeStream. This event bridges the user facing {@link EventStreamWriter}
 * with the added functionality needed by the protocol implementation.
 *
 * <p>Thread Safety: This class is NOT thread-safe for concurrent writes. Only one
 * thread should call write methods at a time. The underlying EventPipeStream is
 * thread-safe for producer-consumer patterns.
 *
 * @param <IE> the initial event ype
 * @param <T>  the event type
 * @param <F>  tye frame type
 */
final class DefaultEventStreamWriter<IE extends SerializableStruct, T extends SerializableStruct,
        F extends Frame<?>>
        implements ProtocolEventStreamWriter<T, IE, F> {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(DefaultEventStreamWriter.class);
    /**
     * Default timeout to block waiting to write.
     */
    private static final int WRITE_TIMEOUT_MILLIS = 10_000;
    /**
     * This latch is used to ensure that the protocol handler writes the initial event
     * before any other event is written. Protocols that don't require the initial event still have
     * to unlatch the writer by bootstrapping it with a null value.
     */
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    /**
     * Pipes bytes written by this writer to an input stream used
     * to send them over the wire.
     */
    private final EventPipeStream pipeStream;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private EventEncoderFactory<F> encoderFactory;
    private IE initialEvent;
    private EventEncoder<F> eventEncoder;
    private FrameEncoder<F> frameEncoder;
    private volatile Throwable lastError;

    /**
     * Creates a new DefaultEventStreamWriter.
     */
    public DefaultEventStreamWriter() {
        this.pipeStream = new EventPipeStream();
    }

    /**
     * Writes an event to the stream. Blocks until the initial event has been written.
     *
     * @param event the event to write (must not be null)
     * @throws NullPointerException  if event or timeout is null
     * @throws IllegalStateException if the stream is closed
     */
    @Override
    public void write(T event) {
        Objects.requireNonNull(event, "event");
        checkState();

        try {
            LOGGER.debug("write event {}", event);

            // Wait for writer to be fully setup and the initial event to be written.
            if (!readyLatch.await(WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                var error = new TimeoutException("Timed out waiting for writing");
                closeWithError(error);
                throw new RuntimeException(error);
            }

            doWrite(event);
        } catch (InterruptedException e) {
            lastError = e;
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while writing event", e);
        }
    }

    @Override
    public void bootstrap(EventEncoderFactory<F> encoderFactory, IE initialEvent) {
        // Make sure that the protocol handler doesn't call bootstrap twice.
        if (readyLatch.getCount() == 0) {
            throw new IllegalStateException("bootstrap has been already called");
        }
        this.encoderFactory = encoderFactory;
        this.initialEvent = initialEvent;
    }

    @Override
    public void setSigner(FrameProcessor<F> eventSigner) {
        this.encoderFactory = encoderFactory.withFrameProcessor(eventSigner);
        setEventStreamEncodingFactory();
        writeInitialEvent(initialEvent);
    }

    /**
     * Writes the initial event that must precede all other events.
     */
    private void writeInitialEvent(SerializableStruct event) {
        checkState();

        LOGGER.debug("write initial event {}", event);
        try {
            // Some protocols, notably REST, encode their initial event in the
            // http request. Callers pass a null value to allow the writing
            // of regular events to start.
            if (event != null) {
                doWrite(event);
            }
        } finally {
            // Always count down, even if write fails, to unblock waiting threads
            readyLatch.countDown();
            // allow initial event to be garbage collected.
            initialEvent = null;
        }
    }

    /**
     * Sets the event encoders from the factory used to get the event and frame encoders used
     * for encoding the events.
     */
    private void setEventStreamEncodingFactory() {
        this.eventEncoder = encoderFactory.newEventEncoder();
        this.frameEncoder = encoderFactory.newFrameEncoder();
        // No need to hold on to the reference, allow it to be garbage collected
        this.encoderFactory = null;
    }

    /**
     * Performs the actual encoding and writing of an event.
     *
     * @param event the event to write
     * @throws RuntimeException if writing fails
     */
    private void doWrite(SerializableStruct event) {
        // Encode the event to a frame, then to bytes
        var frame = eventEncoder.encode(event);
        var encoded = frameEncoder.encode(frame);

        // Write to the stream (may block if queue is full)
        pipeStream.write(encoded);
    }

    /**
     * Checks if the stream has been closed or if there has been a previous error.
     *
     * @throws IllegalStateException if closed
     */
    private void checkState() {
        if (lastError != null) {
            throw new IllegalStateException("Producer failed", lastError);
        }
        if (closed.get()) {
            throw new IllegalStateException("EventStreamWriter is closed");
        }
    }

    @Override
    public EventStreamReader<T> asReader() {
        throw new UnsupportedOperationException(
                "This writer cannot be converted to a reader");
    }

    @Override
    public EventStreamWriter<T> asWriter() {
        return this;
    }

    /**
     * Closes the stream with an error, signaling to the consumer that an error occurred.
     *
     * @param e the error that occurred (must not be null)
     */
    @Override
    public void closeWithError(Exception e) {
        Objects.requireNonNull(e, "exception");
        if (closed.compareAndSet(false, true)) {
            pipeStream.completeWithError(e);
            lastError = e;
        }
    }

    /**
     * Closes the stream normally, signaling to the consumer that no more events will be written.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // Check if the event encoder requires to send a closing frame, and if so encode it and send
            // it before closing the stream. The closing frame is defined by the protocol and/or the
            // auth scheme used to sign the frames, e.g., SigV4 requires to send an empty trailing frame.
            var frame = eventEncoder.closingFrame();
            if (frame != null) {
                var encoded = frameEncoder.encode(frame);
                pipeStream.write(encoded);
            }
            pipeStream.complete();
        }
    }

    /**
     * Returns the DataStream that is used to read the bytes from the
     * encoded events written to this writer.
     *
     * @return the data stream to read encoded event from
     */
    @Override
    public DataStream toDataStream() {
        return DataStream.ofInputStream(pipeStream);
    }
}
