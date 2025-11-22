/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.InetSocketAddress;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.http.api.HttpVersion;

public class NettyTestServer {
    private static final NettyTestLogger LOGGER = NettyTestLogger.getLogger(NettyTestServer.class);
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final Config config;
    private int port;
    private Channel channel;

    public NettyTestServer(Builder builder) {
        this.port = builder.port;
        this.config = builder.buildConfig();
        this.bossGroup = createEventLoopGroup(1);
        this.workerGroup = createEventLoopGroup(4);
    }

    public void start() throws InterruptedException {
        var http2 = config.httpVersion == HttpVersion.HTTP_2;
        var bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ServerInitializer(config));

        channel = bootstrap.bind("127.0.0.1", this.port).sync().channel();
        var address = (InetSocketAddress) channel.localAddress();
        this.port = address.getPort();
        LOGGER.info(null, "Server started on port {}, using: {}", port, (http2 ? " (HTTP/2)" : " (HTTP/1.1)"));
    }

    public void stop() {
        if (channel != null) {
            channel.close().awaitUninterruptibly();
        }
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

    public int getPort() {
        return port;
    }

    private EventLoopGroup createEventLoopGroup(Integer nThreads) {
        var threadFactory = new DefaultThreadFactory("test-server", true);
        if (nThreads != null) {
            return new MultiThreadIoEventLoopGroup(nThreads,
                    threadFactory,
                    NioIoHandler.newFactory());

        }
        return new MultiThreadIoEventLoopGroup(threadFactory,
                NioIoHandler.newFactory());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Config {
        private final int port;
        private final HttpVersion httpVersion;
        private final NettyHttpClientTransport.H2ConnectionMode h2ConnectionMode;
        private final SslContextBuilder sslContextBuilder;
        private final Http2ClientHandlerFactory http2HandlerFactory;
        private final Http11ClientHandlerFactory http11HandlerFactory;

        public Config(Builder builder) {
            this.h2ConnectionMode = builder.h2ConnectionMode;
            this.sslContextBuilder = builder.sslContextBuilder;
            this.httpVersion = builder.httpVersion;
            this.port = builder.port;
            this.http2HandlerFactory = builder.http2HandlerFactory;
            this.http11HandlerFactory = builder.http11HandlerFactory;
        }

        public int port() {
            return this.port;
        }

        public HttpVersion httpVersion() {
            return this.httpVersion;
        }

        public NettyHttpClientTransport.H2ConnectionMode h2ConnectionMode() {
            return this.h2ConnectionMode;
        }

        public SslContextBuilder sslContextBuilder() {
            return this.sslContextBuilder;
        }

        public Http2ClientHandlerFactory http2HandlerFactory() {
            return http2HandlerFactory;
        }

        public Http11ClientHandlerFactory http11HandlerFactory() {
            return http11HandlerFactory;
        }
    }

    public static class Builder {
        private int port = 0;
        private HttpVersion httpVersion = HttpVersion.HTTP_1_1;
        private NettyHttpClientTransport.H2ConnectionMode h2ConnectionMode =
                NettyHttpClientTransport.H2ConnectionMode.AUTO;
        private SslContextBuilder sslContextBuilder;
        private Http2ClientHandlerFactory http2HandlerFactory;
        private Http11ClientHandlerFactory http11HandlerFactory;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder h2ConnectionMode(NettyHttpClientTransport.H2ConnectionMode h2ConnectionMode) {
            this.h2ConnectionMode = h2ConnectionMode;
            return this;
        }

        public Builder sslContextBuilder(SslContextBuilder sslContextBuilder) {
            this.sslContextBuilder = sslContextBuilder;
            return this;
        }

        public Builder http2HandlerFactory(Http2ClientHandlerFactory http2HandlerFactory) {
            this.http2HandlerFactory = http2HandlerFactory;
            return this;
        }

        public Builder http11HandlerFactory(Http11ClientHandlerFactory http11HandlerFactory) {
            this.http11HandlerFactory = http11HandlerFactory;
            return this;
        }

        public NettyTestServer build() {
            return new NettyTestServer(this);
        }

        public Config buildConfig() {
            return new Config(this);
        }
    }
}
