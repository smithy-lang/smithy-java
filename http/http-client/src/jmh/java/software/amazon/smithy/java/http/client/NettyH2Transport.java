/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Netty-backed HTTP/2 transport that exposes blocking {@code send(HttpRequest)} and streams
 * request body uploads and response body downloads.
 *
 * <p>Streaming behavior:
 * <ul>
 *   <li><b>Request body:</b> Caller VT reads chunks from {@code request.body().asInputStream()}
 *       and writes each chunk to the stream channel. Respects {@code channel.isWritable()} for
 *       backpressure — VT parks when the outbound buffer is full.</li>
 *   <li><b>Response body:</b> Returned as a {@link DataStream} wrapping a blocking
 *       {@link InputStream} that pulls from a bounded queue fed by the Netty handler. Headers
 *       are returned to the caller as soon as the response HEADERS frame arrives; body bytes
 *       stream as they arrive.</li>
 * </ul>
 */
final class NettyH2Transport implements AutoCloseable {

    // Chunk size for streamed uploads.
    private static final int UPLOAD_CHUNK = 64 * 1024;
    // Queue depth for response body chunks (backpressure: reader-side).
    private static final int RESPONSE_QUEUE_CAPACITY = 64;
    // Marker in response queue to signal end-of-stream.
    private static final ByteBuf EOS_MARKER = Unpooled.EMPTY_BUFFER;

    private final EventLoopGroup group;
    private final Channel channel;

