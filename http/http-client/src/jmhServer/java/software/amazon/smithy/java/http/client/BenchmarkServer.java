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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
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
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2SecurityUtil;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

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

    // Fixed ports for benchmark server (avoids dynamic port discovery complexity)
    public static final int DEFAULT_H1_PORT = 18080;
    public static final int DEFAULT_H2_PORT = 18443;
    public static final int DEFAULT_H2C_PORT = 18081;

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

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

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
                .option(ChannelOption.SO_BACKLOG, 16384)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_RCVBUF, 1048576)
                .childOption(ChannelOption.SO_SNDBUF, 1048576)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        // h2c prior knowledge: HTTP/2 directly without TLS or upgrade
                        ch.pipeline()
                                .addLast(
                                        Http2FrameCodecBuilder.forServer()
                                                .initialSettings(
                                                        io.netty.handler.codec.http2.Http2Settings.defaultSettings()
                                                                .maxConcurrentStreams(10000)
                                                                .initialWindowSize(1048576))
                                                .build(),
                                        Http2RequestHandler.INSTANCE);
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
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1,
                    OK,
                    Unpooled.wrappedBuffer(CONTENT));
            response.headers()
                    .set(CONTENT_TYPE, "application/json")
                    .set(CONNECTION, KEEP_ALIVE)
                    .setInt(CONTENT_LENGTH, CONTENT.length);
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
                ctx.pipeline()
                        .addLast(
                                Http2FrameCodecBuilder.forServer()
                                        .initialSettings(io.netty.handler.codec.http2.Http2Settings.defaultSettings()
                                                .maxConcurrentStreams(10000)
                                                .initialWindowSize(1048576))
                                        .build(),
                                Http2RequestHandler.INSTANCE);
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
     * Handler for HTTP/2 requests using the frame codec API.
     */
    @io.netty.channel.ChannelHandler.Sharable
    private static class Http2RequestHandler extends ChannelInboundHandlerAdapter {
        static final Http2RequestHandler INSTANCE = new Http2RequestHandler();
        private static final Http2Headers RESPONSE_HEADERS = new DefaultHttp2Headers(true, 3)
                .status("200")
                .set("content-type", "application/json")
                .setInt("content-length", CONTENT.length);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame headersFrame) {
                if (headersFrame.isEndStream()) {
                    sendResponse(ctx, headersFrame.stream());
                }
            } else if (msg instanceof Http2DataFrame dataFrame) {
                dataFrame.release();
                if (dataFrame.isEndStream()) {
                    sendResponse(ctx, dataFrame.stream());
                }
            }
        }

        private void sendResponse(ChannelHandlerContext ctx, Http2FrameStream stream) {
            ctx.write(new DefaultHttp2HeadersFrame(RESPONSE_HEADERS, false).stream(stream));
            ctx.writeAndFlush(new DefaultHttp2DataFrame(
                    Unpooled.wrappedBuffer(CONTENT),
                    true).stream(stream));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
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
