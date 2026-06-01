/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.boringssl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * End-to-end coverage for the BoringSSL SSLEngine provider driven through the smithy native
 * {@code SSLEngineTransport} (no Netty pipeline). Runs a real HTTPS handshake + request/response +
 * body upload + keep-alive reuse against a local {@link HttpsServer} with a self-signed cert.
 *
 * <p>Skipped (not failed) when netty-tcnative is unavailable on the host, so the build stays green
 * on platforms without the native library.
 */
class BoringSslEngineFactoryTest {

    private HttpsServer server;

    @BeforeEach
    void requireTcnative() {
        assumeTrue(BoringSslEngineFactory.isAvailable(),
                "netty-tcnative (BoringSSL) not available on this host");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void startTlsEchoServer(AtomicInteger requestCount) throws Exception {
        var ssc = new SelfSignedCertificate();
        var ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("key", ssc.key(), new char[0], new Certificate[] {ssc.cert()});
        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/echo", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            byte[] resp = (exchange.getRequestMethod() + ":" + new String(body, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "text/plain");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/raw", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("content-type", "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        // Promises a 100-byte body but sends only headers + nothing, then stalls — the client's body
        // read blocks until the watchdog fires (exercises readWithTimeout's deadline path).
        server.createContext("/stall", exchange -> {
            requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 100);
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
    }

    private HttpClient boringSslClient(int maxConns) {
        return boringSslClient(maxConns, null);
    }

    private HttpClient boringSslClient(int maxConns, Duration readTimeout) {
        var poolBuilder = HttpConnectionPool.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(maxConns)
                .maxConnectionsPerRoute(maxConns)
                .sslEngineFactory(BoringSslEngineFactory.create(true)); // trustAll: self-signed test cert
        if (readTimeout != null) {
            poolBuilder.readTimeout(readTimeout);
        }
        return HttpClient.builder().connectionPool(poolBuilder.build()).build();
    }

    private static HttpRequest put(String uri, String body) {
        return HttpRequest.create()
                .setMethod("PUT")
                .setUri(URI.create(uri))
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setBody(DataStream.ofString(body, "text/plain"))
                .toUnmodifiable();
    }

    @Test
    void readTimeoutFiresViaWatchdog() throws Exception {
        // The server sends response headers then stalls without sending the promised body. The
        // blocking-channel body read must be aborted by the shared HashedWheelTimer watchdog (not
        // hang), proving readWithTimeout's deadline path works without a per-read Selector.
        var requestCount = new AtomicInteger();
        startTlsEchoServer(requestCount);

        try (var client = boringSslClient(1, Duration.ofMillis(500))) {
            String uri = "https://127.0.0.1:" + server.getAddress().getPort() + "/stall";
            long start = System.nanoTime();
            var ex = Assertions.assertThrows(java.io.IOException.class, () -> {
                HttpResponse response = client.send(put(uri, "x"));
                // Force the body read where the stall happens.
                try (var b = response.body().asInputStream()) {
                    b.readAllBytes();
                }
            });
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            // Fired promptly (well under the server's 60s stall), not hung.
            Assertions.assertTrue(elapsedMs < 10_000,
                    "expected timeout to fire promptly, took " + elapsedMs + "ms");
            // The cause chain should mention a read timeout somewhere.
            String msg = String.valueOf(ex);
            for (Throwable t = ex; t != null; t = t.getCause()) {
                msg += " | " + t;
            }
            Assertions.assertTrue(
                    msg.toLowerCase().contains("time"),
                    "expected a timeout-related exception, got: " + msg);
        }
    }

    @Test
    void httpsRequestAndKeepAliveReuse() throws Exception {
        var requestCount = new AtomicInteger();
        startTlsEchoServer(requestCount);

        // One connection forces every request after the first to reuse the same TLS session.
        try (var client = boringSslClient(1)) {
            String uri = "https://127.0.0.1:" + server.getAddress().getPort() + "/echo";
            for (int i = 0; i < 10; i++) {
                HttpResponse response = client.send(put(uri, "tls-" + i));
                assertThat(response.statusCode(), equalTo(200));
                try (var b = response.body().asInputStream()) {
                    assertThat(new String(b.readAllBytes(), StandardCharsets.UTF_8), equalTo("PUT:tls-" + i));
                }
            }
            assertEquals(10, requestCount.get());
        }
    }

    @Test
    void httpsLargeBodyRoundTrip() throws Exception {
        var requestCount = new AtomicInteger();
        startTlsEchoServer(requestCount);

        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31 + 7);
        }

        try (var client = boringSslClient(2)) {
            String uri = "https://127.0.0.1:" + server.getAddress().getPort() + "/raw";
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpRequest request = HttpRequest.create()
                        .setMethod("PUT")
                        .setUri(URI.create(uri))
                        .setHttpVersion(HttpVersion.HTTP_1_1)
                        .setBody(DataStream.ofBytes(payload))
                        .toUnmodifiable();
                HttpResponse response = client.send(request);
                assertThat(response.statusCode(), equalTo(200));
                try (var b = response.body().asInputStream()) {
                    assertThat(Arrays.equals(b.readAllBytes(), payload), equalTo(true));
                }
            }
            assertEquals(3, requestCount.get());
        }
    }

