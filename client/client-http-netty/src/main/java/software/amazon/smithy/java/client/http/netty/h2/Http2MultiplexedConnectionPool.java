/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import static software.amazon.smithy.java.client.http.netty.NettyConstants.CHANNEL_POOL;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.HTTP_VERSION_FUTURE;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_CONNECTION;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MAX_CONCURRENT_STREAMS;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CHANNEL;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CONNECTION_POOL;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.client.http.netty.NettyLogger;
import software.amazon.smithy.java.client.http.netty.NettyUtils;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * A specialized connection pool for multiplexed HTTP/2 connections.
 */
final class Http2MultiplexedConnectionPool implements ChannelPool {
    private static final AttributeKey<Boolean> RELEASED = AttributeKey.valueOf(
            "smithy.netty.h2.released-connection-pool");
    private static final NettyLogger LOGGER = NettyLogger.getLogger(Http2MultiplexedConnectionPool.class);
    private final Semaphore initSemaphore = new Semaphore(1);
    private final ChannelPool channelPool;
    private final EventLoopGroup eventLoopGroup;
    private final Http2StreamBootstrap streamBootstrap;
    private final MultiplexedChannelFactory multiplexedChannelFactory;
    private final Set<MultiplexedChannel> channels;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates a new connection pool with the given channel pool to create HTTP/2 connected channels and with the given
     * event loop group to be used by Netty.
     *
     * @param channelPool    The channel pool to create HTTP/2 connected channels
     * @param eventLoopGroup The event loop to be used with Netty
     */
    Http2MultiplexedConnectionPool(ChannelPool channelPool, EventLoopGroup eventLoopGroup) {
        this(channelPool,
                eventLoopGroup,
                (channel) -> new Http2StreamChannelBootstrap(channel).open(),
                MultiplexedChannel::new);
    }

    /**
     * This constructor is only used for testing to allow overriding the logic to bootstrap and open streams
     * from an HTTP/2 channel.
     */
    Http2MultiplexedConnectionPool(
            ChannelPool channelPool,
            EventLoopGroup eventLoopGroup,
            Http2StreamBootstrap streamBootstrap,
            MultiplexedChannelFactory multiplexedChannelFactory
    ) {
        this.channelPool = channelPool;
        this.eventLoopGroup = eventLoopGroup;
        this.streamBootstrap = streamBootstrap;
        this.channels = ConcurrentHashMap.newKeySet();
        this.multiplexedChannelFactory = multiplexedChannelFactory;
    }

    /**
     * This is called to request the expansion of the window size published by this endpoint.
     */
    private static void tryExpandConnectionWindow(Channel channel) {
        var http2Connection = channel.attr(HTTP2_CONNECTION).get();
        var initialWindowSize = channel.attr(HTTP2_INITIAL_WINDOW_SIZE).get();
        var connectionStream = http2Connection.connectionStream();
        LOGGER.debug(channel, "Expanding connection window of size {}", initialWindowSize);
        try {
            var localFlowController = http2Connection.local().flowController();
            localFlowController.incrementWindowSize(connectionStream, initialWindowSize);
        } catch (Http2Exception e) {
            LOGGER.warn(channel, "Failed to expand window of size {}", initialWindowSize, e);
        }
    }

    /**
     * Acquires a channel from the pool, reusing existing multiplexed connections when possible.
     *
     * @return A future that is notified once the acquire is successful and failed otherwise.
     */
    @Override
    public Future<Channel> acquire() {
        var acquirePromise = eventLoopGroup.next().<Channel>newPromise();
        return acquire(acquirePromise);
    }

