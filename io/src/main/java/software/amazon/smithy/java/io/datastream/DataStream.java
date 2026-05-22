/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * Abstraction for reading streams of data.
 */
public interface DataStream extends Flow.Publisher<ByteBuffer>, AutoCloseable {
    /**
     * Length of the data stream, if known.
     *
     * <p>Return a negative number to indicate an unknown length.
     *
     * @return Returns the content length if known, or a negative number if unknown.
     */
    long contentLength();

    /**
     * Check if the stream has a known content-length.
     *
     * @return true if the length is known.
     */
    default boolean hasKnownLength() {
        return contentLength() >= 0;
    }

    /**
     * Returns the content-type of the data, if known.
     *
     * @return the optionally available content-type, or null if not known.
     */
    String contentType();

    /**
     * Check if the DataStream can be restarted from the beginning when new subscribers are added or when getting
     * the data as an InputStream or ByteBuffer.
     *
     * <p>This information can be used to make better decisions about whether an optional process should read all the
     * data stored in a DataStream (e.g., optionally signing the payload of a request, knowing if it's possible to
     * retry a failed request, etc.).
     *
     * @return true if the data is replayable.
     */
    boolean isReplayable();

    /**
     * Check if the DataStream is available for consumption.
     *
     * <p>A stream is available if it either hasn't been consumed yet, or if it is replayable. This is useful for
     * interceptors that need to make decisions based on whether the stream data can be accessed without causing
     * an error. In other worse, if this method returns {@code true}, then calling {@link #asInputStream()} will
     * succeed.
     *
     * <p>Note: This method returning {@code true} does not guarantee that reading will succeed (e.g., I/O errors
     * can still occur), only that the stream has not been previously consumed in a non-replayable manner.
     *
     * @return true if the stream can be consumed.
     */
    boolean isAvailable();

    /**
     * Convert the stream into a blocking {@link InputStream}.
     *
     * @apiNote To ensure that all resources associated with the corresponding exchange are properly released, the
     * caller must ensure to either read all bytes until EOF is reached, or call {@link InputStream#close} if it is
     * unable or unwilling to do so. Calling {@code close} before exhausting the stream may cause the underlying
     * connection to be closed and prevent it from being reused for subsequent operations.
     *
     * @return Returns the future that contains the blocking {@code InputStream}.
     */
    InputStream asInputStream();

