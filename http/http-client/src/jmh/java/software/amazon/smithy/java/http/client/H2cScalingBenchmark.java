/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http2.Http2Client;
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
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import software.amazon.smithy.java.http.client.connection.ConnectionPoolListener;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * HTTP/2 cleartext (h2c) client scaling benchmark.
 *
 * <p>For H2, the key parameters are:
 * <ul>
 *   <li>concurrency - number of virtual threads making requests</li>
 *   <li>connections - number of H2 connections (each multiplexes many streams)</li>
 *   <li>streamsPerConnection - max concurrent streams per connection</li>
 * </ul>
 *
 * <p>Effective parallelism ≈ connections × streamsPerConnection.
 * Set concurrency higher to measure backpressure behavior.
 *
 * <p>Run with: ./gradlew :http:http-client:jmh -Pjmh.includes="H2cScalingBenchmark"
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g", "-Xlog:gc*:stdout:time,level,tags"})
@State(Scope.Benchmark)
public class H2cScalingBenchmark {

    @Param({"10"})
    private int concurrency;

    @Param({"1", "3", "5", "10"})
    private int connections;

    @Param({"100"})
    private int streamsPerConnection;

    private HttpClient smithyClient;
    private Http2Client helidonClient;

    // Netty client state - multiple connections like Smithy
    private EventLoopGroup nettyGroup;
    private List<Channel> nettyChannels;
    private List<Http2StreamChannelBootstrap> nettyStreamBootstraps;
    private AtomicInteger smithyConnectionCount;