    /**
     * Acquires a channel from the pool, reusing existing multiplexed connections when possible.
     *
     * @param acquirePromise the promise to complete when a channel is acquired
     * @return A future that is notified once the acquire is successful and failed otherwise.
     */
    @Override
    public Future<Channel> acquire(Promise<Channel> acquirePromise) {
        Objects.requireNonNull(acquirePromise, "acquirePromise");
        if (closed.get()) {
            acquirePromise.setFailure(new TransportException("Channel pool is closed"));
            return acquirePromise;
        }

        while (true) {
            // Try existing channels first
            for (var multiplexedChannel : channels) {
                if (multiplexedChannel.tryAcquire(acquirePromise)) {
                    acquirePromise.addListener(new AddConnectionPoolAttribute(this));
                    return acquirePromise;
                }
            }

            // Need new channel - synchronize to avoid creating two channels with
            // one stream each instead of two streams from the same channel.
            var currentCount = channels.size();
            var acquired = false;
            try {
                initSemaphore.acquire();
                acquired = true;

                // Double-check: did someone else add a channel?
                if (channels.size() > currentCount) {
                    initSemaphore.release();
                    acquired = false;
                    continue; // Retry with new channel
                }

                // Create new parent channel
                var newParent = channelPool.acquire();
                // Release semaphore when acquire completes (success or failure). We create a new
                // promise to ensure that we don't complete the result prior to releasing the
                // semaphore.
                Promise<Channel> completeAfterReleaseSemaphore = newPromise();
                completeAfterReleaseSemaphore.addListener(new ReleaseSemaphore(acquirePromise, initSemaphore));
                newParent.addListener(new NewParentAcquiredListener(completeAfterReleaseSemaphore, this));
                return acquirePromise;
            } catch (InterruptedException e) {
                // Acquiring the semaphore threw, the permit was not acquired.
                Thread.currentThread().interrupt();
                acquirePromise.setFailure(new TransportException("Interrupted", e));
                return acquirePromise;
            } catch (Exception e) {
                if (acquired) {
                    initSemaphore.release();
                }
                acquirePromise.setFailure(e);
                return acquirePromise;
            }
        }
    }

    @Override
    public Future<Void> release(Channel channel) {
        Promise<Void> promise = eventLoopGroup.next().newPromise();
        return release(channel, promise);
    }

    @Override
    public Future<Void> release(Channel stream, Promise<Void> promise) {
        var parent = stream.parent();
        if (parent == null) {
            // This method should be called with HTTP/2 streams, and those
            // have a parent. This is unexpected.
            var message = "Release attempt on a non HTTP/2 stream";
            closeAndReleaseParent(stream, null);
            NettyUtils.Asserts.shouldNotBeReached(stream, message);
            return promise.setFailure(new TransportException(message));
        }

        var multiplexedChannel = parent.attr(HTTP2_MULTIPLEXED_CHANNEL).get();
        if (multiplexedChannel == null) {
            // This is channel has a parent, and there is no attached multiplexed channel, but
            // we make sure that there is one before returning the first stream to the caller.
            // This is unexpected.
            var message = "Release attempt on a HTTP/2 stream without a multiplexed channel";
            stream.close();
            NettyUtils.Asserts.shouldNotBeReached(stream, message);
            return promise.setFailure(new TransportException(message));
        }

        multiplexedChannel.closeAndReleaseStream(stream);
        if (multiplexedChannel.canBeClosedAndReleased()) {
            // There are no more outstanding streams in this channel,
            // and it's ready to be closed. Close and release
            // the parent to the channel pool.
            return closeAndReleaseParent(parent, null, promise);
        }
        return promise.setSuccess(null);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            var closeCompleteFuture = submitClose();
            try {
                if (!closeCompleteFuture.await(5, TimeUnit.SECONDS)) {
                    throw new TransportException("Failed to finish closing all connections after 5 seconds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransportException(e);
            }

            var exception = closeCompleteFuture.cause();
            if (exception != null) {
                throw new TransportException("Failed to close channel pool.", exception);
            }
        }
    }

    /**
     * Called when there's an issue with the parent channel. This call closes all streams on the channel and then
     * closes the channel itself and releases it to its channel pool.
     *
     * @param parentChannel The parent channel to close
     * @param cause         An optional exception used to notify and close its streams
     * @return A future that will be notified when the close is complete.
     */
    public Future<Void> closeAndReleaseParent(Channel parentChannel, Throwable cause) {
        return closeAndReleaseParent(parentChannel, cause, newPromise());
    }

    private Future<Void> closeAndReleaseParent(Channel parentChannel, Throwable cause, Promise<Void> resultPromise) {
        if (parentChannel.parent() != null) {
            // This is NOT a parent channel. This should NOT happen, notify that something is wrong.
            var message = "Channel (" + parentChannel + ") is not a parent channel. It will be closed, "
                    + "but cannot be released within this pool.";
            var exception = new TransportException(message, cause);
            LOGGER.error(parentChannel, message, cause);
            parentChannel.close();
            NettyUtils.Asserts.shouldNotBeReached(parentChannel, message);
            return resultPromise.setFailure(exception);
        }

        var multiplexedChannel = parentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).get();

        // We may not have a multiplexed channel if the parent channel hasn't been fully initialized.
        if (multiplexedChannel != null) {
            multiplexedChannel.close(cause);
            channels.remove(multiplexedChannel);
        }
        parentChannel.close();
        if (parentChannel.attr(RELEASED).getAndSet(Boolean.TRUE) == null) {
            return channelPool.release(parentChannel, resultPromise);
        }
        return resultPromise.setSuccess(null);
    }

