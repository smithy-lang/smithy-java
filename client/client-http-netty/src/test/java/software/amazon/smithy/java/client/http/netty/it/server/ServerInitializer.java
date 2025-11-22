/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLException;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.http.api.HttpVersion;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {
    private final Http2ClientHandlers h2ClientHandlers;
    private final Http11ClientHandlers h11ClientHandlers;
    private final NettyTestServer.Config config;

    public ServerInitializer(NettyTestServer.Config config) {
        this.config = config;
        if (config.httpVersion() == HttpVersion.HTTP_2) {
            var handlersFactory = Objects.requireNonNull(config.http2HandlerFactory());
            this.h2ClientHandlers = new Http2ClientHandlers(handlersFactory);
            this.h11ClientHandlers = null;
        } else {
            var handlersFactory = Objects.requireNonNull(config.http11HandlerFactory());
            this.h11ClientHandlers = new Http11ClientHandlers(handlersFactory);
            this.h2ClientHandlers = null;
        }
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        var pipeline = ch.pipeline();
        var sslContextBuilder = config.sslContextBuilder();
        if (sslContextBuilder != null) {
            try {
                if (config.h2ConnectionMode() == NettyHttpClientTransport.H2ConnectionMode.ALPN) {
                    sslContextBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2));
                }
                pipeline.addLast(sslContextBuilder.build().newHandler(ch.alloc()));
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        }

        if (config.httpVersion() == HttpVersion.HTTP_2) {
            // HTTP/2 with prior knowledge
            pipeline.addLast(Http2FrameCodecBuilder.forServer().build());
            pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2StreamFrameHandler(h2ClientHandlers));
                }
            }));
            // Handle connection-level frames (SETTINGS, PING, etc.)
            pipeline.addLast(new Http2ConnectionFrameHandler());
        } else {
            // HTTP/1.1
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
            pipeline.addLast(new Http11Handler(h11ClientHandlers));
        }
    }

    public static class Http2ClientHandlers {
        private final Http2ClientHandlerFactory factory;
        private final Map<ChannelId, Http2ClientHandler> h2ClientHandlers = new ConcurrentHashMap<>();

        Http2ClientHandlers(Http2ClientHandlerFactory factory) {
            this.factory = factory;
        }

        public Http2ClientHandler create(ChannelHandlerContext ctx) {
            var result = factory.create(ctx);
            h2ClientHandlers.put(ctx.channel().id(), result);
            return result;
        }

        public Http2ClientHandler get(ChannelHandlerContext ctx) {
            return h2ClientHandlers.get(ctx.channel().id());
        }
    }

    public static class Http11ClientHandlers {
        private final Http11ClientHandlerFactory factory;
        private final Map<ChannelId, Http11ClientHandler> h11ClientHandlers = new ConcurrentHashMap<>();

        Http11ClientHandlers(Http11ClientHandlerFactory factory) {
            this.factory = factory;
        }

        public Http11ClientHandler create(ChannelHandlerContext ctx) {
            var result = factory.create(ctx);
            h11ClientHandlers.put(ctx.channel().id(), result);
            return result;
        }

        public Http11ClientHandler get(ChannelHandlerContext ctx) {
            return h11ClientHandlers.get(ctx.channel().id());
        }
    }
}
