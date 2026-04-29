/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
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
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone Netty-based benchmark server.
 *
 * <p>Runs in a separate process from JMH benchmarks to get clean flame graphs
 * without Netty code polluting the profile.
 *
 * <p>Writes port information to a file for the benchmark to read:
 * <pre>
 * h1Port=12345
 * h2Port=12346
 * h2cPort=12347
 * </pre>
 *
 * <p>Usage:
 * <pre>
 * java -cp ... software.amazon.smithy.java.http.client.BenchmarkServer [port-file]
 * </pre>
 */
public final class BenchmarkServer {

    private static final byte[] CONTENT = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MB_CONTENT = new byte[1024 * 1024]; // 1MB for large transfer tests
    private static final byte[] MB10_CONTENT = new byte[10 * 1024 * 1024]; // 10MB for bulk transfer tests

    // Fixed ports for benchmark server (avoids dynamic port discovery complexity)
    public static final int DEFAULT_H1_PORT = 18080;
    public static final int DEFAULT_H2_PORT = 18443;
    public static final int DEFAULT_H2C_PORT = 18081;

    // HTTP/2 settings - tunable for benchmarking
    private static final int H2_MAX_CONCURRENT_STREAMS = 20000;
    private static final int H2_INITIAL_WINDOW_SIZE = 1024 * 1024 * 2;
    private static final int H2_MAX_FRAME_SIZE = 1024 * 64;
    // Additional connection-level receive window credit for h2c.
    // Without this, concurrent uploads can bottleneck on the RFC default 64KB connection window.
    private static final int H2_CONNECTION_WINDOW_INCREMENT = 64 * 1024 * 1024;

    // HTTP/2 TLS settings (slightly more conservative)
    private static final int H2_TLS_MAX_CONCURRENT_STREAMS = 10000;
    private static final int H2_TLS_INITIAL_WINDOW_SIZE = 1024 * 1024;
    // Additional connection-level receive window credit beyond the RFC default 64KB.
    // With default 64KB, 10 concurrent 1MB uploads must serialize WINDOW_UPDATE roundtrips.
    // Bumping by 64MB leaves only per-stream flow control as a throttling factor.
    private static final int H2_TLS_CONNECTION_WINDOW_INCREMENT = 64 * 1024 * 1024;
    private static final AtomicLong GET_MB_BYTES_SENT = new AtomicLong();
    private static final AtomicLong PUT_MB_BYTES_RECEIVED = new AtomicLong();
    private static final AtomicLong GET_MB_REQUESTS = new AtomicLong();
    private static final AtomicLong PUT_MB_REQUESTS = new AtomicLong();
    private static final ConcurrentHashMap<String, IoStatsAccumulator> RUN_IO_STATS = new ConcurrentHashMap<>();

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final Channel h1ServerChannel;
    private final Channel h2ServerChannel;
    private final Channel h2cServerChannel;
    private final int h1Port;
    private final int h2Port;
    private final int h2cPort;

    public BenchmarkServer() throws Exception {
        this(DEFAULT_H1_PORT, DEFAULT_H2_PORT, DEFAULT_H2C_PORT);
    }

    public BenchmarkServer(int h1Port, int h2Port, int h2cPort) throws Exception {
        this.h1Port = h1Port;
        this.h2Port = h2Port;
        this.h2cPort = h2cPort;

        int cores = Runtime.getRuntime().availableProcessors();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(cores * 4);

        // Start HTTP/1.1 server
        h1ServerChannel = startH1Server(h1Port);

        // Start HTTP/2 server with TLS (h2)
        h2ServerChannel = startH2Server(h2Port);

        // Start HTTP/2 cleartext server (h2c) - prior knowledge only
        h2cServerChannel = startH2cServer(h2cPort);
    }

    public int getH1Port() {
        return h1Port;
    }

    public int getH2Port() {
        return h2Port;
    }

    public int getH2cPort() {
        return h2cPort;
    }

    private static void resetIoStats() {
        GET_MB_BYTES_SENT.set(0);
        PUT_MB_BYTES_RECEIVED.set(0);
        GET_MB_REQUESTS.set(0);
        PUT_MB_REQUESTS.set(0);
        RUN_IO_STATS.clear();
    }

