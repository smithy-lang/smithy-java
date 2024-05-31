/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.streaming;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Represents the stream of a shape.
 *
 * <p>This interface is essentially a {@link Flow.Publisher} that potentially knows the amount of data contained
 * in the stream and the stream's content type.
 */
public interface StreamPublisher extends Flow.Publisher<ByteBuffer> {
    /**
     * Get the content length of the publisher.
     *
     * <p>Zero is returned if the length is known to be zero. A negative number is returned if the length is
     * unknown. A positive number is returned if the length is known.
     *
     * @return Returns the content length of the publisher.
     */
    long contentLength();

    /**
     * Get the content type, or media type, of the stream if known.
     *
     * @return the media type, if known.
     */
    Optional<String> contentType();

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

    /**
     * Creates a StreamPublisher that contains no data.
     *
     * @return The created StreamPublisher.
     */
    static StreamPublisher ofEmpty() {
        return ofHttpRequestPublisher(HttpRequest.BodyPublishers.noBody(), null);
    }

    /**
     * Creates a StreamPublisher that emits data from a {@link HttpRequest.BodyPublisher}.
     *
     * @param publisher   HTTP request body publisher to stream.
     * @param contentType Content-Type to associate with the stream. Can be set to null.
     * @return The created StreamPublisher.
     */
    static StreamPublisher ofHttpRequestPublisher(HttpRequest.BodyPublisher publisher, String contentType) {
        return new StreamPublisher() {
            @Override
            public long contentLength() {
                return publisher.contentLength();
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
     * Creates a StreamPublisher that emits data from a {@link Flow.Publisher}.
     *
     * @param publisher   Publisher to stream.
     * @param contentType Content-Type to associate with the stream. Can be null.
     * @param contentLength Content length of the stream. Use -1 for unknown, and 0 or greater for the byte length.
     * @return the created StreamPublisher.
     */
    static StreamPublisher ofPublisher(Flow.Publisher<ByteBuffer> publisher, String contentType, long contentLength) {
        return new StreamPublisher() {
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
     * Creates a StreamPublisher that emits bytes from a String.
     *
     * @param data Data to stream.
     * @return the created StreamPublisher.
     */
    static StreamPublisher ofString(String data) {
        return ofString(data, null);
    }

    /**
     * Creates a StreamPublisher that emits bytes from a String.
     *
     * @param data        Data to stream.
     * @param contentType Content-Type to associate with the stream.
     * @return the created StreamPublisher.
     */
    static StreamPublisher ofString(String data, String contentType) {
        return ofHttpRequestPublisher(HttpRequest.BodyPublishers.ofString(data), contentType);
    }

    /**
     * Creates a StreamPublisher that emits bytes from a byte array.
     *
     * @param bytes       Data to stream.
     * @return the created StreamPublisher.
     */
    static StreamPublisher ofBytes(byte[] bytes) {
        return ofBytes(bytes, null);
    }

    /**
     * Creates a StreamPublisher that emits bytes from a byte array.
     *
     * @param bytes       Data to stream.
     * @param contentType Content-Type to associate with the stream.
     * @return the created StreamPublisher.
     */
    static StreamPublisher ofBytes(byte[] bytes, String contentType) {
        return ofHttpRequestPublisher(HttpRequest.BodyPublishers.ofByteArray(bytes), contentType);
    }

    static StreamPublisher ofInputStream(InputStream inputStream) {
        return ofInputStream(inputStream, null);
    }

    // TODO: This looks like it is always contentLength = -1. If the length of the InputStream is known, can it be used?
    static StreamPublisher ofInputStream(InputStream inputStream, String contentType) {
        return ofHttpRequestPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> inputStream), contentType);
    }

    static StreamPublisher ofFile(Path file) {
        String contentType;
        try {
            contentType = Files.probeContentType(file);
        } catch (IOException e) {
            contentType = null;
        }
        return ofFile(file, contentType);
    }

    static StreamPublisher ofFile(Path file, String contentType) {
        try {
            return ofHttpRequestPublisher(HttpRequest.BodyPublishers.ofFile(file), contentType);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException("File not found: " + file, e);
        }
    }
}