    /**
     * Write the contents of this stream to the given output stream.
     *
     * <p>This is the preferred way to transfer data from a DataStream to an OutputStream.
     * Implementations may override this to avoid intermediate InputStream allocation
     * (e.g., writing directly from a byte array or ByteBuffer).
     *
     * @param out the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    default void writeTo(OutputStream out) throws IOException {
        try (var is = asInputStream()) {
            is.transferTo(out);
        }
    }

    /**
     * Read the contents of the stream into a ByteBuffer by reading all bytes from {@link #asInputStream()}.
     *
     * <p>Note: This will load the entire stream into memory. If {@link #hasKnownLength()} is true,
     * {@link #contentLength()} can be used to know if it is safe.
     *
     * @return the future that contains the read ByteBuffer.
     */
    default ByteBuffer asByteBuffer() {
        try (var is = asInputStream()) {
            return ByteBuffer.wrap(is.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Indicates whether this stream already has a stable in-memory {@link ByteBuffer} representation.
     *
     * <p>When this returns {@code true}, calling {@link #asByteBuffer()} must not require materializing or reading
     * the underlying stream contents. This is stronger than merely supporting {@code asByteBuffer()}, since the
     * default implementation always materializes the stream into memory.
     *
     * @return true if {@link #asByteBuffer()} is backed by an already-available in-memory buffer.
     */
    default boolean hasByteBuffer() {
        return false;
    }

    @Override
    default void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        HttpRequest.BodyPublishers.ofInputStream(this::asInputStream).subscribe(subscriber);
    }

    /**
     * Closes any underlying resources associated with this data stream.
     *
     * <p>The default implementation does nothing. Implementations that hold closeable resources (e.g., input streams)
     * should override this method to release them.
     *
     * <p>It is safe to call this method multiple times.
     */
    @Override
    default void close() {
        // Default no-op. Implementations holding closeable resources should override.
    }

    /**
     * Create an empty DataStream.
     *
     * @return the empty DataStream.
     */
    static DataStream ofEmpty() {
        return EmptyDataStream.INSTANCE;
    }

    /**
     * Create a DataStream from an InputStream.
     *
     * @param inputStream InputStream to wrap.
     * @return the created DataStream.
     */
    static DataStream ofInputStream(InputStream inputStream) {
        return ofInputStream(inputStream, null);
    }

    /**
     * Create a DataStream from an InputStream.
     *
     * @param inputStream InputStream to wrap.
     * @param contentType Content-Type of the stream if known, or null.
     * @return the created DataStream.
     */
    static DataStream ofInputStream(InputStream inputStream, String contentType) {
        return ofInputStream(inputStream, contentType, -1);
    }

    /**
     * Create a DataStream from an InputStream.
     *
     * @param inputStream InputStream to wrap.
     * @param contentType Content-Type of the stream if known, or null.
     * @param contentLength Bytes in the stream if known, or -1.
     * @return the created DataStream.
     */
    static DataStream ofInputStream(InputStream inputStream, String contentType, long contentLength) {
        return new InputStreamDataStream(inputStream, contentType, contentLength);
    }

    /**
     * Create a DataStream from an in-memory UTF-8 string.
     *
     * @param data Data to stream.
     * @return the created DataStream.
     */
    static DataStream ofString(String data) {
        return ofString(data, null);
    }

    /**
     * Create a DataStream from an in-memory UTF-8 string.
     *
     * @param data        Data to stream.
     * @param contentType Content-Type of the data if known, or null.
     * @return the created DataStream.
     */
    static DataStream ofString(String data, String contentType) {
        return ofBytes(data.getBytes(StandardCharsets.UTF_8), contentType);
    }

    /**
     * Create a DataStream from an in-memory byte array.
     *
     * @param bytes Bytes to read.
     * @return the created DataStream.
     */
    static DataStream ofBytes(byte[] bytes) {
        return ofBytes(bytes, null);
    }

    /**
     * Create a DataStream from an in-memory byte array.
     *
     * @param bytes Bytes to read.
     * @param contentType Content-Type of the data, if known.
     * @return the created DataStream.
     */
    static DataStream ofBytes(byte[] bytes, String contentType) {
        return new ByteBufferDataStream(ByteBuffer.wrap(bytes, 0, bytes.length), contentType);
    }

    /**
     * Create a DataStream from an in-memory byte array.
     *
     * @param bytes Bytes to read.
     * @param offset Starting position.
     * @param length Ending position.
     * @return the created DataStream.
     */
    static DataStream ofBytes(byte[] bytes, int offset, int length) {
        return ofBytes(bytes, offset, length, null);
    }

    /**
     * Create a DataStream from an in-memory byte array.
     *
     * @param bytes Bytes to read.
     * @param offset Starting position.
     * @param length Ending position.
     * @param contentType Content-Type of the data, if known.
     * @return the created DataStream.
     */
    static DataStream ofBytes(byte[] bytes, int offset, int length, String contentType) {
        return new ByteBufferDataStream(ByteBuffer.wrap(bytes, offset, length), contentType);
    }

    /**
     * Create a DataStream from a list of in-memory {@link ByteBuffer}s.
     *
     * <p>The buffers are not stitched into a single buffer until {@link #asByteBuffer()} is
     * called, so consumers that subscribe (or write directly to an output stream) avoid the
     * concatenation copy.
     *
     * @param buffers buffers to expose in order.
     * @param contentLength sum of all actual data in each buffer.
     * @param contentType content-type of the data, if known.
     * @return the created DataStream.
     */
    static DataStream ofByteBuffers(List<ByteBuffer> buffers, long contentLength, String contentType) {
        return switch (buffers.size()) {
            case 0 -> EmptyDataStream.INSTANCE;
            case 1 -> ofByteBuffer(buffers.getFirst(), contentType);
            default -> new MultiByteBufferDataStream(buffers, contentLength, contentType);
        };
    }

    /**
     * Create a DataStream from a ByteBuffer.
     *
     * @param buffer Bytes to read.
     * @return the created DataStream.
     */
    static DataStream ofByteBuffer(ByteBuffer buffer) {
        return ofByteBuffer(buffer, null);
    }

    /**
     * Create a DataStream from a ByteBuffer.
     *
     * @param buffer Bytes to read.
     * @param contentType Content-Type of the data, if known.
     * @return the created DataStream.
     */
    static DataStream ofByteBuffer(ByteBuffer buffer, String contentType) {
        return new ByteBufferDataStream(buffer, contentType);
    }

    /**
     * Create a DataStream from a file on disk.
     *
     * <p>This implementation will attempt to probe the content-type of the file using
     * {@link Files#probeContentType(Path)}. To avoid this, call {@link #ofFile(Path, String)} and pass in a null
     * {@code contentType} argument.
     *
     * @param file File to read.
     * @return the created DataStream.
     */
    static DataStream ofFile(Path file) {
        try {
            return ofFile(file, Files.probeContentType(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Create a DataStream from a file on disk.
     *
     * @param file        File to read.
     * @param contentType Content-Type of the data if known, or null.
     * @return the created DataStream.
     */
    static DataStream ofFile(Path file, String contentType) {
        return new FileDataStream(file, contentType);
    }

    /**
     * Creates a DataStream that emits data from a {@link Flow.Publisher}.
     *
     * @param publisher   Publisher to stream.
     * @param contentType Content-Type to associate with the stream. Can be null.
     * @param contentLength Content length of the stream. Use -1 for unknown, and 0 or greater for the byte length.
     * @return the created DataStream.
     */
    static DataStream ofPublisher(Flow.Publisher<ByteBuffer> publisher, String contentType, long contentLength) {
        return ofPublisher(publisher, contentType, contentLength, false);
    }

    /**
     * Creates a DataStream that emits data from a {@link Flow.Publisher}.
     *
     * @param publisher   Publisher to stream.
     * @param contentType Content-Type to associate with the stream. Can be null.
     * @param contentLength Content length of the stream. Use -1 for unknown, and 0 or greater for the byte length.
     * @param isReplayable True if the publisher can start from the beginning when additional subscribers are added.
     * @return the created DataStream.
     */
    static DataStream ofPublisher(
            Flow.Publisher<ByteBuffer> publisher,
            String contentType,
            long contentLength,
            boolean isReplayable
    ) {
        if (publisher instanceof DataStream ds) {
            return withMetadata(ds, contentType, contentLength, isReplayable);
        }
        return new PublisherDataStream(publisher, contentLength, contentType, isReplayable);
    }

    /**
     * Creates DataStream that returns potentially more specific metadata about the stream.
     *
     * <p>Useful when, e.g., the request body publisher reports unknown length but the HTTP
     * response carries an authoritative Content-Length header that should override it.
     *
     * <p>Returns the input {@code ds} unchanged when none of the requested overrides differ
     * from what {@code ds} already reports.
     *
     * @param ds The DataStream to wrap, if necessary.
     * @param contentType Content-Type to associate. Pass null to inherit from {@code ds}.
     * @param contentLength Content-Length to associate. Use {@code -1} to inherit from {@code ds},
     *                      otherwise {@code 0} or greater for the byte length.
     * @return the wrapped DataStream, or {@code ds} unchanged if no override changes anything.
     */
    static DataStream withMetadata(DataStream ds, String contentType, long contentLength) {
        return withMetadata(ds, contentType, contentLength, ds.isReplayable());
    }

    /**
     * Creates a DataStream that exposes potentially-different metadata than the underlying stream.
     *
     * <p>Useful when, e.g., the request body publisher reports unknown length but the HTTP
     * response carries an authoritative Content-Length header that should override it.
     *
     * <p>Returns the input {@code ds} unchanged when none of the requested overrides differ
     * from what {@code ds} already reports.
     *
     * @param ds The DataStream to wrap, if necessary.
     * @param contentType Content-Type to associate. Pass null to inherit from {@code ds}.
     * @param contentLength Content-Length to associate. Use {@code -1} to inherit from {@code ds},
     *                      otherwise {@code 0} or greater for the byte length.
     * @param isReplayable Replayability to associate.
     * @return the wrapped DataStream, or {@code ds} unchanged if no override changes anything.
     */
    static DataStream withMetadata(DataStream ds, String contentType, long contentLength, boolean isReplayable) {
        boolean changeContentType = contentType != null && !contentType.equals(ds.contentType());
        boolean changeContentLength = contentLength >= 0 && contentLength != ds.contentLength();
        boolean changeReplayable = isReplayable != ds.isReplayable();

        if (!changeContentType && !changeContentLength && !changeReplayable) {
            return ds;
        }

        return new WrappedDataStream(
                ds,
                changeContentLength ? contentLength : ds.contentLength(),
                changeContentType ? contentType : ds.contentType(),
                isReplayable);
    }
}
