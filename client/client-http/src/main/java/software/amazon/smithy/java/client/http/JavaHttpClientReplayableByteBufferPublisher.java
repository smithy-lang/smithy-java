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
 * Fast path {@link HttpRequest.BodyPublisher} for replayable in-memory {@link ByteBuffer} bodies.
 *
 * <p>Publishes the body in a single {@code onNext} without forcing a {@code byte[]} copy or an
 * extra publisher wrapper.
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
