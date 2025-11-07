/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;

/**
 * Translates HTTP/1.1 Netty objects to the corresponding HTTP/2 frame objects. Much of this was lifted from
 * {@link HttpToHttp2ConnectionHandler} but since that actually encodes to the raw bytes it doesn't play nice with
 * {@link Http2MultiplexCodec} which expects the frame objects.
 */
@ChannelHandler.Sharable
final class HttpToHttp2OutboundAdapter extends ChannelOutboundHandlerAdapter {

    public HttpToHttp2OutboundAdapter() {}

    /**
     * Handles conversion of {@link HttpMessage} and {@link HttpContent} to HTTP/2 frames.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof HttpMessage || msg instanceof HttpContent)) {
            ctx.write(msg, promise);
            return;
        }

        var release = true;
        var promiseAggregator = new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        try {
            var endStream = false;
            if (msg instanceof HttpMessage httpMsg) {

                // Convert and write the headers.
                Http2Headers http2Headers = HttpConversionUtil.toHttp2Headers(httpMsg, false);
                endStream = msg instanceof FullHttpMessage fm && !fm.content().isReadable();
                ctx.write(new DefaultHttp2HeadersFrame(http2Headers), promiseAggregator.newPromise());
            }

            if (!endStream && msg instanceof HttpContent) {
                var isLastContent = false;
                HttpHeaders trailers = EmptyHttpHeaders.INSTANCE;
                Http2Headers http2Trailers = EmptyHttp2Headers.INSTANCE;
                if (msg instanceof LastHttpContent lastContent) {
                    isLastContent = true;

                    // Convert any trailing headers.
                    trailers = lastContent.trailingHeaders();
                    http2Trailers = HttpConversionUtil.toHttp2Headers(trailers, false);
                }

                // Write the data
                var content = ((HttpContent) msg).content();
                endStream = isLastContent && trailers.isEmpty();
                release = false;
                ctx.write(new DefaultHttp2DataFrame(content, endStream), promiseAggregator.newPromise());

                if (!trailers.isEmpty()) {
                    // Write trailing headers.
                    ctx.write(new DefaultHttp2HeadersFrame(http2Trailers, true), promiseAggregator.newPromise());

                }
                ctx.flush();
            }
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
        } finally {
            if (release) {
                ReferenceCountUtil.release(msg);
            }
            promiseAggregator.doneAllocatingPromises();
        }
    }

    /**
     * Provides the ability to associate the outcome of multiple {@link ChannelPromise}
     * objects into a single {@link ChannelPromise} object.
     */
    static final class SimpleChannelPromiseAggregator extends DefaultChannelPromise {
        private final ChannelPromise promise;
        private int expectedCount;
        private int doneCount;
        private Throwable lastFailure;
        private boolean doneAllocating;

        SimpleChannelPromiseAggregator(ChannelPromise promise, Channel c, EventExecutor e) {
            super(c, e);
            assert promise != null && !promise.isDone();
            this.promise = promise;
        }

        /**
         * Allocate a new promise which will be used to aggregate the overall success of this promise aggregator.
         *
         * @return A new promise which will be aggregated.
         * {@code null} if {@link #doneAllocatingPromises()} was previously called.
         */
        public ChannelPromise newPromise() {
            assert !doneAllocating : "Done allocating. No more promises can be allocated.";
            ++expectedCount;
            return this;
        }

        /**
         * Signify that no more {@link #newPromise()} allocations will be made.
         * The aggregation can not be successful until this method is called.
         *
         * @return The promise that is the aggregation of all promises allocated with {@link #newPromise()}.
         */
        public ChannelPromise doneAllocatingPromises() {
            if (!doneAllocating) {
                doneAllocating = true;
                if (doneCount == expectedCount || expectedCount == 0) {
                    return setPromise();
                }
            }
            return this;
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            if (allowFailure()) {
                ++doneCount;
                lastFailure = cause;
                if (allPromisesDone()) {
                    return tryPromise();
                }
                // We break the interface a bit here.
                // Multiple failure events can be processed without issue because this is an aggregation.
                return true;
            }
            return false;
        }

        /**
         * Fail this object if it has not already been failed.
         * <p>
         * This method will NOT throw an {@link IllegalStateException} if called multiple times
         * because that may be expected.
         */
        @Override
        public ChannelPromise setFailure(Throwable cause) {
            if (allowFailure()) {
                ++doneCount;
                lastFailure = cause;
                if (allPromisesDone()) {
                    return setPromise();
                }
            }
            return this;
        }

        @Override
        public ChannelPromise setSuccess(Void result) {
            if (awaitingPromises()) {
                ++doneCount;
                if (allPromisesDone()) {
                    setPromise();
                }
            }
            return this;
        }

        @Override
        public boolean trySuccess(Void result) {
            if (awaitingPromises()) {
                ++doneCount;
                if (allPromisesDone()) {
                    return tryPromise();
                }
                // We break the interface a bit here.
                // Multiple success events can be processed without issue because this is an aggregation.
                return true;
            }
            return false;
        }

        private boolean allowFailure() {
            return awaitingPromises() || expectedCount == 0;
        }

        private boolean awaitingPromises() {
            return doneCount < expectedCount;
        }

        private boolean allPromisesDone() {
            return doneCount == expectedCount && doneAllocating;
        }

        private ChannelPromise setPromise() {
            if (lastFailure == null) {
                promise.setSuccess();
                return super.setSuccess(null);
            } else {
                promise.setFailure(lastFailure);
                return super.setFailure(lastFailure);
            }
        }

        private boolean tryPromise() {
            if (lastFailure == null) {
                promise.trySuccess();
                return super.trySuccess(null);
            } else {
                promise.tryFailure(lastFailure);
                return super.tryFailure(lastFailure);
            }
        }
    }
}
