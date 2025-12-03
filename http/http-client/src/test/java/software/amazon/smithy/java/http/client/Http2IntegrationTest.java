/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

/**
 * Integration test for HTTP/2 client implementation.
 */
public class Http2IntegrationTest {

    private static final byte[] CONTENT = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private String baseUrl;
    private HttpClient client;

    @BeforeEach
    void setup() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(4);

        // Start HTTP/2 server
        SelfSignedCertificate ssc = new SelfSignedCertificate("localhost");
        SslContext sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2))
                .build();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
                        ch.pipeline().addLast(new Http2AlpnHandler());
                    }
                });

        serverChannel = b.bind(0).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        baseUrl = "https://localhost:" + port;
        System.out.println("Started HTTP/2 test server on port " + port);

        // Create trust-all SSL context
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        // Create HTTP/2 client
        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));

        client = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(10)
                        .maxTotalConnections(10)
                        .maxIdleTime(Duration.ofMinutes(1))
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_2)
                        .dnsResolver(staticDns)
                        .sslContext(sslContext)
                        .build())
                .build();
    }

    @AfterEach
    void teardown() throws Exception {
        if (client != null)
            client.close();
        if (serverChannel != null)
            serverChannel.close().sync();
        if (bossGroup != null)
            bossGroup.shutdownGracefully().sync();
        if (workerGroup != null)
            workerGroup.shutdownGracefully().sync();
    }

    @Test
    void testSimpleGet() throws Exception {
        URI uri = URI.create(baseUrl + "/test");
        HttpRequest request = HttpRequest.builder()
                .uri(uri)
                .method("GET")
                .build();

        System.out.println("Sending HTTP/2 GET request to " + uri);
        HttpResponse response = client.send(request);

        System.out.println("Response status: " + response.statusCode());
        byte[] bodyBytes = response.body().asByteBuffer().array();
        String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
        System.out.println("Response body: " + bodyStr);

        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"ok\"}", bodyStr);
    }

    @Test
    void testSequentialMultiplexedRequests() throws Exception {
        // Test sequential requests on same connection to verify basic multiplexing
        URI uri = URI.create(baseUrl + "/test");

        for (int i = 0; i < 3; i++) {
            HttpRequest request = HttpRequest.builder()
                    .uri(uri)
                    .method("GET")
                    .build();

            System.out.println("Sending sequential request " + i);
            HttpResponse response = client.send(request);
            assertEquals(200, response.statusCode(), "Request " + i + " should succeed");
            System.out.println("Sequential request " + i + " succeeded");
        }
    }

    @Test
    void testConcurrentMultiplexedRequests() throws Exception {
        URI uri = URI.create(baseUrl + "/test");
        int concurrency = 3; // Start with small number to debug

        var errors = new java.util.concurrent.atomic.AtomicInteger(0);
        var success = new java.util.concurrent.atomic.AtomicInteger(0);
        var latch = new java.util.concurrent.CountDownLatch(concurrency);

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                final int reqNum = i;
                executor.submit(() -> {
                    try {
                        HttpRequest request = HttpRequest.builder()
                                .uri(uri)
                                .method("GET")
                                .build();

                        HttpResponse response = client.send(request);

                        if (response.statusCode() == 200) {
                            success.incrementAndGet();
                        } else {
                            System.err.println("Request " + reqNum + " got status: " + response.statusCode());
                            errors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Request " + reqNum + " failed: " + e.getMessage());
                        e.printStackTrace();
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        }

        System.out.println("Success: " + success.get() + ", Errors: " + errors.get());
        assertEquals(concurrency, success.get(), "All requests should succeed");
        assertEquals(0, errors.get(), "No errors should occur");
    }

    /**
     * ALPN handler for HTTP/2.
     */
    private static class Http2AlpnHandler extends ApplicationProtocolNegotiationHandler {
        Http2AlpnHandler() {
            super(ApplicationProtocolNames.HTTP_2);
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            System.out.println("Server negotiated protocol: " + protocol);
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                ctx.pipeline()
                        .addLast(
                                Http2FrameCodecBuilder.forServer().build(),
                                new Http2Handler());
            } else {
                throw new IllegalStateException("Unknown protocol: " + protocol);
            }
        }
    }

    /**
     * HTTP/2 request handler.
     */
    private static class Http2Handler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame headersFrame) {
                System.out.println("Server received HEADERS on stream " + headersFrame.stream().id() +
                        ", endStream=" + headersFrame.isEndStream());
                if (headersFrame.isEndStream()) {
                    sendResponse(ctx, headersFrame.stream());
                }
            } else if (msg instanceof Http2DataFrame dataFrame) {
                System.out.println("Server received DATA on stream " + dataFrame.stream().id() +
                        ", endStream=" + dataFrame.isEndStream());
                dataFrame.release();
                if (dataFrame.isEndStream()) {
                    sendResponse(ctx, dataFrame.stream());
                }
            } else if (msg instanceof Http2ResetFrame resetFrame) {
                System.out.println("Server received RST_STREAM on stream " + resetFrame.stream().id() +
                        ", errorCode=" + resetFrame.errorCode());
            } else {
                System.out.println("Server received unknown frame: " + msg.getClass().getSimpleName());
            }
        }

        private void sendResponse(ChannelHandlerContext ctx, Http2FrameStream stream) {
            System.out.println("Server sending response on stream " + stream.id());
            Http2Headers headers = new DefaultHttp2Headers()
                    .status("200")
                    .set("content-type", "application/json")
                    .setInt("content-length", CONTENT.length);
            ctx.write(new DefaultHttp2HeadersFrame(headers).stream(stream));
            ctx.writeAndFlush(new DefaultHttp2DataFrame(
                    Unpooled.wrappedBuffer(CONTENT),
                    true).stream(stream));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("Server error: " + cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
    }
}
