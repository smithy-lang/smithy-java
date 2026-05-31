/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Executes an HTTP/1.1 request on a Netty channel. One request per channel at a time
 * (no pipelining). Supports streaming request and response bodies via a single-slot inline
 * handoff to the caller VT (see {@link ResponseBodyChannel}).
 */
final class H1Executor {

    private static final int UPLOAD_CHUNK = 64 * 1024;
    private static final int UPLOAD_BATCH_CHUNKS = 4;
    private static final int BODY_HIGH_WATER = 32;
    private static final int BODY_LOW_WATER = 8;

    private H1Executor() {}

    static software.amazon.smithy.java.http.api.HttpResponse execute(
            NettyConnectionPool pool,
            NettyConnection conn,
            HttpRequest request,
            long requestTimeoutMs
    ) throws IOException {
        Channel channel = conn.channel;
        var headersFuture = new CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse>();
        var error = new AtomicReference<Throwable>();
        var responseComplete = new AtomicBoolean(false);
        var responseStarted = new AtomicBoolean(false);
        var cleanupDone = new AtomicBoolean(false);
        var handlerRef = new AtomicReference<ResponseHandler>();
        Runnable onClose = () -> {
            if (!cleanupDone.compareAndSet(false, true)) {
                return;
            }
            channel.eventLoop().execute(() -> {
                ResponseHandler h = handlerRef.get();
                if (h != null && channel.pipeline().context(h) != null) {
                    channel.pipeline().remove(h);
                }
                // Reuse only a fully-drained, healthy connection; otherwise dispose so no stale
                // response bytes leak into the next request on a reused channel.
                if (responseComplete.get() && error.get() == null && conn.isActive()) {
                    // Restore autoRead before pooling: a large response may have left it paused
                    // (ResponseBodyChannel pauses at high-water; an early close never resumes it).
                    // An idle pooled connection with autoRead=false never registers OP_READ, so a
                    // later server FIN is never observed and the connection rots in the pool stale.
                    channel.config().setAutoRead(true);
                    pool.release(conn);
                } else {
                    pool.dispose(conn);
                }
            });
        };
        var bodyChannel = new ResponseBodyChannel(
                error,
                resume -> channel.eventLoop().execute(() -> channel.config().setAutoRead(resume)),
                onClose,
                BODY_HIGH_WATER,
                BODY_LOW_WATER);
        ResponseHandler handler =
                new ResponseHandler(headersFuture, bodyChannel, error, responseComplete, responseStarted);
        handlerRef.set(handler);
        // Add with an auto-generated name (not a fixed "h1-response"): even if a prior handler were
        // ever left attached, this cannot throw the duplicate-name IllegalArgumentException that
        // previously crashed every reused H1 connection.
        channel.pipeline().addLast(handler);

        boolean hasBody = request.body() != null && request.body().contentLength() != 0;
        long contentLength = hasBody ? request.body().contentLength() : 0;

        var nettyReq = NettyUtils.buildH1Request(
                request,
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(request.method()),
                buildRequestLine(request));
        if (hasBody && contentLength > 0) {
            nettyReq.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
        } else if (hasBody) {
            nettyReq.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
        nettyReq.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        channel.eventLoop().execute(() -> channel.write(nettyReq));

        if (hasBody) {
            try {
                streamRequestBody(channel, request.body());
            } catch (IOException e) {
                channel.close();
                // If the connection was reused from the pool and no response has started, the most
                // likely cause is a keep-alive the server had already closed: the request never
                // reached a responding server, so it is safe to retry on a fresh connection.
                throw maybeStale(conn, responseStarted, e);
            } finally {
                request.body().close();
            }
        } else {
            channel.eventLoop().execute(() -> channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT));
        }

        software.amazon.smithy.java.http.api.HttpResponse headResponse;
        try {
            headResponse = requestTimeoutMs > 0
                    ? headersFuture.get(requestTimeoutMs, TimeUnit.MILLISECONDS)
                    : headersFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.close();
            throw new IOException("Interrupted waiting for H1 response headers", e);
        } catch (ExecutionException e) {
            channel.close();
            Throwable cause = e.getCause();
            if (conn.fromReuse && !responseStarted.get()) {
                throw new StaleConnectionException("Reused H1 connection closed before response", cause);
            }
            if (cause instanceof IOException io)
                throw io;
            throw new IOException("H1 request failed", cause);
        } catch (TimeoutException e) {
            channel.close();
            throw new IOException("Request timed out waiting for H1 headers", e);
        }

        return headResponse.toModifiable()
                .setBody(DataStream.ofInputStream(bodyChannel))
                .toUnmodifiable();
    }

    /**
     * Classify a request-body write failure. If the connection was reused from the pool and no
     * response byte has been received, treat it as a stale keep-alive that the server had already
     * closed — safe to retry on a fresh connection. Otherwise propagate the original IOException.
     */
    private static IOException maybeStale(
            NettyConnection conn,
            AtomicBoolean responseStarted,
            IOException original
    ) {
        if (conn.fromReuse && !responseStarted.get()) {
            return new StaleConnectionException(
                    "Reused H1 connection closed while sending request body",
                    original);
        }
        return original;
    }

    private static String buildRequestLine(HttpRequest request) {
        var uri = request.uri();
        String path = uri.getPath();
        if (path == null || path.isEmpty())
            path = "/";
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        return path;
    }

