/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Abstraction for reading streams of data.
 */
public interface DataStream extends Flow.Publisher<ByteBuffer> {
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

    @Override
    default void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        HttpRequest.BodyPublishers.ofInputStream(this::asInputStream).subscribe(subscriber);
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
     * <p>This might be necessary if the payload of a request is streaming, but an HTTP response gave a Content-Length.
     *
     * @param ds The DataStream to wrap, if necessary.
     * @param contentType Content-Type to associate with the stream. Can be null to not attempt to alter it.
     * @param contentLength Content length of the stream. Can be null to not attempt to alter it.
     * @param isReplayable True if the publisher can be replayed. Can be null to not attempt to alter it.
     * @return the wrapped DataStream.
     */
    static DataStream withMetadata(DataStream ds, String contentType, Long contentLength, Boolean isReplayable) {
        boolean isChanged = false;
        var changedContentType = ds.contentType();
        var changedContentLength = ds.contentLength();
        var changedIsReplayable = ds.isReplayable();

        if (contentType != null && !Objects.equals(contentType, ds.contentType())) {
            isChanged = true;
            changedContentType = contentType;
        }

        if (contentLength != null && !Objects.equals(contentLength, ds.contentLength())) {
            isChanged = true;
            changedContentLength = contentLength;
        }

        if (isReplayable != null && !Objects.equals(isReplayable, ds.isReplayable())) {
            isChanged = true;
            changedIsReplayable = isReplayable;
        }

        return isChanged
                ? new WrappedDataStream(ds, changedContentLength, changedContentType, changedIsReplayable)
                : ds;
    }
}
