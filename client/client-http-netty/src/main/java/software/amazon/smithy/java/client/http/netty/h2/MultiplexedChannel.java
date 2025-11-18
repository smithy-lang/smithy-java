/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.client.http.netty.NettyLogger;
import software.amazon.smithy.java.client.http.netty.NettyUtils;

/**
 * An HTTP/2 channel state used to create new streams, and, keep track of each of the streams, and handling the
 * closing of streams and the parent.
 */
final class MultiplexedChannel {
    private static final int MAX_CAS_FAIL_RETRIES = 5;
    private static final NettyLogger LOGGER = NettyLogger.getLogger(MultiplexedChannel.class);
    private final Channel parentChannel;
    private final int maxConcurrentStreams;
    private final Http2StreamBootstrap streamBootstrap;
    private final Map<ChannelId, Http2StreamChannel> children = new ConcurrentHashMap<>();
    private final AtomicInteger acquiredStreams = new AtomicInteger();
    private volatile State state = State.OPEN;
    private volatile int lastStreamId;

    MultiplexedChannel(Channel parentChannel, int maxConcurrentStreams, Http2StreamBootstrap streamBootstrap) {
        this.parentChannel = parentChannel;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.streamBootstrap = streamBootstrap;
    }

    /**
     * Returns true if a new stream could be acquired successfully. This given {@link Promise}
     * will be notified once acquired successfully and failed otherwise.
     * This method will return false when there are no more streams available or when
     * the parent channel is not-longer open to create new streams.
     *
     * @param promise The promise for the acquired channel
     * @return true if the acquisition is successful, false otherwise.
     */
    public boolean tryAcquire(Promise<Channel> promise) {
        if (!tryAcquireClaim()) {
            return false;
        }
        acquireClaimed(promise);
        return true;
    }

    /**
     * Handle a {@link Http2GoAwayFrame} on this connection, preventing new streams from being created on it, and closing any
     * streams newer than the last-stream-id on the go-away frame.
     */
    public void handleGoAway(int lastStreamId, GoAwayException exception) {
        this.lastStreamId = lastStreamId;
        if (state == State.CLOSED) {
            return;
        }
        if (state == State.OPEN) {
            state = State.CLOSED_TO_NEW;
        }
        var childrenToClose = new ArrayList<>(children.values());
        for (var child : childrenToClose) {
            if (child.stream().id() > lastStreamId) {
                child.pipeline().fireUserEventTriggered(exception);
            }
        }
    }

    /**
     * Closes the channel to new streams.
     */
    public void closeToNewStreams() {
        if (state == State.OPEN) {
            state = State.CLOSED_TO_NEW;
        }
    }

    /**
     * Returns true if the channel is not open and there're no more active streams on it.
     *
     * @return true if the channel can be closed and released to the connection pool.
     */
    public boolean canBeClosedAndReleased() {
        return state != State.OPEN && acquiredStreams.get() == 0;
    }

    /**
     * Close all registered streams, and disallow new streams from being created. If an exception is passed
     * it gets delivered to all streams.
     */
    public void close(Throwable t) {
        if (t != null) {
            closeAndExecuteOnStreams(ch -> ch.pipeline().fireExceptionCaught(decorateConnectionException(t)));
        } else {
            closeAndExecuteOnStreams(ChannelOutboundInvoker::close);
        }
    }

    /**
     * Called by the pool to register an acquired stream from this channel.
     *
     * @param stream The stream to register with this channel.
     */
    public void registerStreamChannel(Http2StreamChannel stream) {
        children.put(stream.id(), stream);
    }

    /**
     * Returns the parent channel for this multiplexed channel.
     *
     * @return the parent channel for this multiplexed channel.
     */
    public Channel parentChannel() {
        return parentChannel;
    }

    /**
     * Returns true if this multiplexed channel is closed.
     *
     * @return true if this multiplexed channel is closed
     */
    boolean isClosed() {
        return state == State.CLOSED;
    }

