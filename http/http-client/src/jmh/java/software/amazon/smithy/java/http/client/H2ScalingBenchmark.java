/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * HTTP/2 over TLS (h2) benchmark focused on the JDK transport.
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
    })
    private int concurrency;

    @Param({"3"})
    private int connections;

    @Param({"4096"})
    private int streamsPerConnection;

    private HttpClient benchmarkClient;
    private HttpClient javaClient;
    private ExecutorService javaExecutor;
    private JavaHttpClientTransport javaTransport;
    private Context transportContext;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        closeClients();

        System.out.println("H2 setup: concurrency=" + concurrency
                + ", connections=" + connections
                + ", streams=" + streamsPerConnection);

        var sslContext = BenchmarkSupport.trustAllSsl();

        benchmarkClient = HttpClient.newBuilder()
                .version(Version.HTTP_2)
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        javaExecutor = Executors.newVirtualThreadPerTaskExecutor();
        javaClient = HttpClient.newBuilder()
                .version(Version.HTTP_2)
                .sslContext(sslContext)
                .executor(javaExecutor)
                .build();
        javaTransport = new JavaHttpClientTransport(javaClient);
        transportContext = Context.create();

        BenchmarkSupport.resetServer(benchmarkClient, BenchmarkSupport.H2_URL);
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        String stats = BenchmarkSupport.getServerStats(benchmarkClient, BenchmarkSupport.H2_URL);
        System.out.println("H2 stats [c=" + concurrency + ", conn=" + connections
                + ", streams=" + streamsPerConnection + "]: " + stats);
        System.out.println("H2 client stats: " + BenchmarkSupport.getH2ConnectionStats(benchmarkClient));
        closeClients();
    }

    private void closeClients() throws Exception {
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
    public void h2JavaWrapperPutMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/putmb");
        var request = HttpRequest.create()
                .setUri(uri)
                .setHttpVersion(HttpVersion.HTTP_2)
                .setMethod("PUT")
                .setBody(DataStream.ofBytes(BenchmarkSupport.MB_PAYLOAD));

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var response = javaTransport.send(transportContext, req)) {
                response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Java wrapper H2 PUT 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2JavaWrapperGetMb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/getmb");
        var request = HttpRequest.create().setUri(uri).setHttpVersion(HttpVersion.HTTP_2).setMethod("GET");

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var response = javaTransport.send(transportContext, req)) {
                response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Java Wrapper H2 GET 1MB");
    }

    @Benchmark
    @Threads(1)
    public void h2JdkGetMb(Counter counter) throws InterruptedException {
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

    @Benchmark
    @Threads(1)
    public void h2JavaWrapperGet10Mb(Counter counter) throws InterruptedException {
        var uri = SmithyUri.of(BenchmarkSupport.H2_URL + "/get10mb");
        var request = HttpRequest.create().setUri(uri).setHttpVersion(HttpVersion.HTTP_2).setMethod("GET");

        BenchmarkSupport.runBenchmark(concurrency, concurrency, (HttpRequest req) -> {
            try (var response = javaTransport.send(transportContext, req)) {
                response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
            }
        }, request, counter);

        counter.logErrors("Java Wrapper H2 GET 10MB");
    }
}