    @Setup(Level.Trial)
    public void setupIteration() throws Exception {
        closeClients();

        System.out.println("H2c setup: concurrency=" + concurrency
                + ", connections=" + connections
                + ", streams=" + streamsPerConnection);

        smithyConnectionCount = new AtomicInteger(0);

        // Smithy H2c client
        smithyClient = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(connections)
                        .maxTotalConnections(connections)
                        .h2StreamsPerConnection(streamsPerConnection)
                        .h2InitialWindowSize(1024 * 1024)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE)
                        .dnsResolver(BenchmarkSupport.staticDns())
                        .addListener(new ConnectionPoolListener() {
                            @Override
                            public void onConnected(HttpConnection conn) {
                                int count = smithyConnectionCount.incrementAndGet();
                                System.out.println("  [Smithy] New connection #" + count + ": " + conn);
                            }
                        })
                        .build())
                .build();

        // Helidon H2c client
        helidonClient = Http2Client.builder()
                .baseUri(BenchmarkSupport.H2C_URL)
                .shareConnectionCache(false)
                .protocolConfig(pc -> pc.priorKnowledge(true))
                .build();

        // Netty H2c client - create same number of connections as Smithy
        nettyGroup = new NioEventLoopGroup();
        nettyChannels = new ArrayList<>();
        nettyStreamBootstraps = new ArrayList<>();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(nettyGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(
                                        Http2FrameCodecBuilder.forClient()
                                                .initialSettings(
                                                        io.netty.handler.codec.http2.Http2Settings.defaultSettings()
                                                                .maxConcurrentStreams(100000)
                                                                .initialWindowSize(1024 * 1024))
                                                .build(),
                                        new Http2MultiplexHandler(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                                            @Override
                                            protected void channelRead0(
                                                    ChannelHandlerContext ctx,
                                                    Http2StreamFrame msg
                                            ) {}
                                        }));
                    }
                });
        for (int i = 0; i < connections; i++) {
            Channel ch = bootstrap.connect(new InetSocketAddress("localhost", 18081)).sync().channel();
            nettyChannels.add(ch);
            nettyStreamBootstraps.add(new Http2StreamChannelBootstrap(ch));
        }

        BenchmarkSupport.resetServer(smithyClient, BenchmarkSupport.H2C_URL);
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        String stats = BenchmarkSupport.getServerStats(smithyClient, BenchmarkSupport.H2C_URL);
        System.out.println("H2c stats [c=" + concurrency + ", conn=" + connections
                + ", streams=" + streamsPerConnection + "]: " + stats);
        closeClients();
    }

    private void closeClients() throws Exception {
        if (smithyClient != null) {
            smithyClient.close();
            smithyClient = null;
        }
        if (helidonClient != null) {
            helidonClient.closeResource();
            helidonClient = null;
        }
        if (nettyChannels != null) {
            for (Channel ch : nettyChannels) {
                ch.close().sync();
            }
            nettyChannels = null;
            nettyStreamBootstraps = null;
        }
        if (nettyGroup != null) {
            nettyGroup.shutdownGracefully().sync();
            nettyGroup = null;
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counter extends BenchmarkSupport.RequestCounter {
        @Setup(Level.Iteration)
        public void reset() {
            super.reset();
        }

        // Override getters so JMH annotation processor sees them directly
        @Override
        public long requests() {
            return requests;
        }

        @Override
        public long errors() {
            return errors;
        }
    }

    @Benchmark
    @Threads(1)
    public void smithy(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2C_URL + "/get");
        var request = HttpRequest.builder().uri(uri).method("GET").build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2c");
    }

    @Benchmark
    @Threads(1)
    public void helidon(Counter counter) throws InterruptedException {
        BenchmarkSupport.runBenchmark(concurrency, concurrency, (Http2Client client) -> {
            try (HttpClientResponse response = client.get("/get").request()) {
                response.entity().consume();
            }
        }, helidonClient, counter);

        counter.logErrors("Helidon H2c");
    }

    @Benchmark
    @Threads(1)
    public void smithyPost(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2C_URL + "/post");
        var request = HttpRequest.builder()
                .uri(uri)
                .method("POST")
                .body(DataStream.ofBytes(BenchmarkSupport.POST_PAYLOAD))
                .build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2c POST");
    }

    @Benchmark
    @Threads(1)
    public void smithyPutMb(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2C_URL + "/putmb");
        var request = HttpRequest.builder()
                .uri(uri)
                .method("PUT")
                .body(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD))
                .build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2c PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void smithyGetMb(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2C_URL + "/getmb");
        var request = HttpRequest.builder().uri(uri).method("GET").build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2c GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void netty(Counter counter) throws Exception {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        headers.method("GET");
        headers.path("/get");
        headers.scheme("http");
        headers.authority("localhost:18081");

        var connectionIndex = new AtomicInteger(0);

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (DefaultHttp2Headers h) -> {
            var latch = new CountDownLatch(1);
            var error = new AtomicReference<Throwable>();

            int idx = connectionIndex.getAndIncrement() % nettyStreamBootstraps.size();
            nettyStreamBootstraps.get(idx).open().addListener(future -> {
                if (!future.isSuccess()) {
                    error.set(future.cause());
                    latch.countDown();
                    return;
                }

                Http2StreamChannel streamChannel = (Http2StreamChannel) future.get();
                streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame frame) {
                        if (frame instanceof Http2DataFrame df) {
                            df.content().skipBytes(df.content().readableBytes());
                        }
                        boolean endStream = (frame instanceof Http2HeadersFrame hf && hf.isEndStream())
                                || (frame instanceof Http2DataFrame df2 && df2.isEndStream());
                        if (endStream) {
                            ctx.close();
                            latch.countDown();
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        error.set(cause);
                        ctx.close();
                        latch.countDown();
                    }
                });

                streamChannel.writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(h, true));
            });

            latch.await();
            if (error.get() != null) {
                throw new RuntimeException(error.get());
            }
        }, headers, counter);

        counter.logErrors("Netty H2c GET");
    }

    @Benchmark
    @Threads(1)
    public void nettyGetMb(Counter counter) throws Exception {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        headers.method("GET");
        headers.path("/getmb");
        headers.scheme("http");
        headers.authority("localhost:18081");

        var connectionIndex = new AtomicInteger(0);

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (DefaultHttp2Headers h) -> {
            var latch = new CountDownLatch(1);
            var error = new AtomicReference<Throwable>();

            int idx = connectionIndex.getAndIncrement() % nettyStreamBootstraps.size();
            nettyStreamBootstraps.get(idx).open().addListener(future -> {
                if (!future.isSuccess()) {
                    error.set(future.cause());
                    latch.countDown();
                    return;
                }

                Http2StreamChannel streamChannel = (Http2StreamChannel) future.get();
                streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                    private final byte[] copyBuf = new byte[8192];

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame frame) {
                        if (frame instanceof Http2DataFrame df) {
                            // Copy data like Smithy does, not just skip
                            var buf = df.content();
                            while (buf.readableBytes() > 0) {
                                int toRead = Math.min(buf.readableBytes(), copyBuf.length);
                                buf.readBytes(copyBuf, 0, toRead);
                            }
                        }
                        boolean endStream = (frame instanceof Http2HeadersFrame hf && hf.isEndStream())
                                || (frame instanceof Http2DataFrame df2 && df2.isEndStream());
                        if (endStream) {
                            ctx.close();
                            latch.countDown();
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        error.set(cause);
                        ctx.close();
                        latch.countDown();
                    }
                });

                streamChannel.writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(h, true));
            });

            latch.await();
            if (error.get() != null) {
                throw new RuntimeException(error.get());
            }
        }, headers, counter);

        counter.logErrors("Netty H2c GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void nettyPost(Counter counter) throws Exception {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        headers.method("POST");
        headers.path("/post");
        headers.scheme("http");
        headers.authority("localhost:18081");
        headers.setInt("content-length", BenchmarkSupport.POST_PAYLOAD.length);

        var connectionIndex = new AtomicInteger(0);

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (DefaultHttp2Headers h) -> {
            var latch = new CountDownLatch(1);
            var error = new AtomicReference<Throwable>();

            int idx = connectionIndex.getAndIncrement() % nettyStreamBootstraps.size();
            nettyStreamBootstraps.get(idx).open().addListener(future -> {
                if (!future.isSuccess()) {
                    error.set(future.cause());
                    latch.countDown();
                    return;
                }

                Http2StreamChannel streamChannel = (Http2StreamChannel) future.get();
                streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame frame) {
                        if (frame instanceof Http2DataFrame df) {
                            df.content().skipBytes(df.content().readableBytes());
                        }
                        boolean endStream = (frame instanceof Http2HeadersFrame hf && hf.isEndStream())
                                || (frame instanceof Http2DataFrame df2 && df2.isEndStream());
                        if (endStream) {
                            ctx.close();
                            latch.countDown();
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        error.set(cause);
                        ctx.close();
                        latch.countDown();
                    }
                });

                // Send headers (endStream=false since we have a body)
                streamChannel.write(new DefaultHttp2HeadersFrame(h, false));
                // Send body with endStream=true
                streamChannel.writeAndFlush(new DefaultHttp2DataFrame(
                        Unpooled.wrappedBuffer(BenchmarkSupport.POST_PAYLOAD),
                        true));
            });

            latch.await();
            if (error.get() != null) {
                throw new RuntimeException(error.get());
            }
        }, headers, counter);

        counter.logErrors("Netty H2c POST");
    }

    @Benchmark
    @Threads(1)
    public void nettyPutMb(Counter counter) throws Exception {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        headers.method("PUT");
        headers.path("/putmb");
        headers.scheme("http");
        headers.authority("localhost:18081");
        headers.setInt("content-length", BenchmarkSupport.MB_PAYLOAD.length);

        var connectionIndex = new AtomicInteger(0);

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (DefaultHttp2Headers h) -> {
            var latch = new CountDownLatch(1);
            var error = new AtomicReference<Throwable>();

            int idx = connectionIndex.getAndIncrement() % nettyStreamBootstraps.size();
            nettyStreamBootstraps.get(idx).open().addListener(future -> {
                if (!future.isSuccess()) {
                    error.set(future.cause());
                    latch.countDown();
                    return;
                }

                Http2StreamChannel streamChannel = (Http2StreamChannel) future.get();
                streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame frame) {
                        if (frame instanceof Http2DataFrame df) {
                            df.content().skipBytes(df.content().readableBytes());
                        }
                        boolean endStream = (frame instanceof Http2HeadersFrame hf && hf.isEndStream())
                                || (frame instanceof Http2DataFrame df2 && df2.isEndStream());
                        if (endStream) {
                            ctx.close();
                            latch.countDown();
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        error.set(cause);
                        ctx.close();
                        latch.countDown();
                    }
                });

                // Send headers (endStream=false since we have a body)
                streamChannel.write(new DefaultHttp2HeadersFrame(h, false));
                // Send body with endStream=true
                streamChannel.writeAndFlush(new DefaultHttp2DataFrame(
                        Unpooled.wrappedBuffer(BenchmarkSupport.MB_PAYLOAD),
                        true));
            });

            latch.await();
            if (error.get() != null) {
                throw new RuntimeException(error.get());
            }
        }, headers, counter);

        counter.logErrors("Netty H2c PUT 1MB");
    }
}
