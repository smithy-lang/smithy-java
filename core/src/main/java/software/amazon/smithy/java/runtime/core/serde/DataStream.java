/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Abstraction for reading streams of data.
 */
// TODO: Is it ok for the implementation to be dependent on java.net.http.HttpRequest.BodyPublishers?
// TODO: Is it ok for core to have dependency on java.net.http? even after http-api module split
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
     * @return the optionally available content-type.
     */
    Optional<String> contentType();

    // TODO: Does close make sense?
    /**
     * Close underlying resources, if necessary.
     *
     * <p>If the resource is already closed, this method does nothing.
     */
    @Override
    default void close() {
//        try {
//            inputStream().close();
//        } catch (IOException e) {
//            throw new UncheckedIOException("Unable to close input stream in data stream", e);
//        }
    }

//    /**
//     * Read the contents of the stream to an in-memory byte array.
//     *
//     * @param maxLength Maximum number of bytes to read.
//     * @return Returns the in-memory byte array.
//     */
//    // TODO: seems blocking. do we need to remove?
//    default byte[] readToBytes(int maxLength) {
//        try (InputStream stream = inputStream()) {
//            return stream.readNBytes(maxLength);
//        } catch (IOException e) {
//            throw new UncheckedIOException(e);
//        }
//    }
//
//    /**
//     * Read the contents of the stream to an in-memory String.
//     *
//     * @param maxLength Maximum number of bytes to read.
//     * @return Returns the in-memory string.
//     */
//    // TODO: seems blocking. do we need to remove?
//    default String readToString(int maxLength) {
//        return new String(readToBytes(maxLength), StandardCharsets.UTF_8);
//    }

    /**
     * Create an empty DataStream.
     *
     * @return the empty DataStream.
     */
    static DataStream ofEmpty() {
        return ofHttpRequestPublisher(HttpRequest.BodyPublishers.noBody(), null);
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
     * @param inputStream   InputStream to wrap.
     * @param contentType   Content-Type of the stream if known, or null.
     * @param contentLength Bytes in the stream if known, or -1.
     * @return the created DataStream.
     */
    static DataStream ofInputStream(InputStream inputStream, String contentType, long contentLength) {
        return ofPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> inputStream), contentType, contentLength);
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
     * @param bytes       Bytes to read.
     * @param contentType Content-Type of the data if known, or null.
     * @return the created DataStream.
     */
    static DataStream ofBytes(byte[] bytes, String contentType) {
        return ofHttpRequestPublisher(HttpRequest.BodyPublishers.ofByteArray(bytes), contentType);
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
        String contentType;
        try {
            contentType = Files.probeContentType(file);
        } catch (IOException e) {
            contentType = null;
        }
        return ofFile(file, contentType);
    }

    /**
     * Create a DataStream from a file on disk.
     *
     * @param file        File to read.
     * @param contentType Content-Type of the data if known, or null.
     * @return the created DataStream.
     */
    static DataStream ofFile(Path file, String contentType) {
        try {
            return ofHttpRequestPublisher(HttpRequest.BodyPublishers.ofFile(file), contentType);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException("File not found: " + file, e);
        }
    }

    /**
     * Creates a DataStream that emits data from a {@link HttpRequest.BodyPublisher}.
     *
     * @param publisher   HTTP request body publisher to stream.
     * @param contentType Content-Type to associate with the stream. Can be set to null.
     * @return the created DataStream.
     */
    static DataStream ofHttpRequestPublisher(HttpRequest.BodyPublisher publisher, String contentType) {
        return ofPublisher(publisher, contentType, publisher.contentLength());
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
        return new DataStream() {
            @Override
            public long contentLength() {
                return contentLength;
            }

            @Override
            public Optional<String> contentType() {
                return Optional.ofNullable(contentType);
            }

            @Override
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                publisher.subscribe(subscriber);
            }
        };
    }

    /**
     * Transform the stream into another value using the given {@link StreamSubscriber}.
     *
     * @param subscriber Subscriber used to transform the stream.
     * @return the eventually transformed result.
     * @param <T> Value to transform into.
     */
    default <T> CompletionStage<T> transform(StreamSubscriber<T> subscriber) {
        subscribe(subscriber);
        return subscriber.result();
    }

    // TODO: Make these return CompletableFuture directly?
    // TODO: Have sync/async versions of these in DataStream directly. asBytesAsync/asBytesCf and asBytes with join()?
    /**
     * Read the contents of the stream into a byte array.
     *
     * @return the CompletionStage that contains the read byte array.
     */
    default CompletionStage<byte[]> asBytes() {
        return transform(StreamSubscriber.ofByteArray());
    }

    /**
     * Attempts to read the contents of the stream into a UTF-8 string.
     *
     * @return the CompletionStage that contains the string.
     */
    default CompletionStage<String> asString() {
        return transform(StreamSubscriber.ofString());
    }

    /**
     * Convert the stream into a blocking {@link InputStream}.
     *
     * @apiNote To ensure that all resources associated with the corresponding exchange are properly released the
     * caller must ensure to either read all bytes until EOF is reached, or call {@link InputStream#close} if it is
     * unable or unwilling to do so. Calling {@code close} before exhausting the stream may cause the underlying
     * connection to be closed and prevent it from being reused for subsequent operations.
     *
     * @return Returns the CompletionStage that contains the blocking {@code InputStream}.
     */
    default CompletionStage<InputStream> asInputStream() {
        return transform(StreamSubscriber.ofInputStream());
    }
}
