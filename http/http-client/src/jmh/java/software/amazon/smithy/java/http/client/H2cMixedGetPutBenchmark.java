/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.OutputStream;
import java.time.Duration;
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
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Mixed H2C benchmark that interleaves 1 MB GETs and 1 MB PUTs on the same client.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@State(Scope.Benchmark)
public class H2cMixedGetPutBenchmark {

    @Param({"1", "10"})
    private int concurrency;

    @Param({"1", "3"})
    private int connections;

    @Param({"4096"})
    private int streamsPerConnection;

    private HttpClient smithyClient;
    private software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport productionNettyTransport;
    private software.amazon.smithy.java.context.Context transportContext;
    private MixedRequests mixedRequests;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        smithyClient = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(connections)
                        .maxTotalConnections(connections)
                        .h2StreamsPerConnection(streamsPerConnection)
                        .h2InitialWindowSize(16 * 1024 * 1024)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE)
                        .dnsResolver(BenchmarkSupport.staticDns())
                        .build())
                .build();

        var nettyTransportConfig = new software.amazon.smithy.java.client.http.netty.NettyHttpTransportConfig()
                .maxConnectionsPerHost(connections)
                .h2StreamsPerConnection(streamsPerConnection)
                .httpVersionPolicy(software.amazon.smithy.java.client.http.netty.HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
        productionNettyTransport =
                new software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport(nettyTransportConfig);
        transportContext = software.amazon.smithy.java.context.Context.create();

        BenchmarkSupport.resetServer(smithyClient, BenchmarkSupport.H2C_URL);

        mixedRequests = new MixedRequests(
                HttpRequest.create()
                        .setUri(SmithyUri.of(BenchmarkSupport.H2C_URL + "/getmb"))
                        .setMethod("GET"),
                HttpRequest.create()
                        .setUri(SmithyUri.of(BenchmarkSupport.H2C_URL + "/putmb"))
                        .setMethod("PUT")
                        .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD)));
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try {
            if (smithyClient != null) {
                String stats = BenchmarkSupport.getServerStats(smithyClient, BenchmarkSupport.H2C_URL);
                System.out.println("H2c mixed GET+PUT stats [c=" + concurrency + ", conn=" + connections
                        + ", streams=" + streamsPerConnection + "]: " + stats);
                System.out.println("H2c client stats: " + BenchmarkSupport.getH2ConnectionStats(smithyClient));
            }
        } finally {
            if (smithyClient != null) {
                smithyClient.close();
                smithyClient = null;
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
        private final HttpRequest getRequest;
        private final HttpRequest putRequest;
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicLong totalGetRequests = new AtomicLong();
        private final AtomicLong totalPutRequests = new AtomicLong();

        private MixedRequests(HttpRequest getRequest, HttpRequest putRequest) {
            this.getRequest = getRequest;
            this.putRequest = putRequest;
        }

        private HttpRequest next() {
            if ((sequence.getAndIncrement() & 1) == 0) {
                totalGetRequests.incrementAndGet();
                return getRequest;
            }
            totalPutRequests.incrementAndGet();
            return putRequest;
        }
    }

    @Benchmark
    @Threads(1)
    public void h2cSmithyMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = smithyClient.send(request)) {
                response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Smithy H2c mixed GET+PUT");
    }

    @Benchmark
    @Threads(1)
    public void h2cProductionNettyMixedGetPutMb(Counter counter) throws InterruptedException {
        long startGet = mixedRequests.totalGetRequests.get();
        long startPut = mixedRequests.totalPutRequests.get();
        BenchmarkSupport.runBenchmark(concurrency, concurrency * 2, (MixedRequests requests) -> {
            var request = requests.next();
            try (var response = productionNettyTransport.send(transportContext, request)) {
                response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, mixedRequests, counter);
        counter.getRequests = mixedRequests.totalGetRequests.get() - startGet;
        counter.putRequests = mixedRequests.totalPutRequests.get() - startPut;

        counter.logErrors("Production-Netty H2c mixed GET+PUT");
    }
}
