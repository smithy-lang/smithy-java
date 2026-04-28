/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import software.amazon.smithy.java.client.http.apache.ApacheHttpClientTransport;
import software.amazon.smithy.java.client.http.apache.ApacheHttpTransportConfig;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Mixed H2 benchmark that interleaves 1 MB GETs and 1 MB PUTs on the same client.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@State(Scope.Benchmark)
public class H2MixedGetPutBenchmark {

    @Param({
            "1",
            "10"
    })
    private int concurrency;

    @Param({"1", "3"})
    private int connections;

    @Param({"4096"})
    private int streamsPerConnection;

    private HttpClient smithyClient;
    private HttpClient smithyPlatformReaderClient;
    private HttpClient connectionAgentClient;
    private java.net.http.HttpClient javaClient;
    private ExecutorService javaExecutor;
    private JavaHttpClientTransport javaTransport;
    private ApacheHttpClientTransport apacheTransport;
    private software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport productionNettyTransport;
    private software.amazon.smithy.java.context.Context transportContext;
    private MixedRequests mixedRequests;
    private String runId;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        var sslContext = BenchmarkSupport.trustAllSsl();

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

        smithyPlatformReaderClient = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(connections)
                        .maxTotalConnections(connections)
                        .h2StreamsPerConnection(streamsPerConnection)
                        .h2InitialWindowSize(16 * 1024 * 1024)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_2)
                        .sslContext(sslContext)
                        .dnsResolver(BenchmarkSupport.staticDns())
                        .usePlatformReaderForH2(true)
                .build())
                .build();

        connectionAgentClient = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(connections)
                        .maxTotalConnections(connections)
                        .h2StreamsPerConnection(streamsPerConnection)
                        .h2InitialWindowSize(16 * 1024 * 1024)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_2)
                        .sslContext(sslContext)
                        .dnsResolver(BenchmarkSupport.staticDns())
                        .useConnectionAgentForH2(true)
                        .build())
                .build();

        javaExecutor = Executors.newVirtualThreadPerTaskExecutor();
        javaClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .executor(javaExecutor)
                .build();
        javaTransport = new JavaHttpClientTransport(javaClient);
        var apacheConfig = new ApacheHttpTransportConfig();
        apacheConfig.httpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_2);
        apacheConfig.maxConnectionsPerHost(connections);
        apacheConfig.h2StreamsPerConnection(streamsPerConnection);
        apacheConfig.ioThreads(1);
        apacheTransport = new ApacheHttpClientTransport(apacheConfig, sslContext);

        var nettyTransportConfig = new software.amazon.smithy.java.client.http.netty.NettyHttpTransportConfig()
                .maxConnectionsPerHost(connections)
                .h2StreamsPerConnection(streamsPerConnection)
                .httpVersionPolicy(software.amazon.smithy.java.client.http.netty.HttpVersionPolicy.ENFORCE_HTTP_2);
        productionNettyTransport =
                new software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport(nettyTransportConfig);
        transportContext = software.amazon.smithy.java.context.Context.create();

        BenchmarkSupport.resetServer(smithyClient, BenchmarkSupport.H2_URL);
        runId = BenchmarkSupport.createRunId("h2-mixed");

        mixedRequests = new MixedRequests(
                new RequestPlan(
                        HttpRequest.create()
                                .setUri(SmithyUri.of(BenchmarkSupport.H2_URL + "/getmb?runId=" + runId))
                                .setMethod("GET"),
                        true,
                        0,
                        BenchmarkSupport.MB_PAYLOAD.length),
                new RequestPlan(
                        HttpRequest.create()
                                .setUri(SmithyUri.of(BenchmarkSupport.H2_URL + "/putmb?runId=" + runId))
                                .setMethod("PUT")
                                .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD)),
                        false,
                        BenchmarkSupport.MB_PAYLOAD.length,
                        0));
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try {
            if (smithyClient != null) {
                String stats = BenchmarkSupport.getServerStats(smithyClient, BenchmarkSupport.H2_URL, runId);
                var actualStats = BenchmarkSupport.parseIoStats(stats);
                var expectedStats = mixedRequests.expectedIoStats();
                BenchmarkSupport.assertIoStats("H2 mixed server IO stats", actualStats, expectedStats);
                mixedRequests.assertClientIoMatches(expectedStats);
                System.out.println("H2 mixed GET+PUT stats [c=" + concurrency + ", conn=" + connections
                        + ", streams=" + streamsPerConnection + "]: " + stats);
                System.out.println("H2 client stats: " + BenchmarkSupport.getH2ConnectionStats(smithyClient));
            }
            if (smithyPlatformReaderClient != null) {
                System.out.println("H2 platform-reader client stats: "
                        + BenchmarkSupport.getH2ConnectionStats(smithyPlatformReaderClient));
            }
            if (connectionAgentClient != null) {
                System.out.println("Connection-agent H2 stats: "
                        + BenchmarkSupport.getH2ConnectionStats(connectionAgentClient));
            }
        } finally {
            if (connectionAgentClient != null) {
                connectionAgentClient.close();
                connectionAgentClient = null;
            }
            if (smithyClient != null) {
                smithyClient.close();
                smithyClient = null;
            }
            if (smithyPlatformReaderClient != null) {
                smithyPlatformReaderClient.close();
                smithyPlatformReaderClient = null;
            }
            if (javaClient != null) {
                javaClient.close();
                javaClient = null;
            }
            if (javaExecutor != null) {
                javaExecutor.close();
                javaExecutor = null;
            }
            if (javaTransport != null) {
                javaTransport = null;
            }
            if (apacheTransport != null) {
                apacheTransport.close();
                apacheTransport = null;
            }
            if (productionNettyTransport != null) {
                productionNettyTransport.close();
                productionNettyTransport = null;
            }
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counter extends BenchmarkSupport.RequestCounter {
        public long getRequests;
        public long putRequests;

        @Setup(Level.Trial)
        public void reset() {
            super.reset();
            getRequests = 0;
            putRequests = 0;
        }
    }

    private static final class MixedRequests {
        private final RequestPlan getRequest;
        private final RequestPlan putRequest;
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicLong totalGetRequests = new AtomicLong();
        private final AtomicLong totalPutRequests = new AtomicLong();
        private final AtomicLong clientRequestBytes = new AtomicLong();
        private final AtomicLong clientResponseBytes = new AtomicLong();

        private MixedRequests(RequestPlan getRequest, RequestPlan putRequest) {
            this.getRequest = getRequest;
            this.putRequest = putRequest;
        }

        private RequestPlan next() {
            if ((sequence.getAndIncrement() & 1) == 0) {
                totalGetRequests.incrementAndGet();
                return getRequest;
            }
            totalPutRequests.incrementAndGet();
            return putRequest;
        }

        private void recordCompletion(RequestPlan request, long responseBytes) {
            clientRequestBytes.addAndGet(request.requestBytes());
            clientResponseBytes.addAndGet(responseBytes);
            if (responseBytes != request.responseBytes()) {
                throw new IllegalStateException("Unexpected response byte count for "
                        + (request.isGet() ? "GET" : "PUT") + ": expected=" + request.responseBytes()
                        + ", actual=" + responseBytes);
            }
        }

        private BenchmarkSupport.IoStats expectedIoStats() {
            return new BenchmarkSupport.IoStats(
                    totalGetRequests.get(),
                    totalGetRequests.get() * BenchmarkSupport.MB_PAYLOAD.length,
                    totalPutRequests.get(),
                    totalPutRequests.get() * BenchmarkSupport.MB_PAYLOAD.length);
        }

        private void assertClientIoMatches(BenchmarkSupport.IoStats expectedStats) {
            long expectedRequestBytes = expectedStats.putMbBytesReceived();
            long expectedResponseBytes = expectedStats.getMbBytesSent();
            if (clientRequestBytes.get() != expectedRequestBytes || clientResponseBytes.get() != expectedResponseBytes) {
                throw new IllegalStateException("H2 mixed client IO mismatch. expectedRequestBytes="
                        + expectedRequestBytes + ", actualRequestBytes=" + clientRequestBytes.get()
                        + ", expectedResponseBytes=" + expectedResponseBytes + ", actualResponseBytes="
                        + clientResponseBytes.get());
            }
        }
    }

    private record RequestPlan(HttpRequest request, boolean isGet, long requestBytes, long responseBytes) {}

    @Benchmark
    @Threads(1)
    public void h2SmithyMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = smithyClient.send(request.request())) {
                long responseBytes = response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
                requests.recordCompletion(request, responseBytes);
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Smithy H2 mixed GET+PUT");
        counter.throwIfErrored("Smithy H2 mixed GET+PUT");
    }

    @Benchmark
    @Threads(1)
    public void h2ConnectionAgentMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = connectionAgentClient.send(request.request())) {
                long responseBytes = response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
                requests.recordCompletion(request, responseBytes);
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Connection-agent H2 mixed GET+PUT");
        counter.throwIfErrored("Connection-agent H2 mixed GET+PUT");
    }

    @Benchmark
    @Threads(1)
    public void h2SmithyPlatformReaderMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = smithyPlatformReaderClient.send(request.request())) {
                long responseBytes = response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
                requests.recordCompletion(request, responseBytes);
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Smithy H2 platform-reader mixed GET+PUT");
        counter.throwIfErrored("Smithy H2 platform-reader mixed GET+PUT");
    }

    @Benchmark
    @Threads(1)
    public void h2JavaWrapperMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = javaTransport.send(transportContext, request.request())) {
                long responseBytes = response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
                requests.recordCompletion(request, responseBytes);
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Java-wrapper H2 mixed GET+PUT");
        counter.throwIfErrored("Java-wrapper H2 mixed GET+PUT");
    }

    @Benchmark
    @Threads(1)
    public void h2ProductionNettyMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = productionNettyTransport.send(transportContext, request.request())) {
                long responseBytes = response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
                requests.recordCompletion(request, responseBytes);
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Production-Netty H2 mixed GET+PUT");
        counter.throwIfErrored("Production-Netty H2 mixed GET+PUT");
    }

    @Benchmark
    @Threads(1)
    public void h2ApacheAsyncMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = apacheTransport.send(transportContext, request.request())) {
                long responseBytes = response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
                requests.recordCompletion(request, responseBytes);
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Apache-async H2 mixed GET+PUT");
        counter.throwIfErrored("Apache-async H2 mixed GET+PUT");
    }
}