    NettyH2Transport(String host, int port) throws Exception {
        this.group = new NioEventLoopGroup(1);

        SslContext sslCtx = SslContextBuilder.forClient()
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2))
                .build();

        var frameCodec = Http2FrameCodecBuilder.forClient()
                .initialSettings(Http2Settings.defaultSettings()
                        .initialWindowSize(16 * 1024 * 1024)
                        .maxConcurrentStreams(4096))
                .build();

        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                // Standard watermarks for backpressure signaling via isWritable().
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 256 * 1024))
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(sslCtx.newHandler(ch.alloc(), host, port));
                        ch.pipeline().addLast(frameCodec);
                        ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel ignored) {}
                        }));
                    }
                });

        this.channel = b.connect(host, port).sync().channel();
    }

    HttpResponse send(HttpRequest request) throws IOException {
        Http2StreamChannel stream;
        try {
            stream = new Http2StreamChannelBootstrap(channel).open().sync().getNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted opening H2 stream", e);
        }

        CompletableFuture<HttpResponse> headersFuture = new CompletableFuture<>();
        LinkedBlockingQueue<ByteBuf> bodyQueue = new LinkedBlockingQueue<>(RESPONSE_QUEUE_CAPACITY);
        ResponseHandler responseHandler = new ResponseHandler(headersFuture, bodyQueue);
        stream.pipeline().addLast(responseHandler);

        Http2Headers nettyHeaders = toNettyHeaders(request);
        boolean hasBody = request.body() != null && request.body().contentLength() != 0;

        // Submit headers on the event loop. For bodyless requests, this also closes the stream.
        stream.eventLoop().execute(() -> {
            stream.write(new DefaultHttp2HeadersFrame(nettyHeaders, !hasBody));
            if (!hasBody) {
                stream.flush();
            }
        });

        // Stream the request body (on caller VT) if present, respecting backpressure.
        if (hasBody) {
            try (InputStream in = request.body().asInputStream()) {
                streamRequestBody(stream, in);
            } catch (IOException e) {
                stream.close();
                throw e;
            }
        }

        // Wait for headers (not the whole body).
        HttpResponse headResponse;
        try {
            headResponse = headersFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for response headers", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("Request failed", cause);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IOException("Request timed out waiting for headers", e);
        }

        // Replace the body with a streaming InputStream pulling from the queue.
        var bodyStream = new ResponseBodyInputStream(bodyQueue, responseHandler);
        return headResponse.toModifiable().setBody(DataStream.ofInputStream(bodyStream)).toUnmodifiable();
    }

    /**
     * Read the request body in chunks and write to the stream channel. Blocks the VT on
     * backpressure (channel not writable).
     */
    private void streamRequestBody(Http2StreamChannel stream, InputStream in) throws IOException {
        byte[] buf = new byte[UPLOAD_CHUNK];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                // End-of-stream: send empty DATA with endStream=true
                stream.eventLoop().execute(() ->
                        stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, true)));
                return;
            }
            if (n == 0) continue;

            // Backpressure: park until the channel is writable again.
            while (!stream.isWritable()) {
                LockSupport.parkNanos(100_000); // 100us hint - parkNanos handles spurious fine
                if (!stream.isOpen()) {
                    throw new IOException("Stream closed while waiting for writability");
                }
            }

            // Allocate a direct pooled buffer from the channel's allocator and copy chunk in.
            ByteBuf out = stream.alloc().buffer(n);
            out.writeBytes(buf, 0, n);
            stream.eventLoop().execute(() ->
                    stream.writeAndFlush(new DefaultHttp2DataFrame(out, false)));
        }
    }

    private static Http2Headers toNettyHeaders(HttpRequest request) {
        var uri = request.uri();
        String path = uri.getPath();
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        var headers = new DefaultHttp2Headers()
                .method(request.method())
                .path(path)
                .scheme(uri.getScheme())
                .authority(uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
        for (Map.Entry<String, java.util.List<String>> e : request.headers().map().entrySet()) {
            String name = e.getKey().toLowerCase(Locale.ROOT);
            for (String v : e.getValue()) {
                headers.add(name, v);
            }
        }
        return headers;
    }

    @Override
    public void close() {
        try {
            channel.close().sync();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        group.shutdownGracefully();
    }

    /**
     * Completes {@code headersFuture} as soon as HEADERS arrives. DATA frames are pushed onto
     * {@code bodyQueue}. An EOS marker is enqueued when the stream ends.
     */
    private static final class ResponseHandler extends SimpleChannelInboundHandler<Http2StreamFrame> {
        private final CompletableFuture<HttpResponse> headersFuture;
        private final LinkedBlockingQueue<ByteBuf> bodyQueue;
        private int status;
        volatile Throwable error;

        ResponseHandler(CompletableFuture<HttpResponse> headersFuture, LinkedBlockingQueue<ByteBuf> bodyQueue) {
            this.headersFuture = headersFuture;
            this.bodyQueue = bodyQueue;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) throws Exception {
            if (msg instanceof Http2HeadersFrame hf) {
                var s = hf.headers().status();
                if (s != null) {
                    status = Integer.parseInt(s.toString());
                }
                var response = HttpResponse.create()
                        .setHttpVersion(HttpVersion.HTTP_2)
                        .setStatusCode(status)
                        .setHeaders(HttpHeaders.ofModifiable())
                        .setBody(DataStream.ofEmpty()); // replaced by send()
                headersFuture.complete(response);
                if (hf.isEndStream()) {
                    bodyQueue.put(EOS_MARKER);
                }
            } else if (msg instanceof Http2DataFrame df) {
                ByteBuf content = df.content();
                if (content.readableBytes() > 0) {
                    bodyQueue.put(content.retain());
                }
                if (df.isEndStream()) {
                    bodyQueue.put(EOS_MARKER);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            error = cause;
            if (!headersFuture.isDone()) {
                headersFuture.completeExceptionally(cause);
            }
            try {
                bodyQueue.put(EOS_MARKER);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            try {
                bodyQueue.put(EOS_MARKER);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Blocking InputStream that pulls {@link ByteBuf} chunks from the handler's queue.
     * Releases each chunk as it's consumed.
     */
    private static final class ResponseBodyInputStream extends InputStream {
        private final LinkedBlockingQueue<ByteBuf> queue;
        private final ResponseHandler handler;
        private ByteBuf current;
        private boolean done;

        ResponseBodyInputStream(LinkedBlockingQueue<ByteBuf> queue, ResponseHandler handler) {
            this.queue = queue;
            this.handler = handler;
        }

        private boolean ensure() throws IOException {
            while (current == null || !current.isReadable()) {
                releaseCurrent();
                if (done) return false;
                try {
                    ByteBuf next = queue.take();
                    if (next == EOS_MARKER) {
                        done = true;
                        if (handler.error != null) {
                            throw new IOException("Response stream failed", handler.error);
                        }
                        return false;
                    }
                    current = next;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted reading response body", e);
                }
            }
            return true;
        }

        private void releaseCurrent() {
            if (current != null) {
                current.release();
                current = null;
            }
        }

        @Override
        public int read() throws IOException {
            if (!ensure()) return -1;
            return current.readByte() & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (!ensure()) return -1;
            int n = Math.min(len, current.readableBytes());
            current.readBytes(b, off, n);
            return n;
        }

        @Override
        public void close() {
            releaseCurrent();
            while (!done) {
                ByteBuf next = queue.poll();
                if (next == null) break;
                if (next == EOS_MARKER) {
                    done = true;
                } else {
                    next.release();
                }
            }
        }
    }
}