    /**
     * Returns the count of parent channels in this pool. Used for testing.
     */
    int parentCount() {
        return channels.size();
    }

    /**
     * Returns true if the acquire semaphore is released. Used for testing.
     */
    boolean isAcquireSemaphoreReleased() {
        var permits = initSemaphore.availablePermits();
        return permits == 1;
    }

    /**
     * Returns the class used to boostrap and open streams.
     *
     * @return the class used to boostrap and open streams
     */
    private Http2StreamBootstrap streamBootstrap() {
        return streamBootstrap;
    }

    /**
     * Returns a new multiplexed channel using the configured factory.
     *
     * @return The new multiplexed channel
     */
    private MultiplexedChannel createMultiplexedChannel(Channel parentChannel, int maxConcurrentStreams) {
        return multiplexedChannelFactory.createChannel(parentChannel, maxConcurrentStreams, streamBootstrap());
    }

    private <T> Promise<T> newPromise() {
        return eventLoopGroup.next().newPromise();
    }

    private Future<?> submitClose() {
        var closeEventLoop = eventLoopGroup.next();
        var closeFinishedPromise = closeEventLoop.<Void>newPromise();
        closeEventLoop.execute(new CloseTask(closeEventLoop, this, closeFinishedPromise));
        return closeFinishedPromise;
    }

    /**
     * Fails the promise using the given exception and then closes and releases the parent channel.
     */
    private void failPromiseAndCloseParent(Promise<Channel> promise, Throwable throwable, Channel parentChannel) {
        LOGGER.warn(parentChannel, "Channel acquisition failed, closing connection", throwable);
        closeAndReleaseParent(parentChannel, null);
        promise.setFailure(throwable);
    }

    /**
     * Caches the multiplexed channel, it can be used to open other streams, up to the configured max concurrent streams
     * value. If the pool is still open, then it succeeds the promise with the given stream. It fails the promise if the
     * pool was closed in the mean time.
     */
    private void cacheMultiplexedChannel(
            Promise<Channel> promise,
            Channel stream,
            MultiplexedChannel multiplexedChannel
    ) {
        if (closed.get()) {
            // We were closed while setting up the stream. Clean up and fail the promise.
            var exception = new TransportException("Connection pool was closed while creating a new stream.");
            failPromiseAndCloseParent(promise, exception, multiplexedChannel.parentChannel());
            return;
        }
        channels.add(multiplexedChannel);
        promise.setSuccess(stream);
    }

    /**
     * Handles when a new channel is acquired. If the operation completes successfully then a promise to create a
     * new H2 stream from the channel will be created. The initialization of the new stream will be handled by
     * {@link InitializeStreamListener}. After that it will be finalized by the {@link FinalizeStream} handler.
     * If any failure is found in between, the resulting promise will be failed and the parent channel closed and
     * released.
     */
    static class NewParentAcquiredListener implements GenericFutureListener<Future<? super Channel>> {
        private final Promise<Channel> resultPromise;
        private final Http2MultiplexedConnectionPool connectionPool;

        NewParentAcquiredListener(
                Promise<Channel> resultPromise,
                Http2MultiplexedConnectionPool connectionPool
        ) {
            this.resultPromise = resultPromise;
            this.connectionPool = connectionPool;
        }

        @Override
        public void operationComplete(Future<? super Channel> parentFuture) {
            if (!parentFuture.isSuccess()) {
                resultPromise.setFailure(parentFuture.cause());
                return;
            }
            var parentChannel = (Channel) parentFuture.getNow();

            // The future is completed by the handler for the H2 settings frame.
            // We block here until completed.
            var throwableRef = new AtomicReference<Throwable>();
            var httpVersion = parentChannel.attr(HTTP_VERSION_FUTURE)
                    .get()
                    .handle(new HttpVersionHandler(throwableRef))
                    .join();
            if (httpVersion == null) {
                var throwable = throwableRef.get();
                failAndClose(parentChannel, throwable);
                return;
            }
            if (httpVersion != HttpVersion.HTTP_2) {
                failAndClose(parentChannel,
                        new TransportException("Unsupported HTTP version: " + httpVersion + "."));
                return;
            }
            parentChannel.attr(HTTP2_MULTIPLEXED_CONNECTION_POOL).set(this.connectionPool);
            acquireStream(parentChannel);
        }

