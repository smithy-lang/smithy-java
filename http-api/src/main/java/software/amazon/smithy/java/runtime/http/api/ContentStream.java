/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * A rewindable stream of data for HTTP messages.
 */
public interface ContentStream {
    /**
     * Get an empty ContentStream.
     */
    ContentStream EMPTY = new ContentStream() {
        @Override
        public Flow.Publisher<ByteBuffer> publisher() {
            // TODO: Is this a valid "empty" publisher?
            return subscriber -> {};
        }

        @Override
        public boolean rewind() {
            return false;
        }
    };

    /**
     * Get the Flow.Publisher of ByteBuffer.
     *
     * @return the underlying Publisher.
     */
    Flow.Publisher<ByteBuffer> publisher();

    // TODO: Not sure if this needs to be rewindable with Flow?
    /**
     * Attempt to rewind the input stream to the beginning of the stream.
     *
     * <p>This method must not throw if the stream is not rewindable.
     *
     * @return Returns true if the stream could be rewound.
     */
    boolean rewind();

    // TODO: Make this return CompletableFuture directly?
    default CompletionStage<String> asString() {
        return transform(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8));
    }

    private <T> CompletionStage<T> transform(HttpResponse.BodySubscriber<T> subscriber) {
        Flow.Subscriber<ByteBuffer> byteBufferSubscriber = new Flow.Subscriber<>() {
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

        publisher().subscribe(byteBufferSubscriber);
        return subscriber.getBody();
    }
}
