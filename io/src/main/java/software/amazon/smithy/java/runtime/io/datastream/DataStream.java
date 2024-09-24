/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
     * Converts the data stream to an in-memory ByteBuffer, blocking if necessary.
     *
     * <p>A value is returned immediately and no blocking occurs if {@link #hasByteBuffer()} returns true.
     *
     * @return a ByteBuffer.
     * @throws RuntimeException if an error occurs while blocking.
     */
    default ByteBuffer waitForByteBuffer() {
        try {
            return asByteBuffer().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if the DataStream has a readily available ByteBuffer that can be returned from
     * {@link #waitForByteBuffer()} without blocking.
     *
     * @return true if there is a readily available ByteBuffer.
     */
    default boolean hasByteBuffer() {
        return false;
    }

    /**
     * Read the contents of the stream into a ByteBuffer.
     *
     * <p>Note: This will load the entire stream into memory. If {@link #hasKnownLength()} is true,
     * {@link #contentLength()} can be used to know if it is safe.
     *
     * @return the future that contains the read ByteBuffer.
     */
    default CompletableFuture<ByteBuffer> asByteBuffer() {
        if (hasByteBuffer()) {
            return CompletableFuture.completedFuture(waitForByteBuffer());
        }

        var subscriber = HttpResponse.BodySubscribers.ofByteArray();
        var delegate = new HttpBodySubscriberAdapter<>(subscriber);
        subscribe(delegate);
        return subscriber.getBody().thenApply(ByteBuffer::wrap).toCompletableFuture();
    }

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
    default CompletableFuture<InputStream> asInputStream() {
        var subscriber = HttpResponse.BodySubscribers.ofInputStream();
        var delegate = new HttpBodySubscriberAdapter<>(subscriber);
        subscribe(delegate);
        return subscriber.getBody().toCompletableFuture();
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
        if (publisher instanceof DataStream ds) {
            if (ds.contentLength() == contentLength && Objects.equals(ds.contentType(), contentType)) {
                return ds;
            }
            return new WrappedDataStream(ds, contentLength, contentType);
        }
        return new PublisherDataStream(publisher, contentLength, contentType);
    }
}