        private void acquireStream(Channel parentChannel) {
            var maxStreams = parentChannel.attr(HTTP2_MAX_CONCURRENT_STREAMS).get();
            var multiplexedChannel = connectionPool.createMultiplexedChannel(parentChannel, maxStreams.intValue());
            parentChannel.attr(HTTP2_MULTIPLEXED_CHANNEL).set(multiplexedChannel);
            var streamPromise = connectionPool.<Channel>newPromise();

            if (tryAcquireOnMultiplexedChannel(multiplexedChannel, streamPromise)) {
                streamPromise.addListener(new FinalizeStream(resultPromise,
                        connectionPool,
                        multiplexedChannel,
                        parentChannel));
            } else {
                failAndClose(parentChannel,
                        new TransportException("Connection was closed while creating a new stream."));
            }
        }

        private boolean tryAcquireOnMultiplexedChannel(
                MultiplexedChannel multiplexedChannel,
                Promise<Channel> streamPromise
        ) {
            var acquirePromise = connectionPool.<Channel>newPromise();
            if (!multiplexedChannel.tryAcquire(acquirePromise)) {
                return false;
            }
            var parentChannel = multiplexedChannel.parentChannel();
            acquirePromise.addListener(new InitializeStreamListener(streamPromise, connectionPool, parentChannel));
            return true;
        }

        private void failAndClose(Channel parentChannel, Throwable throwable) {
            connectionPool.failPromiseAndCloseParent(resultPromise, throwable, parentChannel);
        }
    }

    /**
     * Handler for when the HttpVersion future is complete.
     */
    static class HttpVersionHandler implements BiFunction<HttpVersion, Throwable, HttpVersion> {
        private final AtomicReference<Throwable> throwableRef;

        HttpVersionHandler(AtomicReference<Throwable> throwableRef) {
            this.throwableRef = throwableRef;
        }

        @Override
        public HttpVersion apply(HttpVersion httpVersion, Throwable throwable) {
            if (throwable != null) {
                throwableRef.set(throwable);
                return null;
            }
            return httpVersion;
        }
    }

    /**
     * After acquiring a new stream, if successful, sets the expected attributes on it, and, enqueues a
     * window size increment to match the configured settings. Completes the given promise when done.
     */
    static class InitializeStreamListener implements GenericFutureListener<Future<? super Channel>> {
        private final Promise<Channel> resultPromise;
        private final Http2MultiplexedConnectionPool connectionPool;
        private final Channel parentChannel;

        InitializeStreamListener(
                Promise<Channel> resultPromise,
                Http2MultiplexedConnectionPool connectionPool,
                Channel parentChannel
        ) {
            this.resultPromise = resultPromise;
            this.parentChannel = parentChannel;
            this.connectionPool = connectionPool;
        }

        @Override
        public void operationComplete(Future<? super Channel> streamFuture) {
            if (!streamFuture.isSuccess()) {
                failAndClose(parentChannel, streamFuture.cause());
                return;
            }
            Channel stream = (Channel) streamFuture.getNow();
            stream.attr(CHANNEL_POOL).set(connectionPool);
            tryExpandConnectionWindow(stream.parent());
            resultPromise.setSuccess(stream);
        }

        private void failAndClose(Channel parentChannel, Throwable throwable) {
            connectionPool.failPromiseAndCloseParent(resultPromise, throwable, parentChannel);
        }
    }

    /**
     * Last step in the process of acquiring a new stream.
     */
    static class FinalizeStream implements GenericFutureListener<Future<? super Channel>> {
        private final Promise<Channel> resultPromise;
        private final Http2MultiplexedConnectionPool connectionPool;
        private final MultiplexedChannel multiplexedChannel;
        private final Channel parentChannel;

