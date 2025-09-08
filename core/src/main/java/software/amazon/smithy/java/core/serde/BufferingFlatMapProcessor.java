/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A processor abstraction that maps inputs of type I from an upstream publisher to 0-n items of type O
 * that are buffered without limit and published to a subscriber. This prevents the subscriber from receiving
 * more items than requested when one I maps to multiple Os.
 * <p>
 * Note that this does not perform event publication on a different thread; both receipt of items and requests
 * for more items can trigger publication of items on the calling thread.
 *
 * @param <I> the type published by the upstream publisher.
 * @param <O> the type published to the downstream subscriber.
 */
public abstract class BufferingFlatMapProcessor<I, O> implements
        Flow.Processor<I, O>,
        Flow.Subscription {
    private static final InternalLogger LOG = InternalLogger.getLogger(BufferingFlatMapProcessor.class);
    private static final Throwable COMPLETE_SENTINEL = new RuntimeException();

    private final AtomicReference<Throwable> terminalEventHolder = new AtomicReference<>();
    private final AtomicLong pendingRequests = new AtomicLong();
    private final AtomicInteger pendingFlushes = new AtomicInteger();
    private final BlockingQueue<O> queue = new LinkedBlockingQueue<>();

    private volatile Flow.Subscription upstreamSubscription;
    private volatile Flow.Subscriber<? super O> downstream;
    private boolean terminated = false;

    public BufferingFlatMapProcessor(
            Flow.Publisher<I> publisher
    ) {
        publisher.subscribe(this);
    }

    protected abstract Stream<O> map(I item);

    @Override
    public final void onSubscribe(Flow.Subscription subscription) {
        upstreamSubscription = subscription;
        if (pendingRequests.get() > 0 && pendingFlushes.get() == 0) {
            flush();
        }
    }

    @Override
    public final void subscribe(Flow.Subscriber<? super O> subscriber) {
        downstream = subscriber;
        subscriber.onSubscribe(this);
    }

    @Override
    public final void onNext(I item) {
        try {
            map(item).forEach(this::addToQueue);
        } catch (Exception e) {
            LOG.warn("Malformed input", e);
            onError(new SerializationException("Malformed input", e));
            return;
        }
        flush();
    }

    /**
     * Used to add the initial message to the queue. This message won't be sent until
     * a flush happens, either by a calling {@link #request(long)} or {@link #onNext(Object)}.
     * <p>
     * This method will re-throw any exception caught when calling {@link #map} without calling
     * {@link #onError(Throwable)} since it's assumed that the processor is not yet fully setup
     * when this method is called.
     */
    protected final void enqueueItem(I item) {
        try {
            map(item).forEach(this::addToQueue);
        } catch (RuntimeException e) {
            LOG.warn("Malformed input", e);
            throw e;
        }
    }

    private void addToQueue(O item) {
        queue.add(item);
    }

    @Override
    public final void onError(Throwable t) {
        upstreamSubscription.cancel();
        terminalEventHolder.compareAndSet(null, t);
        if (upstreamSubscription != null && downstream != null) {
            flush();
        }
    }

    @Override
    public final void onComplete() {
        terminalEventHolder.compareAndSet(null, COMPLETE_SENTINEL);
        if (upstreamSubscription != null && downstream != null) {
            flush();
        }
    }

    @Override
    public final void request(long n) {
        if (n <= 0) {
            onError(new IllegalArgumentException("got a request for " + n + " items"));
            return;
        }

        accumulate(pendingRequests, n);
        flush();
    }

    private void flush() {
        if (upstreamSubscription == null || downstream == null) {
            LOG.warn("flush() requested before upstream and downstream fully wired, " +
                    "upstreamSubscription is null: {}, downstream is null: {}",
                    upstreamSubscription == null,
                    downstream == null);
            onError(new IllegalStateException("flush() requested before upstream and downstream fully wired."));
            return;
        }

        if (pendingFlushes.getAndIncrement() > 0) {
            return;
        }

        if (terminated) {
            return;
        }

        int loop = 1;
        while (loop > 0) {
            long pending = pendingRequests.get();

            Flow.Subscriber<? super O> subscriber = downstream;
            long delivered = sendMessages(subscriber, pending);
            boolean empty = queue.isEmpty();
            Throwable terminalEvent = terminalEventHolder.get();
            if (terminalEvent != null && attemptTermination(subscriber, terminalEvent, empty)) {
                terminated = true;
                return;
            }

            if (delivered > 0) {
                /*
                 * We still need to re-read at the start of the loop because additions to pendingRequest happen-before
                 * additions to pendingFlushes. If we reused this value in the next loop, there is a race condition:
                 * 1. Thread A is in `flush()`.
                 * 2. Thread B enters `request`.
                 * 3. Thread A decrements pendingRequests here.
                 * 4. Thread B increments pendingFlushes and returns because A is still flushing.
                 * 5. Thread A decrements pendingFlushes and noticed that something requested a flush. It loops around
                 *    but _doesn't_ read the new value of pendingRequests, so it does nothing.
                 *
                 * In short, reload _all_ state on _every_Loop. You are only guaranteed to see updates to shared state
                 * after reading a value from pendingFlushes.
                 */
                accumulate(pendingRequests, -delivered);

                /*
                 * We need to accumulate our local `pending` value separately from the atomic `pendingRequests`.
                 * Consider this scenario with two buffers available for flush and one outstanding request:
                 * 1. Thread A is in `flush()`. It observes 1 pending request and flushes 1 buffer.
                 * 2. Thread B calls `request` and increments `pendingRequests` from 1 to 2.
                 * 3. Thread A enters this if statement. It delivered one message, subtracts 1 from `pendingRequests`,
                 *    and stores the new sum of 1 in `pending`.
                 * 4. Thread A enters the next if statement and requests one buffer from the upstream subscription,
                 *    even though it fulfilled the 1 request that was present in this loop and we have 1 more buffer
                 *    that can be flushed.
                 *
                 * To avoid over-requesting buffers, we must only consider how much demand we successfully fulfilled
                 * verses how much we were willing to fulfill on this loop. To do this, we must only read a value from
                 * `pendingRequests` a single time, at the top of each loop. The rest of the loop must only work with
                 * the value read at the start.
                 */
                pending = accumulate(pending, -delivered);
            }

            if (pending > 0) {
                // do this inside the flush loop so a recursive flush -> request -> onNext -> flush
                // call will be aborted by the `pendingFlushes` check.
                upstreamSubscription.request(1);
            }

            loop = pendingFlushes.addAndGet(-loop);
        }
    }

    protected void handleError(Throwable error, Flow.Subscriber<? super O> subscriber) {
        subscriber.onError(error);
    }

    /**
     * @return true if this decoder is in a terminal state
     */
    private boolean attemptTermination(Flow.Subscriber<? super O> subscriber, Throwable terminalEvent, boolean done) {
        if (done && subscriber != null) {
            if (terminalEvent == COMPLETE_SENTINEL) {
                subscriber.onComplete();
            } else {
                handleError(terminalEvent, subscriber);
            }
            return true;
        }

        return false;
    }

    /**
     * Tries to flush up to the given demand and signals if we need data from
     * upstream if there is unfulfilled demand.
     *
     * @param outstanding outstanding message demand to fulfill
     * @return number of fulfilled requests
     */
    private long sendMessages(Flow.Subscriber<? super O> subscriber, long outstanding) {
        long served = 0;

        if (subscriber != null) {
            while (served < outstanding) {
                O m = queue.poll();
                if (m == null) {
                    break;
                }
                served++;
                subscriber.onNext(m);
            }
        }

        return served;
    }

    @Override
    public final void cancel() {
        upstreamSubscription.cancel();
    }

    private static long accumulate(long current, long n) {
        if (current == Long.MAX_VALUE || n == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        try {
            return Math.addExact(current, n);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static void accumulate(AtomicLong l, long n) {
        l.accumulateAndGet(n, BufferingFlatMapProcessor::accumulate);
    }
}
