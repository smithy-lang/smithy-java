/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A client transport backed by Netty. Supports HTTP/1.1, HTTP/2 (via ALPN), and HTTP/2 cleartext.
 */
public final class NettyHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(NettyHttpClientTransport.class);

    private final NettyHttpTransportConfig config;
    private final EventLoopGroup group;
    private final NettyConnectionPool pool;

    public NettyHttpClientTransport() {
        this(new NettyHttpTransportConfig());
    }

    public NettyHttpClientTransport(NettyHttpTransportConfig config) {
        this.config = config;
        int threads = config.eventLoopThreads() > 0
                ? config.eventLoopThreads()
                : Runtime.getRuntime().availableProcessors();
        this.group = new NioEventLoopGroup(threads, new DefaultThreadFactory("smithy-netty-evloop", true));
        this.pool = new NettyConnectionPool(group, config, null);
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        try {
            var uri = request.uri();
            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            var route = new Route(uri.getScheme(), uri.getHost(), port);

            long timeoutMs = 0;
            var timeout = context.get(HttpContext.HTTP_REQUEST_TIMEOUT);
            if (timeout != null) {
                timeoutMs = timeout.toMillis();
            }

            try {
                // First attempt may reuse a pooled connection.
                return attempt(route, request, timeoutMs, /*forceFresh=*/false);
            } catch (StaleConnectionException stale) {
                if (request.body() == null || request.body().isReplayable()) {
                    return attempt(route, request, timeoutMs, /*forceFresh=*/true);
                }
                throw stale;
            }
        } catch (Exception e) {
            throw ClientTransport.remapExceptions(e);
        }
    }

    private HttpResponse attempt(Route route, HttpRequest request, long timeoutMs, boolean forceFresh)
            throws IOException {
        NettyConnection conn = forceFresh ? pool.acquireFresh(route) : pool.acquire(route);
        try {
            switch (conn.mode) {
                case H1 -> {
                    // H1 is non-multiplexed: the connection stays exclusively in use until the
                    // response body InputStream is drained and closed. Release/dispose is
                    // therefore deferred to the body's onClose callback wired inside execute()
                    // (mirrors H2 tying cleanup to stream close). On a headers-phase failure,
                    // execute() throws and we dispose below; the deferred path never runs.
                    return H1Executor.execute(pool, conn, request, timeoutMs);
                }
                case H2 -> {
                    HttpResponse response = H2Executor.execute(conn.channel, request, timeoutMs);
                    // H2 is multiplexed: the parent connection can serve other streams
                    // immediately; the response body rides its own stream channel.
                    pool.release(conn);
                    return response;
                }
                default -> throw new IllegalStateException("Unknown connection mode: " + conn.mode);
            }
        } catch (Throwable t) {
            pool.dispose(conn);
            throw t;
        }
    }

    @Override
    public void close() throws IOException {
        pool.close();
        try {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class Factory implements ClientTransportFactory<HttpRequest, HttpResponse> {
        @Override
        public String name() {
            return "http-netty";
        }

        @Override
        public NettyHttpClientTransport createTransport(Document node, Document pluginSettings) {
            var config = new NettyHttpTransportConfig().fromDocument(pluginSettings.asStringMap()
                    .getOrDefault("httpConfig", Document.EMPTY_MAP));
            config.fromDocument(node);
            return new NettyHttpClientTransport(config);
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