    private static String extractPath(String uri) {
        try {
            URI parsed = URI.create(uri);
            String path = parsed.getPath();
            if (path != null && !path.isEmpty()) {
                return path;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to raw request-target handling below.
        }
        int query = uri.indexOf('?');
        if (query >= 0) {
            uri = uri.substring(0, query);
        }
        return uri;
    }

    private static boolean pathMatches(String uri, String path) {
        return path.contentEquals(extractPath(uri));
    }

    private static boolean pathMatches(CharSequence uri, String path) {
        return uri != null && path.contentEquals(extractPath(uri.toString()));
    }

    private static String extractQueryParam(String uri, String name) {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0 || queryStart == uri.length() - 1) {
            return null;
        }
        int index = queryStart + 1;
        while (index < uri.length()) {
            int nextAmp = uri.indexOf('&', index);
            if (nextAmp < 0) {
                nextAmp = uri.length();
            }
            int equals = uri.indexOf('=', index);
            if (equals > index && equals < nextAmp && uri.regionMatches(index, name, 0, name.length())) {
                return uri.substring(equals + 1, nextAmp);
            }
            index = nextAmp + 1;
        }
        return null;
    }

    private static IoStatsAccumulator ioStatsFor(String uri) {
        String runId = extractQueryParam(uri, "runId");
        if (runId == null || runId.isEmpty()) {
            return null;
        }
        return RUN_IO_STATS.computeIfAbsent(runId, ignored -> new IoStatsAccumulator());
    }

    private static byte[] statsJson(String uri) {
        IoStatsAccumulator runStats = ioStatsFor(uri);
        if (runStats == null) {
            return statsJson();
        }
        return statsJson(runStats.getMbRequests.get(),
                runStats.getMbBytesSent.get(),
                runStats.putMbRequests.get(),
                runStats.putMbBytesReceived.get());
    }

    private static byte[] ioStatsJson() {
        return ioStatsJson(GET_MB_REQUESTS.get(),
                GET_MB_BYTES_SENT.get(),
                PUT_MB_REQUESTS.get(),
                PUT_MB_BYTES_RECEIVED.get());
    }

    private static byte[] statsJson() {
        return statsJson(GET_MB_REQUESTS.get(),
                GET_MB_BYTES_SENT.get(),
                PUT_MB_REQUESTS.get(),
                PUT_MB_BYTES_RECEIVED.get());
    }