    private static void streamRequestBody(Channel channel, DataStream body) throws IOException {
        // Stream the body straight through DataStream.writeTo(OutputStream): for in-memory,
        // replayable bodies (ByteBufferDataStream, AwsChunkedDataStream — the S3 upload body)
        // this writes the backing array nearly directly with a single pass, rather than the old
        // path that probed asChannel() (which materialized the entire encoded body into a
        // ByteArrayOutputStream just to discard it because it is not a ScatteringByteChannel) and
        // then called asInputStream() to materialize it a SECOND time. The OutputStream adapter
        // below batches into Netty ByteBufs and applies the same writability backpressure.
        var sink = new ChannelBatchingOutputStream(channel);
        try {
            body.writeTo(sink);
            sink.finish();
        } catch (IOException | RuntimeException e) {
            sink.discard();
            throw e;
        }
    }

    /**
     * An {@link OutputStream} that batches written bytes into {@link ByteBuf} chunks and hands them
     * to the event loop, applying writability backpressure between batches. Buffers handed to the
     * event loop are owned by it; buffers still held here are released on {@link #discard()}.
     */
    private static final class ChannelBatchingOutputStream extends OutputStream {
        private final Channel channel;
        private final List<ByteBuf> batch = new ArrayList<>(UPLOAD_BATCH_CHUNKS);
        private ByteBuf current;

        ChannelBatchingOutputStream(Channel channel) {
            this.channel = channel;
        }

        @Override
        public void write(int b) throws IOException {
            ensureCurrent(1).writeByte(b);
            maybeFlushCurrent();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int remaining = len;
            int pos = off;
            while (remaining > 0) {
                int n = Math.min(remaining, UPLOAD_CHUNK);
                ensureCurrent(n).writeBytes(b, pos, n);
                pos += n;
                remaining -= n;
                maybeFlushCurrent();
            }
        }

        private ByteBuf ensureCurrent(int minWritable) throws IOException {
            if (current == null) {
                awaitWritable(channel, batch);
                current = channel.alloc().buffer(Math.max(UPLOAD_CHUNK, minWritable));
            }
            return current;
        }

        private void maybeFlushCurrent() throws IOException {
            if (current != null && !current.isWritable()) {
                batch.add(current);
                current = null;
                if (batch.size() >= UPLOAD_BATCH_CHUNKS) {
                    flushBatch(channel, batch, false);
                }
            }
        }

        void finish() throws IOException {
            if (current != null && current.isReadable()) {
                batch.add(current);
                current = null;
            } else if (current != null) {
                current.release();
                current = null;
            }
            flushBatch(channel, batch, true);
        }

        void discard() {
            if (current != null) {
                current.release();
                current = null;
            }
            releaseAll(batch);
        }
    }

    private static void awaitWritable(Channel channel, List<ByteBuf> batch) throws IOException {
        while (!channel.isWritable()) {
            flushBatch(channel, batch, false);
            LockSupport.parkNanos(100_000);
            if (!channel.isOpen()) {
                throw new IOException("Channel closed while waiting for writability");
            }
        }
    }

    private static void releaseAll(List<ByteBuf> batch) {
        for (ByteBuf b : batch) {
            b.release();
        }
        batch.clear();
    }

    private static void flushBatch(Channel channel, List<ByteBuf> batch, boolean endStream) {
        if (batch.isEmpty()) {
            if (endStream) {
                channel.eventLoop().execute(() -> channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT));
            }
            return;
        }

        ByteBuf[] bufs = batch.toArray(ByteBuf[]::new);
        batch.clear();
        channel.eventLoop().execute(() -> {
            for (ByteBuf buf : bufs) {
                channel.write(new DefaultHttpContent(buf));
            }
            if (endStream) {
                channel.write(LastHttpContent.EMPTY_LAST_CONTENT);
            }
            channel.flush();
        });
    }

    private static final class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> headersFuture;
        private final ResponseBodyChannel body;
        private final AtomicReference<Throwable> error;
        private final AtomicBoolean responseComplete;
        private final AtomicBoolean responseStarted;

        ResponseHandler(
                CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> headersFuture,
                ResponseBodyChannel body,
                AtomicReference<Throwable> error,
                AtomicBoolean responseComplete,
                AtomicBoolean responseStarted
        ) {
            this.headersFuture = headersFuture;
            this.body = body;
            this.error = error;
            this.responseComplete = responseComplete;
            this.responseStarted = responseStarted;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpResponse nettyResp) {
                // The server has begun replying: this request is no longer safe to blindly retry.
                responseStarted.set(true);
                var response = software.amazon.smithy.java.http.api.HttpResponse.create()
                        .setHttpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1)
                        .setStatusCode(nettyResp.status().code())
                        .setHeaders(NettyUtils.fromH1Headers(nettyResp.headers()))
                        .setBody(DataStream.ofEmpty());
                headersFuture.complete(response);
            }
            if (msg instanceof HttpContent content) {
                ByteBuf c = content.content();
                if (c.readableBytes() > 0) {
                    body.publish(c.retain());
                }
                if (msg instanceof LastHttpContent) {
                    // Full response received: the connection is now safe to reuse once the caller
                    // closes the body stream (see the onClose wired in execute()).
                    responseComplete.set(true);
                    body.publishEos();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            error.compareAndSet(null, cause);
            if (!headersFuture.isDone()) {
                headersFuture.completeExceptionally(cause);
            }
            body.publishError(cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!headersFuture.isDone()) {
                var cause = error.get() != null
                        ? error.get()
                        : new IOException("Connection closed before response headers");
                error.compareAndSet(null, cause);
                headersFuture.completeExceptionally(cause);
            }
            body.publishEos();
        }
    }
}
