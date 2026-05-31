/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLException;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Per-route connection pool. Maintains a bounded number of connections per route, reusing
 * H2 connections across many concurrent streams and H1 connections serially.
 */
final class NettyConnectionPool implements AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(NettyConnectionPool.class);

    private final EventLoopGroup group;
    private final NettyHttpTransportConfig config;
    private final SslContext defaultSslCtx;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition capacityAvailable = lock.newCondition();
    private final Map<Route, Deque<NettyConnection>> idle = new HashMap<>();
    private final Map<Route, Integer> connectionCounts = new HashMap<>();
    private boolean closed;

    private SslContext cachedSslCtx;

    NettyConnectionPool(EventLoopGroup group, NettyHttpTransportConfig config, SslContext defaultSslCtx) {
        this.group = group;
        this.config = config;
        this.defaultSslCtx = defaultSslCtx;
    }

    /**
     * Acquire a connection for the given route. Blocks up to acquireTimeout waiting for capacity.
     * Caller must eventually call {@link #release(NettyConnection)} or {@link #dispose(NettyConnection)}.
     */
    NettyConnection acquire(Route route) throws IOException {
        return acquire(route, false);
    }

    /**
     * Acquire a guaranteed-fresh connection, bypassing reuse of any pooled connection. Used by the
     * stale-connection retry path so a request that failed on a server-closed keep-alive does not
     * immediately land on another potentially-stale pooled connection.
     */
    NettyConnection acquireFresh(Route route) throws IOException {
        return acquire(route, true);
    }

    private NettyConnection acquire(Route route, boolean forceFresh) throws IOException {
        long deadlineNanos = System.nanoTime() + config.acquireTimeout().toNanos();
        while (true) {
            NettyConnection existing;
            lock.lock();
            try {
                if (closed)
                    throw new IOException("Pool closed");
                existing = forceFresh ? null : pickReusable(route);
                if (existing == null) {
                    int count = connectionCounts.getOrDefault(route, 0);
                    if (count < config.maxConnectionsPerHost()) {
                        // Reserve a slot, then open the connection outside the lock below.
                        connectionCounts.merge(route, 1, Integer::sum);
                    } else {
                        // Pool full with no reusable connection: wait to be signalled by a
                        // release/dispose rather than sleep-polling. Loop re-checks on wakeup.
                        long remaining = deadlineNanos - System.nanoTime();
                        if (remaining <= 0) {
                            throw new IOException("Timed out acquiring connection for " + route);
                        }
                        try {
                            // Return value ignored: the enclosing while-loop revalidates capacity
                            // and the deadline on the next iteration (also handles spurious wakeups).
                            long ignored = capacityAvailable.awaitNanos(remaining);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted acquiring connection", e);
                        }
                        continue;
                    }
                }
            } finally {
                lock.unlock();
            }

            if (existing != null) {
                return existing;
            }
            // We reserved a slot under the lock above; open the new connection now.
            try {
                return openNewConnection(route);
            } catch (Throwable t) {
                lock.lock();
                try {
                    connectionCounts.merge(route, -1, Integer::sum);
                    capacityAvailable.signalAll();
                } finally {
                    lock.unlock();
                }
                if (t instanceof IOException io)
                    throw io;
                throw new IOException("Failed to open connection", t);
            }
        }
    }

    /**
     * Try to find an already-open connection. Must be called with lock held.
     */
    private NettyConnection pickReusable(Route route) {
        // Prefer any H2 connection with stream capacity (not even idle - multiplexed)
        // by scanning all connections we track. For simplicity here we track idle only;
        // active H2 connections are returned immediately via release() back to idle
        // and picked again by any waiter.
        var dq = idle.get(route);
        if (dq == null)
            return null;
        long reuseIdleNanos = config.reuseIdleTimeout().toNanos();
        long now = System.nanoTime();
        while (!dq.isEmpty()) {
            var c = dq.peekFirst();
            if (!c.isActive()) {
                dq.pollFirst();
                evictDead(c);
                continue;
            }
            if (c.mode == NettyConnection.Mode.H2) {
                if (c.canAcceptMoreStreams(config.h2StreamsPerConnection())) {
                    // Leave it in idle — other callers can also multiplex on it
                    c.acquireStream();
                    return c;
                }
                // H2 maxed; skip (don't remove; might have capacity later after releases)
                return null;
            } else {
                if (reuseIdleNanos > 0 && now - c.lastUsedNanos >= reuseIdleNanos) {
                    dq.pollFirst();
                    evictDead(c);
                    continue;
                }
                dq.pollFirst();
                c.fromReuse = true;
                return c;
            }
        }
        return null;
    }

    private void evictDead(NettyConnection c) {
        if (c.markClosedOnce()) {
            connectionCounts.merge(c.route, -1, Integer::sum);
            try {
                c.channel.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Release a connection back to the pool.
     */
    void release(NettyConnection c) {
        if (!c.isActive()) {
            dispose(c);
            return;
        }
        lock.lock();
        try {
            if (c.mode == NettyConnection.Mode.H2) {
                c.releaseStream();
                // Already in idle map
                idle.computeIfAbsent(c.route, k -> new ArrayDeque<>());
                var dq = idle.get(c.route);
                if (!dq.contains(c)) {
                    dq.addLast(c);
                }
            } else {
                // H1: return to idle
                idle.computeIfAbsent(c.route, k -> new ArrayDeque<>()).addLast(c);
                c.lastUsedNanos = System.nanoTime();
            }
            capacityAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void dispose(NettyConnection c) {
        if (!c.markClosedOnce()) {
            return;
        }
        try {
            c.channel.close();
        } catch (Exception ignored) {}
        lock.lock();
        try {
            var dq = idle.get(c.route);
            if (dq != null) {
                dq.remove(c);
            }
            connectionCounts.merge(c.route, -1, Integer::sum);
            // Freed a slot for the route — a waiter may now open a new connection. signalAll
            // because one shared condition serves all routes (see acquire()).
            capacityAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Evict idle connections older than maxIdleTime.
     */
    void evictIdle() {
        long cutoff = System.nanoTime() - config.maxIdleTime().toNanos();
        lock.lock();
        try {
            boolean freed = false;
            for (var dq : idle.values()) {
                Iterator<NettyConnection> it = dq.iterator();
                while (it.hasNext()) {
                    var c = it.next();
                    if (c.lastUsedNanos < cutoff && c.inFlightStreams.get() == 0) {
                        it.remove();
                        // markClosedOnce + decrement so the close-future dispose listener does not
                        // double-decrement the route count.
                        evictDead(c);
                        freed = true;
                    }
                }
            }
            if (freed) {
                // Freed one or more slots — wake all waiters to re-check capacity.
                capacityAvailable.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            for (var dq : idle.values()) {
                for (var c : dq) {
                    try {
                        c.channel.close();
                    } catch (Exception ignored) {}
                    c.markClosed();
                }
                dq.clear();
            }
            connectionCounts.clear();
            // Wake every waiter so they observe `closed` and fail fast instead of blocking.
            capacityAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // --- Connection opening ---

    private NettyConnection openNewConnection(Route route) throws IOException {
        var policy = config.httpVersionPolicy();
        boolean tls = route.isTls();
        if (tls) {
            return openTlsConnection(route, policy);
        } else if (policy == HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE) {
            return openH2cConnection(route);
        } else {
            return openH1Connection(route);
        }
    }

    private Bootstrap baseBootstrap() {
        return new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(config.writeBufferLowWater(), config.writeBufferHighWater()));
    }

    private SslContext sslContext(HttpVersionPolicy policy) throws SSLException {
        if (defaultSslCtx != null) {
            return defaultSslCtx;
        }
        lock.lock();
        try {
            if (cachedSslCtx == null) {
                cachedSslCtx = NettyUtils.buildSslContext(policy.alpnProtocols(), /*trustAll=*/true);
            }
            return cachedSslCtx;
        } finally {
            lock.unlock();
        }
    }

    private NettyConnection openTlsConnection(Route route, HttpVersionPolicy policy) throws IOException {
        SslContext sslCtx;
        try {
            sslCtx = sslContext(policy);
        } catch (SSLException e) {
            throw new IOException("Failed to build SSL context", e);
        }

        var resolvedModeHolder = new NettyConnection[1];
        var readyLatch = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();

        Bootstrap b = baseBootstrap().handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(sslCtx.newHandler(ch.alloc(), route.host(), route.port()));
                ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                        try {
                            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                                configureH2Pipeline(ctx);
                                resolvedModeHolder[0] =
                                        new NettyConnection(ctx.channel(), NettyConnection.Mode.H2, route);
                            } else {
                                configureH1Pipeline(ctx);
                                resolvedModeHolder[0] =
                                        new NettyConnection(ctx.channel(), NettyConnection.Mode.H1, route);
                            }
                            readyLatch.countDown();
                        } catch (Throwable t) {
                            failure.set(t);
                            readyLatch.countDown();
                            ctx.close();
                        }
                    }

                    @Override
                    protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
                        failure.set(cause);
                        readyLatch.countDown();
                        ctx.close();
                    }
                });
            }
        });

        ChannelFuture cf;
        try {
            cf = b.connect(route.host(), route.port()).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted connecting", e);
        }
        if (!cf.isSuccess()) {
            throw new IOException("Connect failed", cf.cause());
        }

        try {
            if (!readyLatch.await(15, TimeUnit.SECONDS)) {
                cf.channel().close();
                throw new IOException("Timed out during TLS handshake/ALPN");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during TLS/ALPN", e);
        }
        if (failure.get() != null) {
            throw new IOException("TLS handshake failed", failure.get());
        }

        var conn = resolvedModeHolder[0];
        // For H2 connection, pre-register in idle so other callers can multiplex.
        lock.lock();
        try {
            if (conn.mode == NettyConnection.Mode.H2) {
                conn.acquireStream(); // this caller's stream
                idle.computeIfAbsent(route, k -> new ArrayDeque<>()).addLast(conn);
            }
            // H1: don't add to idle yet — caller is holding exclusive use
        } finally {
            lock.unlock();
        }
        conn.channel.closeFuture().addListener(f -> dispose(conn));
        return conn;
    }

    private NettyConnection openH1Connection(Route route) throws IOException {
        Bootstrap b = baseBootstrap().handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                configureH1Pipeline(ch.pipeline());
            }
        });
        ChannelFuture cf;
        try {
            cf = b.connect(route.host(), route.port()).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted connecting", e);
        }
        if (!cf.isSuccess())
            throw new IOException("Connect failed", cf.cause());
        var conn = new NettyConnection(cf.channel(), NettyConnection.Mode.H1, route);
        conn.channel.closeFuture().addListener(f -> dispose(conn));
        return conn;
    }

    private NettyConnection openH2cConnection(Route route) throws IOException {
        Bootstrap b = baseBootstrap().handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                configureH2Pipeline(ch.pipeline());
            }
        });
        ChannelFuture cf;
        try {
            cf = b.connect(route.host(), route.port()).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted connecting", e);
        }
        if (!cf.isSuccess())
            throw new IOException("Connect failed", cf.cause());
        var conn = new NettyConnection(cf.channel(), NettyConnection.Mode.H2, route);
        lock.lock();
        try {
            conn.acquireStream();
            idle.computeIfAbsent(route, k -> new ArrayDeque<>()).addLast(conn);
        } finally {
            lock.unlock();
        }
        conn.channel.closeFuture().addListener(f -> dispose(conn));
        return conn;
    }

    private void configureH1Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new HttpClientCodec());
    }

    private void configureH1Pipeline(ChannelHandlerContext ctx) {
        configureH1Pipeline(ctx.pipeline());
    }

    private void configureH2Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast(Http2FrameCodecBuilder.forClient()
                .initialSettings(Http2Settings.defaultSettings()
                        .initialWindowSize(config.initialWindowSize())
                        .maxFrameSize(config.maxFrameSize())
                        .maxConcurrentStreams(config.h2StreamsPerConnection()))
                .build());
        pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ignored) {}
        }));
    }

    private void configureH2Pipeline(ChannelHandlerContext ctx) {
        configureH2Pipeline(ctx.pipeline());
    }
}
