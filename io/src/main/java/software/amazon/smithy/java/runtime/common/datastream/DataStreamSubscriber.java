/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.common.datastream;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * Subscribes to a {@link DataStream} to transform it into a result.
 *
 * <p>Note: this class and factory methods are heavily based on {@link HttpResponse.BodySubscriber} and typically
 * leverages its implementations.
 *
 * @param <T> Result to transform the published data into.
 */
interface DataStreamSubscriber<T> extends Flow.Subscriber<ByteBuffer> {
    /**
     * Returns a {@code CompletionStage} which when completed will return the created result. This method can be called
     * at any time relative to the other {@link Flow.Subscriber} methods.
     *
     * @return a CompletionStage for the created result.
     */
    CompletionStage<T> result();

    /**
     * Reads a stream and stores the result into a UTF-8 encoded String.
     *
     * @return the StreamSubscriber that reads the stream into a String.
     */
    static DataStreamSubscriber<String> ofString() {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Reads a stream and stores the result into a byte array.
     *
     * @return the StreamSubscriber that reads the stream into a byte array.
     */
    static DataStreamSubscriber<byte[]> ofByteArray() {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.ofByteArray());
    }

    /**
     * Reads a stream and stores the result into a ByteBuffer.
     *
     * @return the StreamSubscriber that reads the stream into a ByteBuffer.
     */
    static DataStreamSubscriber<ByteBuffer> ofByteBuffer() {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.ofByteArray(), ByteBuffer::wrap);
    }

    /**
     * Returns a {@code StreamSubscriber} which stores the stream in a file opened with the given name.
     *
     * <p>Equivalent to: {@code ofFile(file, CREATE, WRITE)}
     *
     * @param  file the file to store the body in
     * @return a body subscriber
     */
    static DataStreamSubscriber<Path> ofFile(Path file) {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.ofFile(file));
    }

    /**
     * Returns a {@code StreamSubscriber} which stores the stream bytes in a file opened with the given options and
     * name. The file will be opened with the given options using {@link FileChannel#open(Path,OpenOption...)
     * FileChannel.open} just before the stream is read.
     *
     * @param  file the file to write the body to.
     * @param  openOptions the list of options to open the file with.
     * @return a body subscriber
     * @throws IllegalArgumentException if an invalid set of open options are specified.
     */
    static DataStreamSubscriber<Path> ofFile(Path file, OpenOption... openOptions) {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.ofFile(file, openOptions));
    }

    /**
     * Returns a {@code StreamSubscriber} which streams the response body as a blocking {@link InputStream}.
     *
     * <p>The InputStream is made available without requiring to wait for the entire stream to be processed.
     *
     * @apiNote To ensure that all resources associated with the corresponding exchange are properly released, the
     * caller must ensure to either read all bytes until EOF is reached, or call {@link InputStream#close} if it is
     * unable or unwilling to do so. Calling {@code close} before exhausting the stream may cause underlying
     * connections to be closed and prevent them from being reused for subsequent operations.
     *
     * @return a subscriber that streams the response body as an {@link InputStream}.
     */
    static DataStreamSubscriber<InputStream> ofInputStream() {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.ofInputStream());
    }

    /**
     * Returns a {@code StreamSubscriber} that discards and received stream data.
     *
     * @return the discarding subscriber.
     */
    static DataStreamSubscriber<Void> discarding() {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.discarding());
    }

    /**
     * Returns a {@code StreamSubscriber} that discards the stream and replaces it with {@code value}.
     *
     * @param value Value to replace the discarded result.
     * @return the replacing subscriber.
     */
    static <U> DataStreamSubscriber<U> replacing(U value) {
        return ofHttpResponseBodySubscriber(HttpResponse.BodySubscribers.replacing(value));
    }

    /**
     * Create a {@code StreamSubscriber} from an {@link HttpResponse.BodySubscriber}.
     *
     * @param subscriber Body subscriber to adapt to a {@code StreamSubscriber}.
     * @return the adapted StreamSubscriber.
     * @param <T> Value created by the BodySubscriber.
     */
    private static <T> DataStreamSubscriber<T> ofHttpResponseBodySubscriber(HttpResponse.BodySubscriber<T> subscriber) {
        return ofHttpResponseBodySubscriber(subscriber, Function.identity());
    }

    private static <I, O> DataStreamSubscriber<O> ofHttpResponseBodySubscriber(
        HttpResponse.BodySubscriber<I> subscriber,
        Function<I, O> function
    ) {
        return new DataStreamSubscriber<>() {
            @Override
            public CompletionStage<O> result() {
                return subscriber.getBody().thenApply(function);
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
                subscriber.onNext(List.of(item));
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        };
    }
}