    private boolean tryAcquireClaim() {
        if (state != State.OPEN) {
            LOGGER.debug(parentChannel, "tryAcquireClaim called on a non-open multiplexed channel");
            return false;
        }
        // Cap the max retries when we fail to update acquiredStreams. This should only happen
        // under high contention.
        for (var attempt = 0; attempt < MAX_CAS_FAIL_RETRIES; ++attempt) {
            var streams = acquiredStreams.get();
            if (streams >= maxConcurrentStreams) {
                return false;
            }
            if (!acquiredStreams.compareAndSet(streams, streams + 1)) {
                // try again
                continue;
            }
            return true;
        }
        LOGGER.warn(parentChannel, "Failed to claim a new stream after {} attempts", MAX_CAS_FAIL_RETRIES);
        return false;
    }

    private void acquireClaimed(Promise<Channel> resultPromise) {
        resultPromise.addListener(new ReleaseClaimOnFailure(this));
        if (state != State.OPEN) {
            warnAndFailPromise(resultPromise);
            return;
        }
        var streamFuture = streamBootstrap.openStream(parentChannel);
        streamFuture.addListener(new AcquireStreamHandler(this, resultPromise));
    }

    private void warnAndFailPromise(Promise<Channel> promise) {
        String message;
        if (state == State.CLOSED_TO_NEW) {
            message =
                    String.format(
                            "Connection %s received GOAWAY with lastStreamId %d. Unable to open new "
                                    + "streams on this connection.",
                            parentChannel,
                            lastStreamId);
        } else {
            message = String.format("Connection %s was closed while acquiring new stream.", parentChannel);
        }
        LOGGER.warn(parentChannel, message);
        promise.setFailure(new TransportException(message));
    }

    private void closeAndExecuteOnStreams(Consumer<Channel> streamConsumer) {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        var streamsToClose = new ArrayList<>(children.values());
        children.clear();
        for (var streamChannel : streamsToClose) {
            streamConsumer.accept(streamChannel);
        }
    }

    public void closeAndReleaseStream(Channel stream) {
        stream.close();
        children.remove(stream.id());
        releaseClaim();
    }

    private Throwable decorateConnectionException(Throwable t) {
        var message = "Exception caught on channel: " + parentChannel + ". All streams will be closed";
        return new TransportException(message, t);
    }

    private void releaseClaim() {
        if (acquiredStreams.decrementAndGet() < 0) {
            NettyUtils.Asserts.shouldNotBeReached(parentChannel,
                    "Attempt to release more streams than acquired, " +
                            "this SHOULD never happen.");
            acquiredStreams.incrementAndGet();
        }
    }

    enum State {
        /**
         * The channel is open.
         */
        OPEN,
        /**
         * The channel is closed for new streams. This will be set whenever an I/O exception
         * is caught in the channel.
         */
        CLOSED_TO_NEW,
        /**
         * The channel is closed.
         */
        CLOSED
    }

    /**
     * Listener that releases a claim whenever the future completes unsuccessfully.
     */
    static class ReleaseClaimOnFailure implements GenericFutureListener<Future<? super Channel>> {
        private final MultiplexedChannel multiplexedChannel;

        ReleaseClaimOnFailure(MultiplexedChannel multiplexedChannel) {
            this.multiplexedChannel = multiplexedChannel;
        }

        @Override
        public void operationComplete(Future<? super Channel> future) {
            if (!future.isSuccess()) {
                multiplexedChannel.releaseClaim();
            }
        }
    }

    /**
     * Listener for the result of {@link Http2StreamChannelBootstrap#open()}. Configures  the stream channel with the
     * inbound and outbound adapters to convert from and to HTTP/2.
     */
    static class AcquireStreamHandler implements GenericFutureListener<Future<? super Http2StreamChannel>> {
        private final MultiplexedChannel multiplexedChannel;
        private final Promise<Channel> resultPromise;

        AcquireStreamHandler(MultiplexedChannel multiplexedChannel, Promise<Channel> resultPromise) {
            this.multiplexedChannel = multiplexedChannel;
            this.resultPromise = resultPromise;
        }

        @Override
        public void operationComplete(Future<? super Http2StreamChannel> future) {
            if (!future.isSuccess()) {
                resultPromise.setFailure(future.cause());
                return;
            }
            var stream = (Http2StreamChannel) future.getNow();
            var pipeline = stream.pipeline();
            pipeline.addLast(new Http2ToHttpInboundAdapter());
            pipeline.addLast(new HttpToHttp2OutboundAdapter());
            pipeline.addLast(Http2StreamExceptionHandler.getInstance());
            multiplexedChannel.registerStreamChannel(stream);
            resultPromise.setSuccess(stream);
        }
    }
}
