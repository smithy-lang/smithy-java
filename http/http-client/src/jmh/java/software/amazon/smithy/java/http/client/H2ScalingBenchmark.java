/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
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

    @Param({"1000"})
    private int concurrency;

    @Param({"3"})
    private int connections;

    @Param({"4096"})
    private int streamsPerConnection;

    private HttpClient smithyClient;
    private java.net.http.HttpClient javaClient;

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
                        .h2InitialWindowSize(1024 * 1024)
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
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        String stats = BenchmarkSupport.getServerStats(smithyClient, BenchmarkSupport.H2_URL);
        System.out.println("H2 stats [c=" + concurrency + ", conn=" + connections
                + ", streams=" + streamsPerConnection + "]: " + stats);
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
    public void smithy(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2_URL + "/get");
        var request = HttpRequest.builder().uri(uri).method("GET").build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2");
    }

    @Benchmark
    @Threads(1)
    public void javaHttpClient(Counter counter) throws InterruptedException {
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
    public void smithyPost(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2_URL + "/post");
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

        counter.logErrors("Smithy H2 POST");
    }

    @Benchmark
    @Threads(1)
    public void javaHttpClientPost(Counter counter) throws InterruptedException {
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
    public void smithyPutMb(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2_URL + "/putmb");
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

        counter.logErrors("Smithy H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void javaHttpClientPutMb(Counter counter) throws InterruptedException {
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
    public void smithyGetMb(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H2_URL + "/getmb");
        var request = HttpRequest.builder().uri(uri).method("GET").build();

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var res = smithyClient.send(req)) {
                res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Smithy H2 GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void javaHttpClientGetMb(Counter counter) throws InterruptedException {
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(BenchmarkSupport.H2_URL + "/getmb"))
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
}
