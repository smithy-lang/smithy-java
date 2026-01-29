/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
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
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * HTTP/1.1 client scaling benchmark.
 *
 * <p>For H1, the key parameters are:
 * <ul>
 *   <li>concurrency - number of virtual threads making requests</li>
 *   <li>maxConnections - connection pool size (caps actual parallelism)</li>
 * </ul>
 *
 * <p>Run with: ./gradlew :http:http-client:jmh -Pjmh.includes="H1ScalingBenchmark"
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@State(Scope.Benchmark)
public class H1ScalingBenchmark {

    @Param({"100", "500", "1000", "2000"})
    private int concurrency;

    @Param({"50", "100", "200", "500"})
    private int maxConnections;

    private HttpClient smithyClient;
    private CloseableHttpClient apacheClient;
    private WebClient helidonClient;

    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        closeClients();

        System.out.println("H1 setup: concurrency=" + concurrency + ", maxConnections=" + maxConnections);

        // Smithy client
        smithyClient = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(maxConnections)
                        .maxTotalConnections(maxConnections)
                        .maxIdleTime(Duration.ofMinutes(2))
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .dnsResolver(BenchmarkSupport.staticDns())
                        .build())
                .build();

        // Apache client
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(maxConnections);
        connManager.setDefaultMaxPerRoute(maxConnections);
        connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setSocketTimeout(Timeout.ofSeconds(30))
                .build());

        apacheClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();

        // Helidon client
        helidonClient = WebClient.builder()
                .baseUri(BenchmarkSupport.H1_URL)
                .shareConnectionCache(false)
                .connectionCacheSize(maxConnections)
                .build();

        BenchmarkSupport.resetServer(smithyClient, BenchmarkSupport.H1_URL);
    }

    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        String stats = BenchmarkSupport.getServerStats(smithyClient, BenchmarkSupport.H1_URL);
        System.out.println("H1 stats [c=" + concurrency + ", conn=" + maxConnections + "]: " + stats);
    }

    @TearDown
    public void teardown() throws Exception {
        closeClients();
    }

    private void closeClients() throws Exception {
        if (smithyClient != null) {
            smithyClient.close();
            smithyClient = null;
        }
        if (apacheClient != null) {
            apacheClient.close();
            apacheClient = null;
        }
        if (helidonClient != null) {
            helidonClient.closeResource();
            helidonClient = null;
        }
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class Counter extends BenchmarkSupport.RequestCounter {
        @Setup(Level.Iteration)
        public void reset() {
            super.reset();
        }
    }

    @Benchmark
    @Threads(1)
    public void smithy(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H1_URL + "/get");
        var request = HttpRequest.builder().uri(uri).method("GET").build();

        BenchmarkSupport.runBenchmark(concurrency, 1000, (HttpRequest req) -> {
            smithyClient.send(req).close();
        }, request, counter);

        counter.logErrors("Smithy H1");
    }

    @Benchmark
    @Threads(1)
    public void apache(Counter counter) throws InterruptedException {
        var target = BenchmarkSupport.H1_URL + "/get";

        BenchmarkSupport.runBenchmark(concurrency, 1000, (String url) -> {
            try (var response = apacheClient.execute(new HttpGet(url))) {
                EntityUtils.consume(response.getEntity());
            }
        }, target, counter);

        counter.logErrors("Apache H1");
    }

    @Benchmark
    @Threads(1)
    public void helidon(Counter counter) throws InterruptedException {
        BenchmarkSupport.runBenchmark(concurrency, 1000, (WebClient client) -> {
            try (HttpClientResponse response = client.get("/get").request()) {
                response.entity().consume();
            }
        }, helidonClient, counter);

        counter.logErrors("Helidon H1");
    }

    @Benchmark
    @Threads(1)
    public void smithyPost(Counter counter) throws InterruptedException {
        var uri = URI.create(BenchmarkSupport.H1_URL + "/post");
        var request = HttpRequest.builder()
                .uri(uri)
                .method("POST")
                .body(DataStream.ofBytes(BenchmarkSupport.POST_PAYLOAD))
                .build();

        BenchmarkSupport.runBenchmark(concurrency, 1000, (HttpRequest req) -> {
            smithyClient.send(req).close();
        }, request, counter);

        counter.logErrors("Smithy H1 POST");
    }

    @Benchmark
    @Threads(1)
    public void apachePost(Counter counter) throws InterruptedException {
        var target = BenchmarkSupport.H1_URL + "/post";

        BenchmarkSupport.runBenchmark(concurrency, 1000, (String url) -> {
            var post = new HttpPost(url);
            post.setEntity(new ByteArrayEntity(BenchmarkSupport.POST_PAYLOAD, ContentType.APPLICATION_OCTET_STREAM));
            try (var response = apacheClient.execute(post)) {
                EntityUtils.consume(response.getEntity());
            }
        }, target, counter);

        counter.logErrors("Apache H1 POST");
    }
}
