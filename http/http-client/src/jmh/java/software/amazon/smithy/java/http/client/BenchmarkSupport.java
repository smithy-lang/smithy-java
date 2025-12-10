/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

/**
 * Shared utilities for HTTP client benchmarks.
 * This class is not a benchmark - JMH only measures @Benchmark methods.
 */
public final class BenchmarkSupport {

    public static final String H1_URL = "http://localhost:18080";
    public static final String H2C_URL = "http://localhost:18081";
    public static final String H2_URL = "https://localhost:18443";

    // Small JSON payload for POST benchmarks
    public static final byte[] POST_PAYLOAD = "{\"id\":12345,\"name\":\"benchmark\"}".getBytes(StandardCharsets.UTF_8);

    // 1MB payload for large transfer benchmarks
    public static final byte[] MB_PAYLOAD = new byte[1024 * 1024];

    private BenchmarkSupport() {}

    /**
     * Create a DNS resolver that maps localhost to loopback, avoiding DNS overhead.
     */
    public static DnsResolver staticDns() {
        return DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));
    }

    /**
     * Create an SSL context that trusts all certificates (for benchmarking only).
     */
    public static SSLContext trustAllSsl() throws Exception {
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

    /**
     * Reset server state and trigger GC.
     */
    public static void resetServer(HttpClient client, String baseUrl) throws Exception {
        try (var res = client.send(HttpRequest.builder()
                .uri(URI.create(baseUrl + "/reset"))
                .method("POST")
                .build())) {
            res.body().asInputStream().transferTo(OutputStream.nullOutputStream());
        }
        Thread.sleep(100);
    }

    /**
     * Get server stats as JSON string.
     */
    public static String getServerStats(HttpClient client, String baseUrl) throws Exception {
        try (var res = client.send(HttpRequest.builder()
                .uri(URI.create(baseUrl + "/stats"))
                .method("GET")
                .build())) {
            return new String(res.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Run a benchmark loop with virtual threads.
     *
     * @param concurrency number of virtual threads
     * @param durationMs how long to run
     * @param task the task each thread runs in a loop
     * @param context context passed to task (avoids lambda allocation)
     * @param counter output counter for requests/errors
     */
    public static <T> void runBenchmark(
            int concurrency,
            long durationMs,
            BenchmarkTask<T> task,
            T context,
            RequestCounter counter
    ) throws InterruptedException {
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
                            task.run(context);
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

            Thread.sleep(durationMs);
            running.set(false);
            // Don't wait long - VTs may be blocked on in-flight requests
            latch.await(100, TimeUnit.MILLISECONDS);
        }

        counter.requests = requests.get();
        counter.errors = errors.get();
        counter.firstError = firstError.get();
    }

    @FunctionalInterface
    public interface BenchmarkTask<T> {
        void run(T context) throws Exception;
    }

    /**
     * Simple counter for benchmark results. Used with @AuxCounters.
     */
    public static class RequestCounter {
        public long requests;
        public long errors;
        public Throwable firstError;

        public void reset() {
            requests = 0;
            errors = 0;
            firstError = null;
        }

        public void logErrors(String label) {
            if (firstError != null) {
                System.err.println(label + " errors: " + errors + ", first:");
                firstError.printStackTrace(System.err);
            }
        }
    }
}
