/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link HttpRequest.BodyPublisher} that publishes an in-memory request body in a single {@code onNext} call.
 *
 * <p>Used as a fast path for request bodies that are both replayable and already backed by a {@link ByteBuffer}.
 * Why not use {@link HttpRequest.BodyPublishers#ofByteArray(byte[])}? -- requires a {@code byte[]}.
 * Why not use {@link HttpRequest.BodyPublishers#fromPublisher(Flow.Publisher, long)}? -- add adds a subscription
 * state-machine hop and requires the upstream publisher to handle backpressure properly per subscribe. For a single
 * in-memory body that's all wasted overhead.
 */
final class JavaHttpClientReplayableByteBufferPublisher implements HttpRequest.BodyPublisher {
    private final ByteBuffer body;

    JavaHttpClientReplayableByteBufferPublisher(ByteBuffer body) {
        this.body = body.asReadOnlyBuffer();
    }

    @Override
    public long contentLength() {
        return body.remaining();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            private final AtomicBoolean completed = new AtomicBoolean();

            @Override
            public void request(long n) {
                if (n <= 0 || !completed.compareAndSet(false, true)) {
                    return;
                } else {
                    subscriber.onNext(body.duplicate());
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                completed.set(true);
            }
        });
    }
}
