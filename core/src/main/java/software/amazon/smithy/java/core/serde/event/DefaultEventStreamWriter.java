/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Default implementation of EventStreamWriter that encodes events and writes them
 * to an internal {@link WriterDataStream}. This bridges the user facing {@link EventStreamWriter}
 * with the added functionality needed by the protocol implementation.
 *
 * <p>Thread Safety: This class is NOT thread-safe for concurrent writes. Only one
 * thread should call write methods at a time. The underlying WriterDataStream is
 * thread-safe for producer-consumer patterns.
 *
 * @param <IE> the initial event type
 * @param <T>  the event type
 * @param <F>  the frame type
 */
final class DefaultEventStreamWriter<IE extends SerializableStruct, T extends SerializableStruct,
        F extends Frame<?>>
        implements ProtocolEventStreamWriter<T, IE, F> {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(DefaultEventStreamWriter.class);
    /**
     * This latch is used to ensure that the protocol handler writes the initial event
     * before any other event is written. Protocols that don't require the initial event still have
     * to unlatch the writer by bootstrapping it with a null value.
     */
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    private final WriterDataStream dataStream;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private EventEncoder<F> eventEncoder;
    private FrameEncoder<F> frameEncoder;
    private volatile Throwable lastError;

    /**
     * Creates a new DefaultEventStreamWriter.
     */
    public DefaultEventStreamWriter() {
        this.dataStream = new WriterDataStream();
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
            readyLatch.await();

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
        setEventStreamEncodingFactory(Objects.requireNonNull(encoderFactory, "encoderFactory"));
        dataStream.setContentType(encoderFactory.contentType());
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
        }
    }

    /**
     * Sets the event encoder factory used to get the event and frame encoders used
     * for encoding the events.
     *
     * @param factory the event encoder factory
     */
    private void setEventStreamEncodingFactory(EventEncoderFactory<F> factory) {
        Objects.requireNonNull(factory, "eventEncoderFactory");
        this.eventEncoder = factory.newEventEncoder();
        this.frameEncoder = factory.newFrameEncoder();
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

        // Write to the data stream (may block via rendezvous)
        dataStream.put(encoded);
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
            dataStream.completeWithError(e);
            lastError = e;
        }
    }

    /**
     * Closes the stream normally, signaling to the consumer that no more events will be written.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            dataStream.complete();
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
        return dataStream;
    }

    /**
     * A DataStream implementation that bridges event writes to a Flow.Publisher
     * using a zero-buffer SynchronousQueue rendezvous.
     *
     * <p>This eliminates the intermediate EventPipeStream and InputStream layers
     * by handing ByteBuffers directly to the HttpClient's reactive subscriber.
     */
    private static final class WriterDataStream implements DataStream {
        private static final ByteBuffer POISON_PILL = ByteBuffer.allocate(0);

        private final SynchronousQueue<ByteBuffer> queue = new SynchronousQueue<>();
        private volatile String contentType;
        private volatile boolean completed = false;
        private volatile Throwable lastError = null;

        void setContentType(String contentType) {
            this.contentType = contentType;
        }

        /**
         * Puts a ByteBuffer into the rendezvous queue, blocking until a consumer takes it.
         */
        void put(ByteBuffer buffer) {
            try {
                queue.put(buffer);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while writing", e);
            }
        }

        /**
         * Signals normal completion of the stream.
         */
        void complete() {
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
         * Signals error completion of the stream.
         */
        void completeWithError(Throwable error) {
            Objects.requireNonNull(error, "error must not be null");
            if (completed) {
                return;
            }
            this.lastError = error;
            complete();
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public boolean isReplayable() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private final AtomicBoolean started = new AtomicBoolean(false);
                private volatile boolean cancelled = false;

                @Override
                public void request(long n) {
                    if (n <= 0) {
                        subscriber.onError(new IllegalArgumentException("non-positive subscription request"));
                        return;
                    }
                    // Start the draining thread on first request
                    if (started.compareAndSet(false, true)) {
                        Thread.ofVirtual().name("event-stream-publisher").start(() -> drain(subscriber));
                    }
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }

                private void drain(Flow.Subscriber<? super ByteBuffer> sub) {
                    try {
                        while (!cancelled) {
                            ByteBuffer buf = queue.take();
                            if (buf == POISON_PILL) {
                                if (lastError != null) {
                                    sub.onError(new IOException("Producer failed", lastError));
                                } else {
                                    sub.onComplete();
                                }
                                return;
                            }
                            sub.onNext(buf);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        sub.onError(e);
                    }
                }
            });
        }

        @Override
        public InputStream asInputStream() {
            return new InputStream() {
                private ByteBuffer current = null;
                private boolean eof = false;

                @Override
                public int read() throws IOException {
                    if (!ensureCurrent()) {
                        return -1;
                    }
                    return current.get() & 0xFF;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    Objects.checkFromIndexSize(off, len, b.length);
                    if (len == 0) {
                        return 0;
                    }
                    if (!ensureCurrent()) {
                        return -1;
                    }
                    int available = Math.min(len, current.remaining());
                    current.get(b, off, available);
                    if (!current.hasRemaining()) {
                        current = null;
                    }
                    return available;
                }

                private boolean ensureCurrent() throws IOException {
                    if (eof) {
                        return false;
                    }
                    if (current == null || !current.hasRemaining()) {
                        try {
                            current = queue.take();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while reading", e);
                        }
                        if (current == POISON_PILL) {
                            eof = true;
                            if (lastError != null) {
                                throw new IOException("Producer failed", lastError);
                            }
                            return false;
                        }
                    }
                    return true;
                }
            };
        }
    }
}
