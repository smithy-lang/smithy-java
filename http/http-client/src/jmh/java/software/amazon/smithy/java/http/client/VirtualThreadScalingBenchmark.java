/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2Client;
import io.netty.bootstrap.Bootstrap;
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
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
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
import software.amazon.smithy.java.http.client.dns.DnsResolver;

/**
 * Benchmark comparing HTTP/1.1 vs HTTP/2 clients at extreme concurrency.
 *
 * <p>Uses an external Netty server (separate process) to get clean flame graphs
 * without server code polluting the profile.
 *
 * <p>Run with external server (recommended for profiling):
 * <pre>
 * ./gradlew :http:http-client:jmhWithServer
 * </pre>
 *
 * <p>Or manually:
 * <pre>
 * # Terminal 1: Start server
 * ./gradlew :http:http-client:startBenchmarkServer
 *
 * # Terminal 2: Run benchmark
 * ./gradlew :http:http-client:jmh --args='-p externalServer=http://localhost:PORT'
 *
 * # Terminal 1: Stop server when done
 * ./gradlew :http:http-client:stopBenchmarkServer
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 4)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@State(Scope.Benchmark)
public class VirtualThreadScalingBenchmark {

    // Note: this takes _forever_, so comment out whatever you don't want to try if you want it to go faster.
    @Param({
            //"100", "1000", "5000", "10000", "20000",
            "30000"})
    private int concurrency;

    @Param({//"1", "2", "3", "4", "5", "10",
            "20",
            //"50"
    })
    private int connectionLimit;

    @Param({
            //            "100", "1024", "2048",
            "4096"
            //            , "8192"
    })
    private int streamsLimit;

    // Server URL - use jmhWithServer task or set manually
    // For h2c: "http://localhost:18081" (default)
    // For h2 TLS: "https://localhost:18443"
    // For h1: "http://localhost:18080"
    @Param({"http://localhost:18081"})
    private String externalServer;

    // HTTP/1.1 clients
    private HttpClient smithyClientH1;
    private CloseableHttpClient apacheClientH1;
    private WebClient helidonClientH1;

    // HTTP/2 cleartext clients (h2c)
    private HttpClient smithyClientH2c;
    private Http2Client helidonClientH2c;

    // HTTP/2 TLS clients (h2)
    private HttpClient smithyClientH2;
    private Http2Client helidonClientH2;

    // Netty HTTP/2 client (raw, no abstractions)
    private EventLoopGroup nettyEventLoopGroup;
    private Bootstrap nettyBootstrap;
    private String nettyH2cHost;
    private int nettyH2cPort;

    // Server URLs
    private String h1BaseUrl;
    private String h2BaseUrl;
    private String h2cBaseUrl;

    // SSL context for clients
    private SSLContext trustAllSslContext;

    @Setup
    public void setup() throws Exception {
        if (externalServer == null || externalServer.isEmpty()) {
            throw new IllegalStateException(
                    "externalServer parameter is required. Use ./gradlew :http:http-client:jmhWithServer " +
                            "or set -p externalServer=http://localhost:PORT");
        }

        // Fixed server ports (must match BenchmarkServer.java)
        // externalServer is used as the base host, ports are fixed
        String host = externalServer.replaceAll("https?://", "").replaceAll(":\\d+.*", "");
        h1BaseUrl = "http://" + host + ":18080";
        h2BaseUrl = "https://" + host + ":18443";
        h2cBaseUrl = "http://" + host + ":18081";

        System.out.println("Using external server: " + externalServer);

        // Create trust-all SSL context for self-signed cert
        trustAllSslContext = createTrustAllSslContext();

        // Static DNS resolver to bypass DNS overhead
        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));

        // ===== HTTP/1.1 Clients =====

        // Smithy H1 client
        smithyClientH1 = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(100)
                        .maxTotalConnections(100)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .dnsResolver(staticDns)
                        .build())
                .build();

        // Apache H1 client (Apache HttpClient 5 classic API only supports H1)
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(5000);
        connectionManager.setDefaultMaxPerRoute(5000);
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setSocketTimeout(Timeout.ofSeconds(30))
                .build());

        apacheClientH1 = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();

        // Helidon H1 client
        helidonClientH1 = WebClient.builder()
                .baseUri(h1BaseUrl)
                .shareConnectionCache(false)
                .connectionCacheSize(5000)
                .build();

        // ===== HTTP/2 Cleartext Clients (h2c) =====

        // Smithy H2c client - created per iteration in setupIteration()

        // Helidon H2c client (cleartext, prior knowledge)
        helidonClientH2c = Http2Client.builder()
                .baseUri(h2cBaseUrl)
                .shareConnectionCache(false)
                .protocolConfig(pc -> pc.priorKnowledge(true))
                .build();

        // ===== HTTP/2 TLS Clients (h2 with ALPN) =====

        // Smithy H2 client (TLS with ALPN)
        smithyClientH2 = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(10000)
                        .maxTotalConnections(10000)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .dnsResolver(staticDns)
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_2)
                        .sslContext(trustAllSslContext)
                        .h2StreamsPerConnection(100)
                        .build())
                .build();

        // Helidon H2 client (TLS with ALPN)
        // priorKnowledge(false) = use ALPN for protocol negotiation over TLS
        // shareConnectionCache(true) is essential for HTTP/2 multiplexing
        helidonClientH2 = Http2Client.builder()
                .baseUri(h2BaseUrl)
                .shareConnectionCache(true)
                .tls(Tls.builder().trustAll(true).build())
                .protocolConfig(pc -> pc.priorKnowledge(false))
                .build();

        // ===== Netty HTTP/2 Client (raw, no abstractions) =====
        nettyEventLoopGroup = new NioEventLoopGroup();
        nettyBootstrap = new Bootstrap();
        nettyBootstrap.group(nettyEventLoopGroup)
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
                                                                .maxConcurrentStreams(10000))
                                                .build(),
                                        new Http2MultiplexHandler(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                                            @Override
                                            protected void channelRead0(
                                                    ChannelHandlerContext ctx,
                                                    Http2StreamFrame msg
                                            ) {
                                                // Inbound stream handler (for server push, not used here)
                                            }
                                        }));
                    }
                });

        // Store host/port for connection during benchmark (not pre-connected)
        nettyH2cHost = h2cBaseUrl.replaceAll("https?://", "").replaceAll(":\\d+.*", "");
        nettyH2cPort = 18081;
    }

    private SSLContext createTrustAllSslContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        return sslContext;
    }

    @TearDown
    public void teardown() throws Exception {
        // Close H1 clients
        if (smithyClientH1 != null) {
            smithyClientH1.close();
        }
        if (apacheClientH1 != null) {
            apacheClientH1.close();
        }
        if (helidonClientH1 != null) {
            helidonClientH1.closeResource();
        }

        // Close H2c clients
        // smithyClientH2c closed per-iteration in setupIteration()
        if (helidonClientH2c != null) {
            helidonClientH2c.closeResource();
        }

        // Close H2 TLS clients
        if (smithyClientH2 != null) {
            smithyClientH2.close();
        }
        if (helidonClientH2 != null) {
            helidonClientH2.closeResource();
        }

        // Close Netty event loop group
        if (nettyEventLoopGroup != null) {
            nettyEventLoopGroup.shutdownGracefully().sync();
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class RequestCounter {
        public long requests;
        public long errors;

        @Setup(Level.Iteration)
        public void reset() {
            requests = 0;
            errors = 0;
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        if (smithyClientH2c != null) {
            smithyClientH2c.close();
        }

        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));

        System.out.println("Creating client: connectionLimit=" + connectionLimit + ", streamsLimit=" + streamsLimit);

        smithyClientH2c = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(connectionLimit)
                        .h2StreamsPerConnection(streamsLimit)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .dnsResolver(staticDns)
                        .httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE)
                        .build())
                .build();

        try (var res = smithyClientH2c.send(HttpRequest.builder()
                .uri(URI.create(h2cBaseUrl + "/reset"))
                .method("POST")
                .build())) {
            res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }
        Thread.sleep(100);
    }

    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        try (var res = smithyClientH2c.send(HttpRequest.builder()
                .uri(URI.create(h2cBaseUrl + "/stats"))
                .method("GET")
                .build())) {
            String stats =
                    new String(res.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Server stats [connectionLimit=" + connectionLimit + ", streamsLimit=" + streamsLimit
                    + "]: " + stats);
        }
    }

    // ===== HTTP/1.1 Benchmarks =====

    /**
     * Smithy HTTP/1.1 client throughput with virtual threads.
     */
    @Benchmark
    @Threads(1)
    public void smithyH1(RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        var running = new AtomicBoolean(true);
        var latch = new CountDownLatch(concurrency);
        var uri = URI.create(h1BaseUrl + "/get");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            // Close will drain the remaining bytes
                            smithyClientH1.send(HttpRequest.builder().uri(uri).method("GET").build()).close();
                            requests.incrementAndGet();
                        }
                    } catch (IOException e) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Thread.sleep(1000);
            running.set(false);
            var _ignored = latch.await(5, TimeUnit.SECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();

        // Log first error for debugging
        if (firstError.get() != null) {
            System.err.println("errors: " + errors.get() + ", first error:");
            firstError.get().printStackTrace(System.err);
        }
    }

    /**
     * Apache HTTP/1.1 client throughput with virtual threads.
     * Note: Apache HttpClient 5 classic API only supports HTTP/1.1.
     */
    @Benchmark
    @Threads(1)
    public void apacheH1(RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        var running = new AtomicBoolean(true);
        var latch = new CountDownLatch(concurrency);
        var target = h1BaseUrl + "/get";

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            HttpGet request = new HttpGet(target);
                            try (CloseableHttpResponse response = apacheClientH1.execute(request)) {
                                EntityUtils.consume(response.getEntity());
                            }
                            requests.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Thread.sleep(1000);
            running.set(false);
            var _ignored = latch.await(5, TimeUnit.SECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();
    }

    /**
     * Helidon HTTP/1.1 client throughput with virtual threads.
     */
    @Benchmark
    @Threads(1)
    public void helidonH1(RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        var running = new AtomicBoolean(true);
        var latch = new CountDownLatch(concurrency);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            try (HttpClientResponse response = helidonClientH1.get("/get").request()) {
                                response.entity().consume();
                            }
                            requests.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Thread.sleep(1000);
            running.set(false);
            var _ignored = latch.await(5, TimeUnit.SECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();
    }

    // ===== HTTP/2c Benchmarks =====

    @Benchmark
    @Threads(1)
    public void smithyH2c(RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        var running = new AtomicBoolean(true);
        var latch = new CountDownLatch(concurrency);
        var uri = URI.create(h2cBaseUrl + "/get");
        var request = HttpRequest.builder().uri(uri).method("GET").build();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            try (var res = smithyClientH2c.send(request)) {
                                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
                                requests.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Thread.sleep(1000);
            running.set(false);
            var _ignored = latch.await(5, TimeUnit.SECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();

        // Log first error for debugging
        if (firstError.get() != null) {
            System.err.println("errors: " + errors.get() + ", first error:");
            firstError.get().printStackTrace(System.err);
        }
    }

    @Benchmark
    @Threads(1)
    public void helidonH2c(RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        var firstError = new AtomicReference<Throwable>();
        var running = new AtomicBoolean(true);
        var latch = new CountDownLatch(concurrency);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            try (HttpClientResponse response = helidonClientH2c.get("/get").request()) {
                                response.entity().consume();
                            }
                            requests.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Thread.sleep(1000);
            running.set(false);
            var _ignored = latch.await(5, TimeUnit.SECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();

        // Log first error for debugging
        if (firstError.get() != null) {
            System.err.println("Helidon H2c errors: " + errors.get() + ", first error:");
            firstError.get().printStackTrace(System.err);
        }
    }

    /**
     * Raw Netty HTTP/2 client throughput with single connection.
     */
    @Benchmark
    @Threads(1)
    public void nettyH2c(RequestCounter counter) throws InterruptedException {
        runNettyH2c(1, counter);
    }

    /**
     * Raw Netty HTTP/2 client throughput with pooled connections.
     */
    @Benchmark
    @Threads(1)
    public void nettyH2cPooled(RequestCounter counter) throws InterruptedException {
        runNettyH2c(3, counter);
    }

    private void runNettyH2c(int numConnections, RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        var firstError = new AtomicReference<Throwable>();
        var running = new AtomicBoolean(true);
        var activeTasks = new AtomicLong(concurrency);

        // Connect to h2c server during benchmark
        List<Channel> channels = new ArrayList<>();
        try {
            for (int i = 0; i < numConnections; i++) {
                channels.add(
                        nettyBootstrap.connect(new InetSocketAddress(nettyH2cHost, nettyH2cPort)).sync().channel());
            }
        } catch (Exception e) {
            counter.errors = 1;
            return;
        }

        try {
            // Create stream bootstraps for each connection
            List<Http2StreamChannelBootstrap> streamBootstraps = channels.stream()
                    .map(Http2StreamChannelBootstrap::new)
                    .toList();

            // Pre-create headers
            DefaultHttp2Headers headers = new DefaultHttp2Headers();
            headers.method("GET");
            headers.path("/get");
            headers.scheme("http");
            headers.authority("localhost:18081");

            // Handler that opens a new stream, sends request, and repeats on response
            Runnable[] makeRequest = new Runnable[1];
            var connectionIndex = new AtomicInteger(0);
            makeRequest[0] = () -> {
                if (!running.get()) {
                    activeTasks.decrementAndGet();
                    return;
                }

                // Round-robin across connections
                int idx = connectionIndex.getAndIncrement() % streamBootstraps.size();
                Http2StreamChannelBootstrap streamBootstrap = streamBootstraps.get(idx);

                streamBootstrap.open().addListener(future -> {
                    if (!future.isSuccess()) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, future.cause());
                        if (!running.get()) {
                            activeTasks.decrementAndGet();
                        } else {
                            makeRequest[0].run();
                        }
                        return;
                    }

                    Http2StreamChannel streamChannel = (Http2StreamChannel) future.get();
                    streamChannel.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame frame) {
                            if (frame instanceof io.netty.handler.codec.http2.Http2DataFrame dataFrame) {
                                dataFrame.content().readableBytes();
                            }

                            boolean endStream = false;
                            if (frame instanceof Http2HeadersFrame headersFrame) {
                                endStream = headersFrame.isEndStream();
                            } else if (frame instanceof io.netty.handler.codec.http2.Http2DataFrame dataFrame) {
                                endStream = dataFrame.isEndStream();
                            }

                            if (endStream) {
                                requests.incrementAndGet();
                                ctx.close();
                                makeRequest[0].run();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            errors.incrementAndGet();
                            firstError.compareAndSet(null, cause);
                            ctx.close();
                            makeRequest[0].run();
                        }
                    });

                    streamChannel
                            .writeAndFlush(new io.netty.handler.codec.http2.DefaultHttp2HeadersFrame(headers, true));
                });
            };

            for (int i = 0; i < concurrency; i++) {
                makeRequest[0].run();
            }

            Thread.sleep(1000);
            running.set(false);

            long deadline = System.currentTimeMillis() + 5000;
            while (activeTasks.get() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
        } finally {
            for (Channel ch : channels) {
                ch.close().sync();
            }
        }

        counter.requests = requests.get();
        counter.errors = errors.get();

        if (firstError.get() != null) {
            System.err.println("Netty H2c errors: " + errors.get() + ", first error:");
            firstError.get().printStackTrace(System.err);
        }
    }

    // ===== HTTP/2 TLS Benchmarks =====

    @Benchmark
    @Threads(1)
    public void smithyH2(RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        var firstError = new AtomicReference<Throwable>();
        var running = new AtomicBoolean(true);
        var latch = new CountDownLatch(concurrency);
        var uri = URI.create(h2BaseUrl + "/get");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            smithyClientH2.send(HttpRequest.builder().uri(uri).method("GET").build());
                            requests.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Thread.sleep(1000);
            running.set(false);
            var _ignored = latch.await(5, TimeUnit.SECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();

        // Log first error for debugging
        if (firstError.get() != null) {
            System.err.println("H2 TLS errors: " + errors.get() + ", first error:");
            firstError.get().printStackTrace(System.err);
        }
    }

    @Benchmark
    @Threads(1)
    public void helidonH2(RequestCounter counter) throws InterruptedException {
        var requests = new AtomicLong();
        var errors = new AtomicLong();
        var firstError = new AtomicReference<Throwable>();
        var running = new AtomicBoolean(true);
        var latch = new CountDownLatch(concurrency);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        while (running.get()) {
                            try (HttpClientResponse response = helidonClientH2.get("/get").request()) {
                                response.entity().consume();
                            }
                            requests.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            Thread.sleep(1000);
            running.set(false);
            var _ignored = latch.await(5, TimeUnit.SECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();

        // Log first error for debugging
        if (firstError.get() != null) {
            System.err.println("Helidon H2 TLS errors: " + errors.get() + ", first error:");
            firstError.get().printStackTrace(System.err);
        }
    }
}
