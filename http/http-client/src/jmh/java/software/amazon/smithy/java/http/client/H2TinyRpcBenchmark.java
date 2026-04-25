/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
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
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Tiny request/response RPC latency benchmark over H2.
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

    private HttpClient smithyClient;
    private software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport productionNettyTransport;
    private software.amazon.smithy.java.context.Context transportContext;
    private HttpRequest smithyRequest;

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

        var nettyTransportConfig = new software.amazon.smithy.java.client.http.netty.NettyHttpTransportConfig()
                .maxConnectionsPerHost(connections)
                .h2StreamsPerConnection(streamsPerConnection)
                .httpVersionPolicy(software.amazon.smithy.java.client.http.netty.HttpVersionPolicy.ENFORCE_HTTP_2);
        productionNettyTransport =
                new software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport(nettyTransportConfig);
        transportContext = software.amazon.smithy.java.context.Context.create();

        BenchmarkSupport.resetServer(smithyClient, BenchmarkSupport.H2_URL);

        smithyRequest = HttpRequest.create()
                .setUri(SmithyUri.of(BenchmarkSupport.H2_URL + "/rpc"))
                .setMethod("POST")
                .setBody(DataStream.ofBytes(BenchmarkSupport.POST_PAYLOAD));
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try {
            if (smithyClient != null) {
                String stats = BenchmarkSupport.getServerStats(smithyClient, BenchmarkSupport.H2_URL);
                System.out.println("H2 tiny RPC stats [conn=" + connections + ", streams=" + streamsPerConnection
                        + "]: " + stats);
                System.out.println("H2 client stats: " + BenchmarkSupport.getH2ConnectionStats(smithyClient));
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

    @Benchmark
    @Threads(64)
    public void h2SmithyTinyRpc() throws Exception {
        try (var response = smithyClient.send(smithyRequest)) {
            response.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }
    }

    @Benchmark
    @Threads(64)
    public void h2ProductionNettyTinyRpc() throws Exception {
        try (var response = productionNettyTransport.send(transportContext, smithyRequest)) {
            try (InputStream body = response.body().asInputStream()) {
                body.transferTo(OutputStream.nullOutputStream());
            }
        }
    }
}
