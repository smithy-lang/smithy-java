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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
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
        server.start();
    }

    private HttpClient boringSslClient(int maxConns) {
        var pool = HttpConnectionPool.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(maxConns)
                .maxConnectionsPerRoute(maxConns)
                .sslEngineFactory(BoringSslEngineFactory.create(true)) // trustAll: self-signed test cert
                .build();
        return HttpClient.builder().connectionPool(pool).build();
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
}
