/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
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
 * Tiny request/response RPC latency benchmark over H2 for the JDK transport.
 *
 * <p>This uses JMH threads directly rather than the internal virtual-thread fanout so SampleTime
 * percentile output reflects per-request latency under real concurrent pressure.
 */
@BenchmarkMode(org.openjdk.jmh.annotations.Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@State(Scope.Benchmark)
public class H2TinyRpcBenchmark {

    @Param({"3"})
    private int connections;

    @Param({"4096"})
    private int streamsPerConnection;

    private HttpClient benchmarkClient;
    private HttpClient javaClient;
    private ExecutorService javaExecutor;
    private JavaHttpClientTransport javaTransport;
    private Context transportContext;
    private HttpRequest smithyRequest;

    @Setup(Level.Trial)
    public void setup() throws Exception {
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

        smithyRequest = HttpRequest.create()
                .setUri(SmithyUri.of(BenchmarkSupport.H2_URL + "/rpc"))
                .setHttpVersion(HttpVersion.HTTP_2)
                .setMethod("POST")
                .setBody(DataStream.ofBytes(BenchmarkSupport.POST_PAYLOAD));
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try {
            if (benchmarkClient != null) {
                String stats = BenchmarkSupport.getServerStats(benchmarkClient, BenchmarkSupport.H2_URL);
                System.out.println("H2 tiny RPC stats [conn=" + connections
                        + ", streams=" + streamsPerConnection + "]: " + stats);
                System.out.println("H2 client stats: " + BenchmarkSupport.getH2ConnectionStats(benchmarkClient));
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

    @Benchmark
    @Threads(64)
    public void h2JavaWrapperTinyRpc() throws Exception {
        try (var response = javaTransport.send(transportContext, smithyRequest)) {
            try (InputStream body = response.body().asInputStream()) {
                body.transferTo(OutputStream.nullOutputStream());
            }
        }
    }
}
