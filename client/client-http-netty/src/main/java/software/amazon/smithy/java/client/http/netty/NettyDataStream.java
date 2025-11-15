/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.Channel;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * A {@link DataStream} implementation that uses a queue to for backpressure control. Backpressure
 * control is needed to account for content delivered by Netty <i>before</i> there's a consumer
 * for it. We have some control by requesting reads from Netty, but we need to read right
 * before creating the result to create the response object and that might bring more data
 * than what's needed to complete the response objects (read the status, headers, etc)
 * <p>
 * At any time at most two threads will have access to this object, the producer of the stream, i.e.,
 * {@link NettyHttpResponseHandler}, and, the consumer of the stream, i.e., the smithy execution
 * stage responsible from reading the chunks and converting them into client side consumable
 * objects.
 */
final class NettyDataStream implements DataStream {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(NettyDataStream.class);
    // Proactively refill the queue if it goes below this minimum size just to have a buffer.
    // under the assumption that reads are expensive while the consuming is cheap.
    private static final int QUEUE_MIN_SIZE = 5;

    /**
     * The content type of this data stream.
     */
    private final String contentType;
    /**
     * The content length for this data stream. -1 means the content length is unknown.
     */
    private final long contentLength;
    /**
     * The Netty channel, used only for logging.
     */
    private final Channel channel;
    /**
     * Queue to control backpressure.
     */
    private final BlockingQueue<ByteBuffer> queue = new LinkedBlockingQueue<>();
    /**
     * Changes to true when the consumer gets subscribed by calling {@link DataStream#subscribe(Flow.Subscriber)}.
     * Using an atomic to keep at most a single subscriber subscribed.
     */
    private final AtomicBoolean consumerSubscribed = new AtomicBoolean(false);
    /**
     * This is actual flow of the subscriber. This field will be written by the thread setting the
     * consumer subscriber, and read by the producer thread (e.g., called from Netty read handler).
     */
    private volatile Flow.Subscriber<? super ByteBuffer> consumerSubscriber;
    /**
     * Amount of messages requested by the consumer but not yet fulfill by the producer.
     */
    private final AtomicLong pending = new AtomicLong(0);
    /**
     * Changes to true when the producer {@link Flow.Subscriber#onError(Throwable)} is called. This signals
     * an error in Netty. The {@link #error} reference get set with the exception.
     */
    private final AtomicBoolean errored = new AtomicBoolean(false);
    /**
     * Atomic reference to keep the error set by the producer by calling {@link Flow.Subscriber#onError(Throwable)}.
     * An atomic reference is used to ensure that we deliver the error to the consumer only once.
     */
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    /**
     * Changes to true when the consumer calls {@link Flow.Subscription#cancel()} is called. This prevents further
     * delivery but keep
     */
    private volatile boolean consumerCancelled = false;
    /**
     * Changes to true when we call {@link Flow.Subscription#cancel()} on the producer subscription to request
     * the flow to be cancelled.
     */
    private final AtomicBoolean producerCancelled = new AtomicBoolean(false);
    /**
     * Flag to make sure that we only call the consumer {@link Flow.Subscriber#onComplete()} once.
     */
    private final AtomicBoolean onCompleteCalled = new AtomicBoolean(false);
    /**
     * Flag set by the producer when its {@link Flow.Subscriber#onComplete()} method is called.
     * This will in turn call {@code onComplete()} on the consumer after draining the queue.
     */
    private volatile boolean producerCompleted = false;
    /**
     * Flag to control requests from the producer. Calling request calls a {@link Channel#read()}
     * in the Netty event-loop. Multiple events if one is pending will be ignored.
     */
    private volatile boolean readRequested = false;
    /**
     * The producer subscriber, used to request more reads from Netty and to cancel the flow if needed.
     */
    private final ProducerSubscriber producerSubscriber;

    NettyDataStream(String contentType, long contentLength, Channel channel) {
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.contentLength = contentLength;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.producerSubscriber = new ProducerSubscriber();
    }

    @Override
    public boolean isReplayable() {
        // Streaming data cannot be replayed
        return false;
    }

    @Override
    public InputStream asInputStream() {
        return new NettyFlowInputStream(this);
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (!consumerSubscribed.compareAndSet(false, true)) {
            LOGGER.warn(channel,
                    "Already subscribed to {}, new subscriber {} will get onError",
                    this.consumerSubscriber,
                    subscriber);
            subscriber.onError(new IllegalStateException("Already subscribed"));
            return;
        }
        LOGGER.trace(channel, "Subscribed to {}", subscriber);
        this.consumerSubscriber = subscriber;
        subscriber.onSubscribe(new ConsumerSubscription());
    }

