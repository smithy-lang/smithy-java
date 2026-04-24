/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * HTTP/2 over TLS (h2) benchmark comparing Smithy and Java HttpClient.
 *
 * <p>Run with: ./gradlew :http:http-client:jmh -Pjmh.includes="H2ScalingBenchmark"
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@State(Scope.Benchmark)
public class H2ScalingBenchmark {

    @Param({
            "1",
            "10"
            //, "100", "1000"
    })
    private int concurrency;

    @Param({"3"})
    private int connections;

    @Param({"4096"})
    private int streamsPerConnection;

    private HttpClient smithyClient;
    private java.net.http.HttpClient javaClient;
    private EventLoopGroup nettyGroup;
    private Channel nettyChannel;
    private NettyH2Transport nettyTransport;
    private EventLoopH2Transport eventLoopTransport;
    private software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport productionNettyTransport;
    private software.amazon.smithy.java.context.Context transportContext;

    @Setup(Level.Trial)
    public void setupIteration() throws Exception {
        closeClients();

        System.out.println("H2 setup: concurrency=" + concurrency
                + ", connections=" + connections
                + ", streams=" + streamsPerConnection);

        var sslContext = BenchmarkSupport.trustAllSsl();

        // Smithy H2 client
        smithyClient = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(connections)
                        .maxTotalConnections(connections)
                        .h2StreamsPerConnection(streamsPerConnection)
                        .h2InitialWindowSize(16 * 1024 * 1024)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_2)
                        .sslContext(sslContext)
                        .dnsResolver(BenchmarkSupport.staticDns())
                        .build())
                .build();

        // Java HttpClient (HTTP/2 over TLS)
        javaClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .build();

        BenchmarkSupport.resetServer(smithyClient, BenchmarkSupport.H2_URL);

        // Netty H2 client
        SslContext nettySslCtx = SslContextBuilder.forClient()
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2))
                .build();

        nettyGroup = new NioEventLoopGroup(1);
        var h2FrameCodec = Http2FrameCodecBuilder.forClient()
                .initialSettings(Http2Settings.defaultSettings()
                        .initialWindowSize(1024 * 1024)
                        .maxConcurrentStreams(4096))
                .build();

        Bootstrap b = new Bootstrap();
        b.group(nettyGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(nettySslCtx.newHandler(ch.alloc(), "localhost", 18443));
                        ch.pipeline().addLast(h2FrameCodec);
                        ch.pipeline()
                                .addLast(new Http2MultiplexHandler(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {}
                                }));
                    }
                });
        nettyChannel = b.connect("localhost", 18443).sync().channel();

        // Netty-backed Smithy transport prototype
        nettyTransport = new NettyH2Transport("localhost", 18443);

        // Event-loop prototype (Phase 1+2: non-blocking TLS + single-thread H2)
        eventLoopTransport = new EventLoopH2Transport("localhost", 18443);

        // Productionized client-http-netty transport
        var nettyTransportConfig = new software.amazon.smithy.java.client.http.netty.NettyHttpTransportConfig()
                .maxConnectionsPerHost(connections)
                .h2StreamsPerConnection(streamsPerConnection)
                .httpVersionPolicy(software.amazon.smithy.java.client.http.netty.HttpVersionPolicy.ENFORCE_HTTP_2);
        productionNettyTransport = new software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport(nettyTransportConfig);
        transportContext = software.amazon.smithy.java.context.Context.create();
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        String stats = BenchmarkSupport.getServerStats(smithyClient, BenchmarkSupport.H2_URL);
        System.out.println("H2 stats [c=" + concurrency + ", conn=" + connections
                + ", streams=" + streamsPerConnection + "]: " + stats);
        System.out.println("H2 client stats: " + BenchmarkSupport.getH2ConnectionStats(smithyClient));
        closeClients();
    }

    private void closeClients() throws Exception {
        if (smithyClient != null) {
            smithyClient.close();
            smithyClient = null;
        }
        if (javaClient != null) {
            javaClient.close();
            javaClient = null;
        }
        if (nettyChannel != null) {
            nettyChannel.close().sync();
            nettyChannel = null;
        }
        if (nettyGroup != null) {
            nettyGroup.shutdownGracefully().sync();
            nettyGroup = null;
        }
        if (nettyTransport != null) {
            nettyTransport.close();
            nettyTransport = null;
        }
        if (eventLoopTransport != null) {
            eventLoopTransport.close();
            eventLoopTransport = null;
        }
        if (productionNettyTransport != null) {
            productionNettyTransport.close();
            productionNettyTransport = null;
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counter extends BenchmarkSupport.RequestCounter {
        @Setup(Level.Trial)
        public void reset() {
            super.reset();
        }
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyGet(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/get");
        var request = HttpRequest.create().setUri(uri).setMethod("GET");

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2");
    }

    @Benchmark
    @Threads(1)
    public void h2JdkGet(Counter counter) throws InterruptedException {
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(BenchmarkSupport.H2_URL + "/get"))
                .GET()
                .build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (java.net.http.HttpRequest req) -> {
            var response = javaClient.send(req, BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                body.transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Java HttpClient H2");
    }

    @Benchmark
    @Threads(1)
    public void h2NettyGet(Counter counter) throws InterruptedException {
        BenchmarkSupport.runBenchmark(concurrency, concurrency, (Channel ch) -> {
            var streamBootstrap = new Http2StreamChannelBootstrap(ch);
            var future = new CompletableFuture<Void>();
            Http2StreamChannel stream = streamBootstrap.open().sync().getNow();
            stream.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                    if (msg instanceof Http2DataFrame data) {
                        // Consume data
                        if (data.isEndStream()) {
                            future.complete(null);
                        }
                    } else if (msg instanceof Http2HeadersFrame headers) {
                        if (headers.isEndStream()) {
                            future.complete(null);
                        }
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    future.completeExceptionally(cause);
                }
            });

            var headers = new DefaultHttp2Headers()
                    .method("GET")
                    .path("/get")
                    .scheme("https")
                    .authority("localhost:18443");
            stream.writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(headers, true));
            future.join();
        }, nettyChannel, counter);

        counter.logErrors("Netty H2");
    }

    @Benchmark
    @Threads(1)
    public void h2NettyPost(Counter counter) throws InterruptedException {
        BenchmarkSupport.runBenchmark(concurrency, concurrency, (Channel ch) -> {
            var future = new CompletableFuture<Void>();
            Http2StreamChannel stream = new Http2StreamChannelBootstrap(ch).open().sync().getNow();
            stream.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                    if (msg instanceof Http2DataFrame data && data.isEndStream()) {
                        future.complete(null);
                    } else if (msg instanceof Http2HeadersFrame headers && headers.isEndStream()) {
                        future.complete(null);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    future.completeExceptionally(cause);
                }
            });
            var headers = new DefaultHttp2Headers()
                    .method("POST")
                    .path("/post")
                    .scheme("https")
                    .authority("localhost:18443");
            stream.write(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(headers, false));
            stream.writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2DataFrame(
                    Unpooled.wrappedBuffer(BenchmarkSupport.POST_PAYLOAD),
                    true));
            future.join();
        }, nettyChannel, counter);

        counter.logErrors("Netty H2 POST");
    }

    @Benchmark
    @Threads(1)
    public void h2NettyPutMb(Counter counter) throws InterruptedException {
        BenchmarkSupport.runBenchmark(concurrency, concurrency, (Channel ch) -> {
            var future = new CompletableFuture<Void>();
            Http2StreamChannel stream = new Http2StreamChannelBootstrap(ch).open().sync().getNow();
            stream.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                    if (msg instanceof Http2DataFrame data && data.isEndStream()) {
                        future.complete(null);
                    } else if (msg instanceof Http2HeadersFrame headers && headers.isEndStream()) {
                        future.complete(null);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    future.completeExceptionally(cause);
                }
            });
            var headers = new DefaultHttp2Headers()
                    .method("PUT")
                    .path("/putmb")
                    .scheme("https")
                    .authority("localhost:18443");
            stream.write(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(headers, false));
            stream.writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2DataFrame(
                    Unpooled.wrappedBuffer(BenchmarkSupport.MB_PAYLOAD),
                    true));
            future.join();
        }, nettyChannel, counter);

        counter.logErrors("Netty H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyPost(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/post");
        var request = HttpRequest.create()
                .setUri(uri)
                .setMethod("POST")
                .setBody(DataStream.ofBytes(BenchmarkSupport.POST_PAYLOAD));

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2 POST");
    }

    @Benchmark
    @Threads(1)
    public void h2JdkPost(Counter counter) throws InterruptedException {
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(BenchmarkSupport.H2_URL + "/post"))
                .POST(BodyPublishers.ofByteArray(BenchmarkSupport.POST_PAYLOAD))
                .build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (java.net.http.HttpRequest req) -> {
            var response = javaClient.send(req, BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                body.transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Java HttpClient H2 POST");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyPutMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/putmb");
        var request = HttpRequest.create()
                .setUri(uri)
                .setMethod("PUT")
                .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD));

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyNettyPutMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/putmb");
        var request = HttpRequest.create()
                .setUri(uri)
                .setMethod("PUT")
                .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD));

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            var res = nettyTransport.send(req);
            res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }, request, counter);

        counter.logErrors("Smithy-on-Netty H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyEventLoopPutMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/putmb");
        var request = HttpRequest.create()
                .setUri(uri)
                .setMethod("PUT")
                .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD));

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            var res = eventLoopTransport.send(req);
            res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }, request, counter);

        counter.logErrors("Smithy-EventLoop H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2ProductionNettyPutMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/putmb");
        var request = HttpRequest.create()
                .setUri(uri)
                .setMethod("PUT")
                .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD));

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            var res = productionNettyTransport.send(transportContext, req);
            res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }, request, counter);

        counter.logErrors("Production-Netty H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2ProductionNettyGetMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/getmb");
        var request = HttpRequest.create().setUri(uri).setMethod("GET");

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            var res = productionNettyTransport.send(transportContext, req);
            res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }, request, counter);

        counter.logErrors("Production-Netty H2 GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2ProductionNettyGet10Mb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/get10mb");
        var request = HttpRequest.create().setUri(uri).setMethod("GET");

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            var res = productionNettyTransport.send(transportContext, req);
            res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }, request, counter);

        counter.logErrors("Production-Netty H2 GET 10MB");
    }

    @Benchmark
    @Threads(1)
    public void h2JdkPutMb(Counter counter) throws InterruptedException {
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(BenchmarkSupport.H2_URL + "/putmb"))
                .PUT(BodyPublishers.ofByteArray(BenchmarkSupport.MB_PAYLOAD))
                .build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (java.net.http.HttpRequest req) -> {
            var response = javaClient.send(req, BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                body.transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Java HttpClient H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyGetMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/getmb");
        var request = HttpRequest.create().setUri(uri).setMethod("GET");

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2 GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyGetMbChannel(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/getmb");
        var request = HttpRequest.create().setUri(uri).setMethod("GET");
        var drainBuf = ByteBuffer.allocate(65536);

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                var ch = res.body().asChannel();
                while (ch.read(drainBuf) >= 0) {
                    drainBuf.clear();
                }
            }
        }, request, counter);

        counter.logErrors("Smithy H2 GET 1MB (channel)");
    }

    @Benchmark
    @Threads(1)
    public void h2JdkGetMb(Counter counter) throws InterruptedException {
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(BenchmarkSupport.H2_URL + "/getmb"))
                .GET()
                .build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (java.net.http.HttpRequest req) -> {
            var response = javaClient.send(req, BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                body.transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Java HttpClient H2 GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2NettyGetMb(Counter counter) throws InterruptedException {
        BenchmarkSupport.runBenchmark(concurrency, concurrency, (Channel ch) -> {
            var future = new CompletableFuture<Void>();
            Http2StreamChannel stream = new Http2StreamChannelBootstrap(ch).open().sync().getNow();
            stream.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                    if (msg instanceof Http2DataFrame data) {
                        if (data.isEndStream()) {
                            future.complete(null);
                        }
                    } else if (msg instanceof Http2HeadersFrame headers) {
                        if (headers.isEndStream()) {
                            future.complete(null);
                        }
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    future.completeExceptionally(cause);
                }
            });

            var headers = new DefaultHttp2Headers()
                    .method("GET")
                    .path("/getmb")
                    .scheme("https")
                    .authority("localhost:18443");
            stream.writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(headers, true));
            future.join();
        }, nettyChannel, counter);

        counter.logErrors("Netty H2 GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyGet10MbChannel(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/get10mb");
        var request = HttpRequest.create().setUri(uri).setMethod("GET");
        var drainBuf = ByteBuffer.allocate(65536);

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                var ch = res.body().asChannel();
                while (ch.read(drainBuf) >= 0) {
                    drainBuf.clear();
                }
            }
        }, request, counter);

        counter.logErrors("Smithy H2 GET 10MB (channel)");
    }

    @Benchmark
    @Threads(1)
    public void h2NettyGet10Mb(Counter counter) throws InterruptedException {
        BenchmarkSupport.runBenchmark(concurrency, concurrency, (Channel ch) -> {
            var future = new CompletableFuture<Void>();
            Http2StreamChannel stream = new Http2StreamChannelBootstrap(ch).open().sync().getNow();
            stream.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                    if (msg instanceof Http2DataFrame data) {
                        if (data.isEndStream())
                            future.complete(null);
                    } else if (msg instanceof Http2HeadersFrame headers) {
                        if (headers.isEndStream())
                            future.complete(null);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    future.completeExceptionally(cause);
                }
            });
            var headers = new DefaultHttp2Headers()
                    .method("GET")
                    .path("/get10mb")
                    .scheme("https")
                    .authority("localhost:18443");
            stream.writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(headers, true));
            future.join();
        }, nettyChannel, counter);

        counter.logErrors("Netty H2 GET 10MB");
    }
}