    private static byte[] ioStatsJson(long getRequests, long getBytesSent, long putRequests, long putBytesReceived) {
        String json = "{"
                + "\"getMbRequests\":" + getRequests + ","
                + "\"getMbBytesSent\":" + getBytesSent + ","
                + "\"putMbRequests\":" + putRequests + ","
                + "\"putMbBytesReceived\":" + putBytesReceived
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] statsJson(long getRequests, long getBytesSent, long putRequests, long putBytesReceived) {
        String json = "{"
                + "\"settings\":{"
                + "\"maxConcurrentStreams\":" + H2_MAX_CONCURRENT_STREAMS + ","
                + "\"initialWindowSize\":" + H2_INITIAL_WINDOW_SIZE + ","
                + "\"maxFrameSize\":" + H2_MAX_FRAME_SIZE
                + "},"
                + "\"io\":{"
                + "\"getMbRequests\":" + getRequests + ","
                + "\"getMbBytesSent\":" + getBytesSent + ","
                + "\"putMbRequests\":" + putRequests + ","
                + "\"putMbBytesReceived\":" + putBytesReceived
                + "}"
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private Channel startH1Server(int port) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 16384)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(8192))
                                .addLast(new Http1RequestHandler());
                    }
                });

        return b.bind(port).sync().channel();
    }

    private Channel startH2Server(int port) throws Exception {
        // Create self-signed certificate (uses BouncyCastle)
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        // Build SSL context with ALPN for HTTP/2
        SslContext sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 16384)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
                        ch.pipeline().addLast(new Http2OrHttpHandler());
                    }
                });

        return b.bind(port).sync().channel();
    }

    /**
     * Start HTTP/2 cleartext server (h2c) with prior knowledge.
     * No TLS, no upgrade - client must speak HTTP/2 directly.
     */
    private Channel startH2cServer(int port) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 65536)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_RCVBUF, 2097152)
                .childOption(ChannelOption.SO_SNDBUF, 2097152)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32768, 65536))
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        var settings = io.netty.handler.codec.http2.Http2Settings.defaultSettings()
                                .maxConcurrentStreams(H2_MAX_CONCURRENT_STREAMS)
                                .initialWindowSize(H2_INITIAL_WINDOW_SIZE)
                                .maxFrameSize(H2_MAX_FRAME_SIZE);
                        var frameCodec = Http2FrameCodecBuilder.forServer()
                                .initialSettings(settings)
                                .autoAckSettingsFrame(true)
                                .autoAckPingFrame(true)
                                .build();
                        ch.pipeline()
                                .addLast(
                                        frameCodec,
                                        new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                            @Override
                                            protected void initChannel(Channel ch) {
                                                ch.pipeline().addLast(new Http2StreamHandler());
                                            }
                                        }));
                        ch.eventLoop().execute(() -> {
                            try {
                                var connection = frameCodec.connection();
                                connection.local()
                                        .flowController()
                                        .incrementWindowSize(
                                                connection.connectionStream(),
                                                H2_CONNECTION_WINDOW_INCREMENT);
                            } catch (Http2Exception e) {
                                ch.pipeline().fireExceptionCaught(e);
                            }
                        });
                    }
                });

        return b.bind(port).sync().channel();
    }

    public void shutdown() throws InterruptedException {
        if (h1ServerChannel != null) {
            h1ServerChannel.close().sync();
        }
        if (h2ServerChannel != null) {
            h2ServerChannel.close().sync();
        }
        if (h2cServerChannel != null) {
            h2cServerChannel.close().sync();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().sync();
        }
    }

    /**
     * Handler for HTTP/1.1 requests.
     */
    private static class Http1RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
            String uri = msg.uri();
            FullHttpResponse response;

            if (pathMatches(uri, "/rpc")) {
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(CONTENT));
                response.headers()
                        .set(CONTENT_TYPE, "application/json")
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, CONTENT.length);
            } else if (pathMatches(uri, "/reset-io-stats")) {
                resetIoStats();
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.EMPTY_BUFFER);
                response.headers()
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, 0);
            } else if (pathMatches(uri, "/io-stats")) {
                byte[] body = ioStatsJson();
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(body));
                response.headers()
                        .set(CONTENT_TYPE, "application/json")
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, body.length);
            } else if (pathMatches(uri, "/stats")) {
                byte[] body = statsJson(uri);
                System.out.println("[H1 stats] " + new String(body, StandardCharsets.UTF_8));
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(body));
                response.headers()
                        .set(CONTENT_TYPE, "application/json")
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, body.length);
            } else if (pathMatches(uri, "/post") || pathMatches(uri, "/putmb")) {
                if (pathMatches(uri, "/putmb")) {
                    IoStatsAccumulator runStats = ioStatsFor(uri);
                    PUT_MB_REQUESTS.incrementAndGet();
                    PUT_MB_BYTES_RECEIVED.addAndGet(msg.content().readableBytes());
                    if (runStats != null) {
                        runStats.putMbRequests.incrementAndGet();
                        runStats.putMbBytesReceived.addAndGet(msg.content().readableBytes());
                    }
                }
                // POST/PUT returns empty 200 OK
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.EMPTY_BUFFER);
                response.headers()
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, 0);
            } else if (pathMatches(uri, "/get10mb")) {
                // Return 10MB response
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(MB10_CONTENT));
                response.headers()
                        .set(CONTENT_TYPE, "application/octet-stream")
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, MB10_CONTENT.length);
            } else if (pathMatches(uri, "/getmb")) {
                IoStatsAccumulator runStats = ioStatsFor(uri);
                GET_MB_REQUESTS.incrementAndGet();
                GET_MB_BYTES_SENT.addAndGet(MB_CONTENT.length);
                if (runStats != null) {
                    runStats.getMbRequests.incrementAndGet();
                    runStats.getMbBytesSent.addAndGet(MB_CONTENT.length);
                }
                // Return 1MB response
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(MB_CONTENT));
                response.headers()
                        .set(CONTENT_TYPE, "application/octet-stream")
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, MB_CONTENT.length);
            } else {
                // GET returns JSON body
                response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(CONTENT));
                response.headers()
                        .set(CONTENT_TYPE, "application/json")
                        .set(CONNECTION, KEEP_ALIVE)
                        .setInt(CONTENT_LENGTH, CONTENT.length);
            }
            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    /**
     * ALPN handler that configures the pipeline for HTTP/2 or HTTP/1.1.
     */
    private static class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {
        Http2OrHttpHandler() {
            super(ApplicationProtocolNames.HTTP_1_1);
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                var settings = io.netty.handler.codec.http2.Http2Settings.defaultSettings()
                        .maxConcurrentStreams(H2_TLS_MAX_CONCURRENT_STREAMS)
                        .initialWindowSize(H2_TLS_INITIAL_WINDOW_SIZE)
                        .maxFrameSize(H2_MAX_FRAME_SIZE);
                var frameCodec = Http2FrameCodecBuilder.forServer()
                        .initialSettings(settings)
                        .build();
                ctx.pipeline()
                        .addLast(
                                frameCodec,
                                new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                    @Override
                                    protected void initChannel(Channel ch) {
                                        ch.pipeline().addLast(new Http2StreamHandler());
                                    }
                                }));
                // Grow the connection-level receive window so it isn't the bottleneck under
                // concurrent uploads. RFC default is 64KB, which forces frequent WINDOW_UPDATE
                // roundtrips when many streams share one connection. We run in the event loop
                // because flow-controller methods require it.
                ctx.channel().eventLoop().execute(() -> {
                    try {
                        var connection = frameCodec.connection();
                        connection.local()
                                .flowController()
                                .incrementWindowSize(
                                        connection.connectionStream(),
                                        H2_TLS_CONNECTION_WINDOW_INCREMENT);
                    } catch (Http2Exception e) {
                        ctx.fireExceptionCaught(e);
                    }
                });
            } else {
                ctx.pipeline()
                        .addLast(
                                new HttpServerCodec(),
                                new HttpObjectAggregator(8192),
                                new Http1RequestHandler());
            }
        }
    }

    /**
     * Per-stream handler for HTTP/2 requests.
     *
     * <p>Each stream gets its own instance via Http2MultiplexHandler.
     * Flow control is handled automatically by Netty when using stream channels.
     */
    private static class Http2StreamHandler extends SimpleChannelInboundHandler<Http2StreamFrame> {
        private static final Http2Headers RESPONSE_HEADERS = new DefaultHttp2Headers(true, 3)
                .status("200")
                .set("content-type", "application/json")
                .setInt("content-length", CONTENT.length);
        private static final Http2Headers EMPTY_RESPONSE_HEADERS = new DefaultHttp2Headers(true, 2)
                .status("200")
                .setInt("content-length", 0);
        private static final Http2Headers MB_RESPONSE_HEADERS = new DefaultHttp2Headers(true, 3)
                .status("200")
                .set("content-type", "application/octet-stream")
                .setInt("content-length", MB_CONTENT.length);

        private static final Http2Headers MB10_RESPONSE_HEADERS = new DefaultHttp2Headers(true, 3)
                .status("200")
                .set("content-type", "application/octet-stream")
                .setInt("content-length", MB10_CONTENT.length);
        private RequestKind requestKind = RequestKind.OTHER;
        private String requestUri;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame frame) {
            if (frame instanceof Http2HeadersFrame headersFrame) {
                CharSequence path = headersFrame.headers().path();
                requestUri = path == null ? null : path.toString();
                if (pathMatches(path, "/reset")) {
                    System.gc();
                    Http2Headers resetHeaders = new DefaultHttp2Headers(true, 1).status("200");
                    ctx.writeAndFlush(new DefaultHttp2HeadersFrame(resetHeaders, true));
                } else if (pathMatches(path, "/reset-io-stats")) {
                    resetIoStats();
                    Http2Headers resetHeaders = new DefaultHttp2Headers(true, 2)
                            .status("200")
                            .setInt("content-length", 0);
                    ctx.writeAndFlush(new DefaultHttp2HeadersFrame(resetHeaders, true));
                } else if (pathMatches(path, "/io-stats")) {
                    byte[] body = ioStatsJson();
                    Http2Headers ioHeaders = new DefaultHttp2Headers(true, 3)
                            .status("200")
                            .set("content-type", "application/json")
                            .setInt("content-length", body.length);
                    ctx.write(new DefaultHttp2HeadersFrame(ioHeaders, false));
                    ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body), true));
                } else if (pathMatches(path, "/stats")) {
                    byte[] body = statsJson(path.toString());
                    System.out.println("[H2 stats] " + new String(body, StandardCharsets.UTF_8));
                    Http2Headers statsHeaders = new DefaultHttp2Headers(true, 2)
                            .set("content-type", "application/json")
                            .status("200")
                            .setInt("content-length", body.length);
                    ctx.write(new DefaultHttp2HeadersFrame(statsHeaders, false));
                    ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body), true));
                } else if (pathMatches(path, "/rpc")) {
                    requestKind = RequestKind.RPC;
                    if (headersFrame.isEndStream()) {
                        writeResponse(ctx, requestKind);
                    }
                } else if (pathMatches(path, "/post") || pathMatches(path, "/putmb")) {
                    requestKind = RequestKind.EMPTY;
                    if (pathMatches(path, "/putmb")) {
                        PUT_MB_REQUESTS.incrementAndGet();
                        IoStatsAccumulator runStats = ioStatsFor(requestUri);
                        if (runStats != null) {
                            runStats.putMbRequests.incrementAndGet();
                        }
                    }
                    // POST/PUT with body - wait for data frames
                    if (headersFrame.isEndStream()) {
                        // No body, respond immediately
                        writeResponse(ctx, requestKind);
                    }
                    // else: wait for DATA frames with endStream
                } else if (pathMatches(path, "/get10mb")) {
                    requestKind = RequestKind.GET_10MB;
                    if (headersFrame.isEndStream()) {
                        writeResponse(ctx, requestKind);
                    }
                } else if (pathMatches(path, "/getmb")) {
                    requestKind = RequestKind.GET_MB;
                    if (headersFrame.isEndStream()) {
                        writeResponse(ctx, requestKind);
                    }
                } else if (headersFrame.isEndStream()) {
                    requestKind = RequestKind.OTHER;
                    // Simple GET - respond with JSON body
                    writeResponse(ctx, requestKind);
                }
            } else if (frame instanceof Http2DataFrame dataFrame) {
                // Data consumed - flow control handled automatically by Http2MultiplexHandler
                if (requestKind == RequestKind.EMPTY) {
                    PUT_MB_BYTES_RECEIVED.addAndGet(dataFrame.content().readableBytes());
                    IoStatsAccumulator runStats = ioStatsFor(requestUri);
                    if (runStats != null) {
                        runStats.putMbBytesReceived.addAndGet(dataFrame.content().readableBytes());
                    }
                }
                if (dataFrame.isEndStream()) {
                    writeResponse(ctx, requestKind);
                }
            }
        }

        private void writeResponse(ChannelHandlerContext ctx, RequestKind kind) {
            switch (kind) {
                case RPC -> {
                    ctx.write(new DefaultHttp2HeadersFrame(RESPONSE_HEADERS, false));
                    ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(CONTENT), true));
                }
                case EMPTY -> ctx.writeAndFlush(new DefaultHttp2HeadersFrame(EMPTY_RESPONSE_HEADERS, true));
                case GET_MB -> {
                    IoStatsAccumulator runStats = ioStatsFor(requestUri);
                    GET_MB_REQUESTS.incrementAndGet();
                    GET_MB_BYTES_SENT.addAndGet(MB_CONTENT.length);
                    if (runStats != null) {
                        runStats.getMbRequests.incrementAndGet();
                        runStats.getMbBytesSent.addAndGet(MB_CONTENT.length);
                    }
                    ctx.write(new DefaultHttp2HeadersFrame(MB_RESPONSE_HEADERS, false));
                    ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(MB_CONTENT), true));
                }
                case GET_10MB -> {
                    ctx.write(new DefaultHttp2HeadersFrame(MB10_RESPONSE_HEADERS, false));
                    ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(MB10_CONTENT), true));
                }
                case OTHER -> {
                    ctx.write(new DefaultHttp2HeadersFrame(RESPONSE_HEADERS, false));
                    ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(CONTENT), true));
                }
            }
            requestKind = RequestKind.OTHER;
        }

        private enum RequestKind {
            RPC,
            EMPTY,
            GET_MB,
            GET_10MB,
            OTHER
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private static final class IoStatsAccumulator {
        private final AtomicLong getMbRequests = new AtomicLong();
        private final AtomicLong getMbBytesSent = new AtomicLong();
        private final AtomicLong putMbRequests = new AtomicLong();
        private final AtomicLong putMbBytesReceived = new AtomicLong();
    }

    /**
     * Write port configuration to a file for the benchmark to read.
     */
    public void writePortFile(File portFile) throws IOException {
        try (FileWriter writer = new FileWriter(portFile, StandardCharsets.UTF_8)) {
            writer.write("h1Port=" + h1Port + "\n");
            writer.write("h2Port=" + h2Port + "\n");
            writer.write("h2cPort=" + h2cPort + "\n");
        }
    }

    public static void main(String[] args) throws Exception {
        // Default port file location
        String portFilePath = args.length > 0 ? args[0] : "build/benchmark-server-ports.properties";

        System.out.println("Starting benchmark server...");
        BenchmarkServer server = new BenchmarkServer();

        System.out.println("HTTP/1.1 server: http://localhost:" + server.getH1Port());
        System.out.println("HTTP/2 (TLS) server: https://localhost:" + server.getH2Port());
        System.out.println("HTTP/2 (h2c) server: http://localhost:" + server.getH2cPort());

        // Write port file
        File portFile = new File(portFilePath);
        File parentDir = portFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + parentDir);
        }
        server.writePortFile(portFile);
        System.out.println("Port file written to: " + portFile.getAbsolutePath());

        // Wait for shutdown signal
        System.out.println("Press Ctrl+C to stop...");
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            try {
                server.shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            shutdownLatch.countDown();
        }));

        shutdownLatch.await();
        System.out.println("Server stopped.");
    }
}