        FinalizeStream(
                Promise<Channel> resultPromise,
                Http2MultiplexedConnectionPool connectionPool,
                MultiplexedChannel multiplexedChannel,
                Channel parentChannel
        ) {
            this.resultPromise = resultPromise;
            this.connectionPool = connectionPool;
            this.multiplexedChannel = multiplexedChannel;
            this.parentChannel = parentChannel;
        }

        @Override
        public void operationComplete(Future<? super Channel> streamFuture) {
            if (!streamFuture.isSuccess()) {
                failAndClose(parentChannel, streamFuture.cause());
                return;
            }
            var stream = (Channel) streamFuture.getNow();
            // Make sure that exceptions on the connection will remove it from the cache.
            parentChannel.pipeline().addLast(ReleaseOnExceptionHandler.getInstance());
            connectionPool.cacheMultiplexedChannel(resultPromise, stream, multiplexedChannel);
        }

        private void failAndClose(Channel parentChannel, Throwable throwable) {
            connectionPool.failPromiseAndCloseParent(resultPromise, throwable, parentChannel);
        }
    }

    /**
     * Releases the semaphore used to synchronize the creation of new parent channels.
     */
    static class ReleaseSemaphore implements GenericFutureListener<Future<? super Channel>> {
        private final Promise<Channel> resultPromise;
        private final Semaphore semaphore;

        ReleaseSemaphore(Promise<Channel> resultPromise, Semaphore semaphore) {
            this.resultPromise = resultPromise;
            this.semaphore = semaphore;
        }

        @Override
        public void operationComplete(Future<? super Channel> future) {
            semaphore.release();
            if (future.isSuccess()) {
                resultPromise.setSuccess((Channel) future.getNow());
            } else {
                resultPromise.setFailure(future.cause());
            }
        }
    }

    /**
     * Attach the connection pool attribute. This is used to identify the connection pool to which
     * the channel should be released to when closed.
     */
    static class AddConnectionPoolAttribute implements GenericFutureListener<Future<? super Channel>> {
        private final Http2MultiplexedConnectionPool connectionPool;

        AddConnectionPoolAttribute(Http2MultiplexedConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
        }

        @Override
        public void operationComplete(Future<? super Channel> streamFuture) {
            if (streamFuture.isSuccess()) {
                var stream = (Channel) streamFuture.getNow();
                stream.attr(CHANNEL_POOL).set(connectionPool);
            }
        }
    }

    /**
     * Task to close all the parent channels in the multiplexed channel pool.
     */
    static class CloseTask implements Runnable {
        private final EventLoop closeEventLoop;
        private final Http2MultiplexedConnectionPool connectionPool;
        private final Promise<Void> closeFinishedPromise;

        CloseTask(
                EventLoop closeEventLoop,
                Http2MultiplexedConnectionPool connectionPool,
                Promise<Void> closeFinishedPromise
        ) {
            this.closeEventLoop = closeEventLoop;
            this.connectionPool = connectionPool;
            this.closeFinishedPromise = closeFinishedPromise;
        }

        @Override
        public void run() {
            var promiseCombiner = new PromiseCombiner(closeEventLoop);
            var channelsToRemove = new ArrayList<>(connectionPool.channels);
            for (var multiplexedChannel : channelsToRemove) {
                promiseCombiner.add(connectionPool.closeAndReleaseParent(multiplexedChannel.parentChannel(), null));
            }
            var releaseAllChannelsPromise = closeEventLoop.<Void>newPromise();
            promiseCombiner.finish(releaseAllChannelsPromise);
            releaseAllChannelsPromise
                    .addListener(new ReleaseAllChannels(connectionPool.channelPool, closeFinishedPromise));
        }
    }

    /**
     * Listener for the completion of the task to release all the open channels. It will close
     * the channel pool when completed regardless of the outcome.
     */
    static class ReleaseAllChannels implements GenericFutureListener<Future<? super Void>> {
        private final ChannelPool channelPool;
        private final Promise<Void> closeFinishedPromise;

        ReleaseAllChannels(ChannelPool channelPool, Promise<Void> closeFinishedPromise) {
            this.channelPool = channelPool;
            this.closeFinishedPromise = closeFinishedPromise;
        }

        @Override
        public void operationComplete(Future<? super Void> future) {
            if (future.isSuccess()) {
                closeFinishedPromise.setSuccess(null);
            } else {
                closeFinishedPromise.setFailure(future.cause());
            }
            // Close the pool regardless of the outcome of the future.
            channelPool.close();
        }
    }
}
