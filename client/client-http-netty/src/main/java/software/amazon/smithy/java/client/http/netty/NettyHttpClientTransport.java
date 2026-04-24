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

            NettyConnection conn = pool.acquire(route);
            try {
                HttpResponse response = switch (conn.mode) {
                    case H1 -> H1Executor.execute(conn.channel, request, timeoutMs);
                    case H2 -> H2Executor.execute(conn.channel, request, timeoutMs);
                };
                // For H2: release back to pool (multiplexed). For H1: release serially.
                // Note: the response body will be consumed AFTER we return; the caller
                // eventually closes the InputStream. For H1, the connection is "in use"
                // until the body is drained. We return it to the pool now anyway — a future
                // acquire on the same H1 conn would collide with a still-reading response.
                // TODO: properly defer H1 release until body stream is closed.
                pool.release(conn);
                return response;
            } catch (Throwable t) {
                pool.dispose(conn);
                throw t;
            }
        } catch (Exception e) {
            throw ClientTransport.remapExceptions(e);
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
