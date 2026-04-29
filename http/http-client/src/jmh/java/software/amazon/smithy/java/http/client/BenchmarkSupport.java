/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Shared utilities for JDK transport benchmarks.
 */
public final class BenchmarkSupport {

    static {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
    }

    public static final String H1_URL = "http://localhost:18080";
    public static final String H2C_URL = "http://localhost:18081";
    public static final String H2_URL = "https://localhost:18443";

    public static final byte[] POST_PAYLOAD = "{\"id\":12345,\"name\":\"benchmark\"}".getBytes(StandardCharsets.UTF_8);
    public static final byte[] MB_PAYLOAD = new byte[1024 * 1024];

    private BenchmarkSupport() {}

    public record IoStats(long getMbRequests, long getMbBytesSent, long putMbRequests, long putMbBytesReceived) {}

    public static SSLContext trustAllSsl() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        return sslContext;
    }

    public static void resetServer(HttpClient client, String baseUrl) throws Exception {
        sendAndDrain(client, baseUrl + "/reset", "POST");
        sendAndDrain(client, baseUrl + "/reset-io-stats", "POST");
        Thread.sleep(100);
    }

    public static String getServerStats(HttpClient client, String baseUrl) throws Exception {
        return getServerStats(client, baseUrl, null);
    }

    public static String getServerStats(HttpClient client, String baseUrl, String runId)
            throws Exception {
        String url = runId == null ? baseUrl + "/stats" : baseUrl + "/stats?runId=" + runId;
        var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        var response = client.send(request, BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String createRunId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    public static IoStats parseIoStats(String json) {
        return new IoStats(
                parseLongField(json, "getMbRequests"),
                parseLongField(json, "getMbBytesSent"),
                parseLongField(json, "putMbRequests"),
                parseLongField(json, "putMbBytesReceived"));
    }

    public static void assertIoStats(String label, IoStats actual, IoStats expected) {
        if (!actual.equals(expected)) {
            throw new IllegalStateException(label + " mismatch. expected=" + expected + ", actual=" + actual);
        }
    }

    public static <T> void runBenchmark(
            int concurrency,
            int totalRequests,
            BenchmarkTask<T> task,
            T context,
            RequestCounter counter
    ) throws InterruptedException {
        var completed = new AtomicInteger(0);
        var errors = new AtomicLong();
        var firstError = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(concurrency);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        while (completed.getAndIncrement() < totalRequests) {
                            task.run(context);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, e);
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                        firstError.compareAndSet(null, new RuntimeException("Thread " + threadId + " error", t));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable err = firstError.get();
                System.err.println("BENCHMARK TIMEOUT: " + (concurrency - (int) latch.getCount())
                        + "/" + concurrency + " threads completed, errors=" + errors.get()
                        + (err != null ? ", firstError=" + err : ""));
                if (err != null) {
                    err.printStackTrace(System.err);
                }
            }
        }

        counter.requests = completed.get();
        counter.errors = errors.get();
        counter.firstError = firstError.get();
    }

    @FunctionalInterface
    public interface BenchmarkTask<T> {
        void run(T context) throws Exception;
    }

    public static class RequestCounter {
        public long requests;
        public long errors;
        public Throwable firstError;

        public long requests() {
            return requests;
        }

        public long errors() {
            return errors;
        }

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

        public void throwIfErrored(String label) {
            if (firstError == null) {
                return;
            }
            throw new IllegalStateException(label + " failed with " + errors + " error(s)", firstError);
        }
    }

    public static String getH2ConnectionStats(HttpClient client) {
        return "(stats unavailable for java.net.http.HttpClient)";
    }

    private static void sendAndDrain(HttpClient client, String url, String method) throws Exception {
        var builder = HttpRequest.newBuilder().uri(URI.create(url));
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }
        var response = client.send(builder.build(), BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            body.transferTo(OutputStream.nullOutputStream());
        }
    }

    private static long parseLongField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            throw new IllegalArgumentException("Missing field `" + fieldName + "` in stats: " + json);
        }
        start += needle.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c < '0' || c > '9') && c != '-') {
                break;
            }
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
