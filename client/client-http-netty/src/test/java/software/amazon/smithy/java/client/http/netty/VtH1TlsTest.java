/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * End-to-end TLS coverage for the VT-blocking transport: drives a real HTTPS handshake over the
 * EmbeddedChannel + SslHandler pump (BoringSSL when available, else JDK) against a local HTTPS
 * server with a self-signed certificate. Exercises handshake, request/response, body upload, and
 * keep-alive reuse — the code with no other coverage.
 */
class VtH1TlsTest {

    private HttpsServer server;

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
        ks.setKeyEntry(
                "key",
                ssc.key(),
                new char[0],
                new Certificate[] {ssc.cert()});
        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/echo", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            byte[] resp =
                    (exchange.getRequestMethod() + ":" + new String(body, StandardCharsets.UTF_8))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "text/plain");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
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
    void httpsRequestOverTlsAndReuse() throws Exception {
        var requestCount = new AtomicInteger();
        startTlsEchoServer(requestCount);

        // One connection forces every request after the first to reuse the same TLS session.
        var config = new NettyHttpTransportConfig().maxConnectionsPerHost(1);
        var transport = new NettyHttpClientTransport(config);
        try {
            String uri = "https://127.0.0.1:" + server.getAddress().getPort() + "/echo";
            for (int i = 0; i < 10; i++) {
                HttpResponse response = transport.send(Context.create(), put(uri, "tls-" + i));
                assertThat(response.statusCode(), equalTo(200));
                try (var b = response.body().asInputStream()) {
                    assertThat(
                            new String(b.readAllBytes(), StandardCharsets.UTF_8),
                            equalTo("PUT:tls-" + i));
                }
            }
            assertEquals(10, requestCount.get());
        } finally {
            transport.close();
        }
    }

    @Test
    void httpsUnderConcurrency() throws Exception {
        var requestCount = new AtomicInteger();
        startTlsEchoServer(requestCount);

        var config = new NettyHttpTransportConfig().maxConnectionsPerHost(4);
        var transport = new NettyHttpClientTransport(config);
        try {
            String uri = "https://127.0.0.1:" + server.getAddress().getPort() + "/echo";
            int tasks = 100;
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<String>> futures = new ArrayList<>(tasks);
                for (int i = 0; i < tasks; i++) {
                    final int idx = i;
                    futures.add(pool.submit(() -> {
                        HttpResponse response = transport.send(Context.create(), put(uri, "c-" + idx));
                        try (var b = response.body().asInputStream()) {
                            return new String(b.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    }));
                }
                for (int i = 0; i < tasks; i++) {
                    assertEquals("PUT:c-" + i, futures.get(i).get());
                }
            }
            assertEquals(tasks, requestCount.get());
        } finally {
            transport.close();
        }
    }
}
