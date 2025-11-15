/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import software.amazon.smithy.java.client.core.error.TlsException;
import software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Utils;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Provides access to a channel promises for a given URI.
 */
final class ChannelPoolMap implements Closeable {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(ChannelPoolMap.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final NettyHttpClientTransport.Configuration config;
    private final ConcurrentMap<URI, ChannelPool> uriToChannelPool = new ConcurrentHashMap<>();
    private final Bootstrap baseBootstrap;
    private final EventLoopGroup eventLoopGroup;

    ChannelPoolMap(NettyHttpClientTransport.Configuration config) {
        this.config = config;
        this.eventLoopGroup = createEventLoopGroup(config);
        this.baseBootstrap = createBaseBootstrap(this.eventLoopGroup, this.config);
    }

    private static Bootstrap createBaseBootstrap(
            EventLoopGroup eventLoopGroup,
            NettyHttpClientTransport.Configuration config
    ) {
        var bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup);
        bootstrap.channel(getChannelClass(config));
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, fromLong(config.connectTimeout().toMillis()));
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.AUTO_READ, false);
        bootstrap.option(ChannelOption.RECVBUF_ALLOCATOR,
                new AdaptiveRecvByteBufAllocator(4096, 16384, 65536));
        bootstrap.option(ChannelOption.SO_RCVBUF, 1048576);

        return bootstrap;
    }

    static EventLoopGroup createEventLoopGroup(NettyHttpClientTransport.Configuration configuration) {
        ThreadFactory threadFactory = new DefaultThreadFactory("smithy-java-netty", true);
        return new MultiThreadIoEventLoopGroup(configuration.eventLoopGroupThreads(),
                threadFactory,
                NioIoHandler.newFactory());
    }

    static Class<? extends Channel> getChannelClass(NettyHttpClientTransport.Configuration configuration) {
        return NioSocketChannel.class;
    }

    static int fromLong(long i) {
        if (i > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (i < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) i;
    }

    /**
     * Acquires a channel from a connection pool for the given URI.
     *
     * @param uri The uri from the channel
     * @return A future that will get notified whether the acquisition was successful.
     */
    public Future<Channel> acquire(URI uri) {
        var channelPool = channelPool(uri);
        return channelPool.acquire();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (var kvp : uriToChannelPool.entrySet()) {
            try {
                kvp.getValue().close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close channel pool for: " + kvp.getKey(), e);
            }

        }
        uriToChannelPool.clear();
        eventLoopGroup.shutdownGracefully();
    }

    private ChannelPool channelPool(URI uri) {
        if (closed.get()) {
            throw new IllegalStateException("closed");
        }
        Objects.requireNonNull(uri, "uri");
        return uriToChannelPool.computeIfAbsent(uri, this::newChannelPool);
    }

    private ChannelPool newChannelPool(URI uri) {
        var bootstrap = baseBootstrap.clone();
        bootstrap.remoteAddress(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        var channelPoolRef = new AtomicReference<ChannelPool>();
        SslContext sslContext = null;
        if (isHttps(uri)) {
            sslContext = createSslContext();
        }
        var initializer = new ChannelPipelineInitializer(config, uri, sslContext, channelPoolRef);
        var pool = createChannelPool(bootstrap, initializer);
        channelPoolRef.set(pool);
        return pool;
    }

    private ChannelPool createChannelPool(Bootstrap bootstrap, ChannelPipelineInitializer initializer) {
        var basePool = new SimpleChannelPool(bootstrap, initializer);
        if (config.httpVersion() == HttpVersion.HTTP_2) {
            return NettyHttp2Utils.createChannelPool(basePool, this.eventLoopGroup);
        }
        return basePool;
    }

    private boolean isHttps(URI uri) {
        return uri.getScheme().equalsIgnoreCase("https");
    }

    private SslContext createSslContext() throws TlsException {
        try {
            return createSslContextBuilder().build();
        } catch (SSLException e) {
            throw new TlsException(e);
        }
    }

    private SslContextBuilder createSslContextBuilder() {
        if (config.httpVersion() == HttpVersion.HTTP_1_1) {
            LOGGER.info("Configuring ssl context for HTTP 1.1.");
            var builder = SslContextBuilder.forClient();
            config.sslContextModifier().accept(builder);
            return builder;
        }
        LOGGER.info("Configuring ssl context for HTTP 2.");
        var sslProvider = SslContext.defaultClientProvider();
        ApplicationProtocolConfig.SelectorFailureBehavior selectorFailureBehavior;
        ApplicationProtocolConfig.SelectedListenerFailureBehavior selectedListenerFailureBehavior;
        if (sslProvider == SslProvider.JDK) {
            selectorFailureBehavior = ApplicationProtocolConfig.SelectorFailureBehavior.FATAL_ALERT;
            selectedListenerFailureBehavior =
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.FATAL_ALERT;
        } else {
            // OpenSSL doesn't support FATAL_ALERT
            selectorFailureBehavior = ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE;
            selectedListenerFailureBehavior = ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
        }
        var builder = SslContextBuilder.forClient()
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        selectorFailureBehavior,
                        selectedListenerFailureBehavior,
                        ApplicationProtocolNames.HTTP_2));
        config.sslContextModifier().accept(builder);
        return builder;
    }
}
