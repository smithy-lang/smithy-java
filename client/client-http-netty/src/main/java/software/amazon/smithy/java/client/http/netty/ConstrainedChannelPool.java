/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ChannelPool wrapper that limits concurrent connections and queues
 * excess requests with configurable timeout.
 */
public class ConstrainedChannelPool implements ChannelPool {

    private final ChannelPool delegate;
    private final EventLoopGroup eventLoopGroup;
    private final int maxConnections;
    private final long acquireTimeoutMillis;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final Queue<PendingAcquisition> waitQueue = new ConcurrentLinkedQueue<>();

    public ConstrainedChannelPool(
            ChannelPool delegate,
            EventLoopGroup eventLoopGroup,
            int maxConnections,
            long acquireTimeoutMillis
    ) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections must be positive");
        }
        if (acquireTimeoutMillis <= 0) {
            throw new IllegalArgumentException("acquireTimeoutMillis must be positive");
        }
        this.delegate = delegate;
        this.eventLoopGroup = eventLoopGroup;
        this.maxConnections = maxConnections;
        this.acquireTimeoutMillis = acquireTimeoutMillis;
    }

    @Override
    public Future<Channel> acquire() {
        return acquire(eventLoopGroup.next().<Channel>newPromise());
    }

    @Override
    public Future<Channel> acquire(Promise<Channel> promise) {
        if (promise == null) {
            promise = eventLoopGroup.next().newPromise();
        }

        final Promise<Channel> finalPromise = promise;

        // Try to acquire immediately if under limit
        if (activeConnections.get() < maxConnections &&
                activeConnections.compareAndSet(activeConnections.get(), activeConnections.get() + 1)) {

            acquireFromDelegate(finalPromise);
        } else {
            // Queue the request with timeout
            enqueueAcquisition(finalPromise);
        }

        return finalPromise;
    }

    @Override
    public Future<Void> release(Channel channel) {
        return release(channel, channel.eventLoop().<Void>newPromise());
    }

    @Override
    public Future<Void> release(Channel channel, Promise<Void> promise) {
        if (promise == null) {
            promise = channel.eventLoop().newPromise();
        }

        final Promise<Void> finalPromise = promise;

        // Release to delegate
        delegate.release(channel).addListener(future -> {
            if (future.isSuccess()) {
                // Decrement counter and process queue
                activeConnections.decrementAndGet();
                processNextInQueue();
                finalPromise.setSuccess(null);
            } else {
                finalPromise.setFailure(future.cause());
            }
        });

        return finalPromise;
    }

    @Override
    public void close() {
        // Fail all pending acquisitions
        PendingAcquisition pending;
        while ((pending = waitQueue.poll()) != null) {
            pending.cancel();
            pending.promise.setFailure(new IllegalStateException("Pool is closing"));
        }

        delegate.close();
    }

    private void acquireFromDelegate(Promise<Channel> promise) {
        delegate.acquire().addListener(future -> {
            if (future.isSuccess()) {
                Channel channel = (Channel) future.getNow();
                promise.setSuccess(channel);
            } else {
                // Failed to acquire, decrement and try next in queue
                activeConnections.decrementAndGet();
                processNextInQueue();
                promise.setFailure(future.cause());
            }
        });
    }

    private void enqueueAcquisition(Promise<Channel> promise) {
        // Get EventLoop from the promise's channel (if it's an EventLoop)
        // or use one from the group
        EventLoop eventLoop = eventLoopGroup.next();

        PendingAcquisition pending = new PendingAcquisition(promise, eventLoop);
        waitQueue.offer(pending);

        // Schedule timeout
        pending.scheduleTimeout(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    private void processNextInQueue() {
        PendingAcquisition pending = waitQueue.poll();
        if (pending != null && !pending.isCancelled()) {
            if (activeConnections.get() < maxConnections) {
                int current = activeConnections.get();
                if (activeConnections.compareAndSet(current, current + 1)) {
                    pending.cancelTimeout();
                    acquireFromDelegate(pending.promise);
                    return;
                }
            }
            // Put it back if we can't process it
            waitQueue.offer(pending);
        }
    }

    private class PendingAcquisition {
        final Promise<Channel> promise;
        final EventLoop eventLoop;
        private Future<?> timeoutFuture;
        private volatile boolean cancelled = false;

        PendingAcquisition(Promise<Channel> promise, EventLoop eventLoop) {
            this.promise = promise;
            this.eventLoop = eventLoop;
        }

        void scheduleTimeout(long timeout, TimeUnit unit) {
            timeoutFuture = eventLoop.schedule(() -> {
                if (!cancelled && waitQueue.remove(this)) {
                    cancelled = true;
                    promise.setFailure(new TimeoutException(
                            "Acquisition timeout after " + timeout + " " + unit));
                }
            }, timeout, unit);
        }

        void cancelTimeout() {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
        }

        void cancel() {
            cancelled = true;
            cancelTimeout();
        }

        boolean isCancelled() {
            return cancelled || promise.isDone();
        }
    }
}
