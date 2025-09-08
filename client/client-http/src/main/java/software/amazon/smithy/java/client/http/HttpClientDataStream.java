/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * This class defers turning the HTTP client's response publisher into an adapted publisher unless required.
 *
 * <p>This class avoids needing to use multiple intermediate adapters to go from a flow that publishes a list of
 * byte buffers to a flow that publishes single byte buffer. Instead, it directly implements asByteBuffer and
 * asInputStream to use a more direct integration from the HTTP client library.
 */
record HttpClientDataStream(
        Flow.Publisher<List<ByteBuffer>> httpPublisher,
        long contentLength,
        String contentType) implements DataStream {
    @Override
    public boolean isReplayable() {
        return false;
    }

    @Override
    public CompletableFuture<ByteBuffer> asByteBuffer() {
        var p = java.net.http.HttpResponse.BodySubscribers.ofByteArray();
        httpPublisher.subscribe(p);
        return p.getBody().thenApply(ByteBuffer::wrap).toCompletableFuture();
    }

    @Override
    public CompletableFuture<InputStream> asInputStream() {
        var p = java.net.http.HttpResponse.BodySubscribers.ofInputStream();
        httpPublisher.subscribe(p);
        return p.getBody().toCompletableFuture();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        // Adapt the "Flow.Subscriber<List<ByteBuffer>" to "Flow.Subscriber<ByteBuffer>".
        httpPublisher.subscribe(new BbListToBbSubscriber(subscriber));
    }

    private static final class BbListToBbSubscriber implements Flow.Subscriber<List<ByteBuffer>> {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;

        BbListToBbSubscriber(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        private Flow.Subscription upstreamSubscription;
        private final Queue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();
        private final AtomicLong demand = new AtomicLong(0);
        private final AtomicBoolean senderFinished = new AtomicBoolean(false);

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            upstreamSubscription = subscription;

            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    demand.addAndGet(n);
                    drainAndRequest();
                }

                @Override
                public void cancel() {
                    upstreamSubscription.cancel();
                }
            });
        }

        @Override
        public void onError(Throwable throwable) {
            subscriber.onError(throwable);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            queue.addAll(item);
            drainAndRequest();
        }

        @Override
        public void onComplete() {
            // The sender is done sending us bytes, so when our queue is empty, emit onComplete downstream.
            senderFinished.set(true);
            drain();
        }

        private void drain() {
            try {
                while (!queue.isEmpty() && demand.get() > 0) {
                    ByteBuffer buffer = queue.poll();
                    if (buffer != null) {
                        subscriber.onNext(buffer);
                        demand.decrementAndGet();
                    }
                }
                // When we have no more buffered BBs and the sender has signaled they're done, then complete downstream.
                if (queue.isEmpty() && senderFinished.get()) {
                    subscriber.onComplete();
                }
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }

        private void drainAndRequest() {
            drain();

            var currentDemand = demand.get();
            if (currentDemand > 0 && queue.isEmpty() && !senderFinished.get()) {
                upstreamSubscription.request(currentDemand);
            }
        }
    }
}