    /**
     * Called by {@link NettyHttpResponseHandler} to get the subscriber that
     * is going to get notified about when content is received.
     */
    Flow.Subscriber<ByteBuffer> producerSubscriber() {
        return producerSubscriber;
    }

    private void enqueue(ByteBuffer item) {
        queue.add(item);
        tryDeliver();
    }

    private void tryDeliver() {
        LOGGER.trace(channel,
                "Attempting to deliver; subscriber {}, requested {}, got: {}, isErrored: {}",
                this.consumerSubscriber,
                this.pending,
                this.queue.size(),
                this.errored);

        // We don't have a subscription to the flow yet. Let's wait until we do.
        if (consumerSubscriber == null) {
            return;
        }

        // The producer flow got signaled about an error, let's make sure that the
        // consumer gets notified about it.
        if (errored.get()) {
            var pendingError = error.get();
            if (pendingError != null && error.compareAndSet(pendingError, null)) {
                // Deliver anything queued before signaling about the error
                deliverPending();
                consumerSubscriber.onError(pendingError);
            }
            queue.clear();
            return;
        }

        // The upstream consumer is no longer interested in receiving content.
        // Swallow any still present on the queue and notify the producer if
        // we yet haven't done so.
        if (consumerCancelled) {
            queue.clear();
            if (producerCancelled.compareAndSet(false, true)) {
                producerSubscriber.cancel();
            }
            return;
        }

        // Deliver as many pending items as possible.
        deliverPending();

        // If after delivery we still have pending items or the queue falls below our min size threshold
        // we request more from the producer making sure that we don't over-request.
        if (!producerCompleted && (pending.get() > 0 || queue.size() < QUEUE_MIN_SIZE)) {
            // `readRequested` flag is reset after getting a new item in the `ProducerSubscriber#onNext` .
            if (!readRequested) {
                readRequested = true;
                producerSubscriber.request();
            }
        }

        // The downstream has completed and there are no messages left in the queue. Notify the upstream
        // subscriber that we have completed the flow.
        if (producerCompleted && queue.isEmpty()) {
            if (onCompleteCalled.compareAndSet(false, true)) {
                consumerSubscriber.onComplete();
            }
        }
    }

    private void deliverPending() {
        while (pending.get() > 0 && !queue.isEmpty()) {
            var item = queue.poll();
            if (item != null) {
                pending.decrementAndGet();
                consumerSubscriber.onNext(item);
            }
        }
    }

    /**
     * The subscription object given to the consumer subscriber. The consumer use it to request
     * more data from the data stream.
     */
    class ConsumerSubscription implements Flow.Subscription {

        @Override
        public void request(long n) {
            LOGGER.trace(channel, "Requested {} items", n);
            if (n <= 0) {
                consumerSubscriber.onError(new IllegalArgumentException("Request must be positive"));
                errored.set(true);
                return;
            }
            var result = pending.addAndGet(n);
            // Check for overflow.
            if (result < 0) {
                pending.set(Long.MAX_VALUE);
            }
            tryDeliver();
        }

        @Override
        public void cancel() {
            consumerCancelled = true;
        }
    }

    /**
     * This subscriber is used by {@link NettyHttpResponseHandler} to send ByteBuffer chunks as those
     * are read. The sourceSubscription {@link Flow.Subscription#request(long)} to request more data
     * from the Netty. This in turn will trigger a {@link Channel#read()} on the channel. The n value
     * is ignored, as only one read can be triggered for each call.
     */
    class ProducerSubscriber implements Flow.Subscriber<ByteBuffer> {
        private volatile Flow.Subscription producerSubscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.producerSubscription != null) {
                throw new IllegalStateException("Already subscribed");
            }
            this.producerSubscription = subscription;
        }

        @Override
        public void onNext(ByteBuffer content) {
            readRequested = false;
            enqueue(content);
        }

        @Override
        public void onError(Throwable throwable) {
            if (errored.compareAndSet(false, true)) {
                error.set(throwable);
                // allow the error to get delivered.
                tryDeliver();
            }
        }

        @Override
        public void onComplete() {
            producerCompleted = true;
            tryDeliver();
        }

        public void request() {
            if (this.producerSubscription == null) {
                throw new IllegalStateException("ProducerSubscription not set");
            }
            this.producerSubscription.request(1);
        }

        public void cancel() {
            if (this.producerSubscription == null) {
                throw new IllegalStateException("ProducerSubscription not set");
            }
            if (!producerCompleted) {
                this.producerSubscription.cancel();
            }
        }
    }
}