    @Test
    void httpsLargeBodyRoundTripWithLargeReadBuffer() throws Exception {
        // Same 256 KiB round-trip, but with a 256 KiB tlsReadBufferSize so a single socketChannel.read
        // pulls many TLS records at once and SSLEngineTransport.readAndUnwrap drains them all in one
        // pass (compacting netIn once, not per record). This is the multi-record batch-drain path the
        // default 16 KiB buffer never exercises; assert byte-exactness across reuse to prove the
        // drain-then-compact loop frames every record correctly and leaves no plaintext behind.
        var requestCount = new AtomicInteger();
        startTlsEchoServer(requestCount);

        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 17 + 3);
        }

        var poolBuilder = HttpConnectionPool.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(1)
                .maxConnectionsPerRoute(1)
                .tlsReadBufferSize(256 * 1024)
                .socketReceiveBufferSize(512 * 1024)
                .sslEngineFactory(BoringSslEngineFactory.create(true));
        try (var client = HttpClient.builder().connectionPool(poolBuilder.build()).build()) {
            String uri = "https://127.0.0.1:" + server.getAddress().getPort() + "/raw";
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpRequest request = HttpRequest.create()
                        .setMethod("PUT")
                        .setUri(URI.create(uri))
                        .setHttpVersion(HttpVersion.HTTP_1_1)
                        .setBody(DataStream.ofBytes(payload))
                        .toUnmodifiable();
                HttpResponse response = client.send(request);
                assertThat(response.statusCode(), equalTo(200));
                try (var b = response.body().asInputStream()) {
                    assertThat(Arrays.equals(b.readAllBytes(), payload), equalTo(true));
                }
            }
            // One connection reused across all three — each response fully drained and released.
            assertEquals(3, requestCount.get());
        }
    }

    @Test
    void httpsLargeBodyRoundTripWithLargeWriteBuffer() throws Exception {
        // Drive the coalescing write path: a 256 KiB body wrapped into ~16 TLS records that
        // accumulate in one 256 KiB netOut before a single writeNetOut, instead of one socket write
        // per record. The echo server reflects the body, so a byte-exact round-trip proves write()
        // framed every coalesced record correctly (no dropped/duplicated bytes at flush boundaries).
        var requestCount = new AtomicInteger();
        startTlsEchoServer(requestCount);

        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 13 + 5);
        }

        var poolBuilder = HttpConnectionPool.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(1)
                .maxConnectionsPerRoute(1)
                .tlsWriteBufferSize(256 * 1024)
                .socketSendBufferSize(512 * 1024)
                .sslEngineFactory(BoringSslEngineFactory.create(true));
        try (var client = HttpClient.builder().connectionPool(poolBuilder.build()).build()) {
            String uri = "https://127.0.0.1:" + server.getAddress().getPort() + "/raw";
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpRequest request = HttpRequest.create()
                        .setMethod("PUT")
                        .setUri(URI.create(uri))
                        .setHttpVersion(HttpVersion.HTTP_1_1)
                        .setBody(DataStream.ofBytes(payload))
                        .toUnmodifiable();
                HttpResponse response = client.send(request);
                assertThat(response.statusCode(), equalTo(200));
                try (var b = response.body().asInputStream()) {
                    assertThat(Arrays.equals(b.readAllBytes(), payload), equalTo(true));
                }
            }
            assertEquals(3, requestCount.get());
        }
    }
}
