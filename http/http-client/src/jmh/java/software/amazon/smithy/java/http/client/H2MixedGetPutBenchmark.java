/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.OutputStream;
import java.net.http.HttpClient;
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
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Mixed H2 benchmark that interleaves 1 MB GETs and 1 MB PUTs on the JDK transport.
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

    private HttpClient benchmarkClient;
    private HttpClient javaClient;
    private ExecutorService javaExecutor;
    private JavaHttpClientTransport javaTransport;
    private Context transportContext;
    private MixedRequests mixedRequests;
    private String runId;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        var sslContext = BenchmarkSupport.trustAllSsl();

        benchmarkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        javaExecutor = Executors.newVirtualThreadPerTaskExecutor();
        javaClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .executor(javaExecutor)
                .build();
        javaTransport = new JavaHttpClientTransport(javaClient);
        transportContext = Context.create();

        BenchmarkSupport.resetServer(benchmarkClient, BenchmarkSupport.H2_URL);
        runId = BenchmarkSupport.createRunId("h2-mixed");

        mixedRequests = new MixedRequests(
                new RequestPlan(
                        HttpRequest.create()
                                .setUri(SmithyUri.of(BenchmarkSupport.H2_URL + "/getmb?runId=" + runId))
                                .setHttpVersion(HttpVersion.HTTP_2)
                                .setMethod("GET"),
                        true,
                        0,
                        BenchmarkSupport.MB_PAYLOAD.length),
                new RequestPlan(
                        HttpRequest.create()
                                .setUri(SmithyUri.of(BenchmarkSupport.H2_URL + "/putmb?runId=" + runId))
                                .setHttpVersion(HttpVersion.HTTP_2)
                                .setMethod("PUT")
                                .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD)),
                        false,
                        BenchmarkSupport.MB_PAYLOAD.length,
                        0));
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try {
            if (benchmarkClient != null) {
                String stats = BenchmarkSupport.getServerStats(benchmarkClient, BenchmarkSupport.H2_URL, runId);
                var actualStats = BenchmarkSupport.parseIoStats(stats);
                var expectedStats = mixedRequests.expectedIoStats();
                BenchmarkSupport.assertIoStats("H2 mixed server IO stats", actualStats, expectedStats);
                mixedRequests.assertClientIoMatches(expectedStats);
                System.out.println("H2 mixed GET+PUT stats [c=" + concurrency + ", conn=" + connections
                        + ", streams=" + streamsPerConnection + "]: " + stats);
                System.out.println("H2 client stats: " + BenchmarkSupport.getH2ConnectionStats(benchmarkClient));
                System.out.println("JDK mixed config: executor=vt"
                        + ", transferScratchSize="
                        + Integer.getInteger("smithy.java.client.http.jdk.transferScratchSize", 16 * 1024)
                        + ", jdk.httpclient.maxframesize=" + System.getProperty("jdk.httpclient.maxframesize")
                        + ", jdk.httpclient.bufsize=" + System.getProperty("jdk.httpclient.bufsize")
                        + ", jdk.httpclient.maxstreams=" + System.getProperty("jdk.httpclient.maxstreams"));
            }
        } finally {
            if (benchmarkClient != null) {
                benchmarkClient.close();
                benchmarkClient = null;
            }
            if (javaClient != null) {
                javaClient.close();
                javaClient = null;
            }
            if (javaExecutor != null) {
                javaExecutor.close();
                javaExecutor = null;
            }
            javaTransport = null;
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
            if (clientRequestBytes.get() != expectedRequestBytes
                    || clientResponseBytes.get() != expectedResponseBytes) {
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
}
