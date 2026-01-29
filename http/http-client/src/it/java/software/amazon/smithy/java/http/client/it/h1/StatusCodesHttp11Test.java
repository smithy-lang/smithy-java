/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.StatusCodeHttp11ClientHandler;

/**
 * Tests various HTTP status codes with empty bodies.
 */
public class StatusCodesHttp11Test {

    private HttpClient client;
    private NettyTestServer server;

    @BeforeEach
    void setUp() {
        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));

        client = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .maxConnectionsPerRoute(10)
                        .maxTotalConnections(10)
                        .maxIdleTime(Duration.ofMinutes(1))
                        .dnsResolver(staticDns)
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void handles200WithEmptyBody() throws Exception {
        startServer(200);
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://localhost:" + server.getPort(), "");
        var response = client.send(request);

        assertEquals(200, response.statusCode());
        assertEquals("", readBody(response));
    }

    @Test
    void handles204NoContent() throws Exception {
        startServer(204);
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://localhost:" + server.getPort(), "");
        var response = client.send(request);

        assertEquals(204, response.statusCode());
        assertEquals("", readBody(response));
    }

    @Test
    void handles304NotModified() throws Exception {
        startServer(304);
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://localhost:" + server.getPort(), "");
        var response = client.send(request);

        assertEquals(304, response.statusCode());
        assertEquals("", readBody(response));
    }

    @Test
    void handles404NotFound() throws Exception {
        startServer(404);
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://localhost:" + server.getPort(), "");
        var response = client.send(request);

        assertEquals(404, response.statusCode());
    }

    @Test
    void handles500InternalServerError() throws Exception {
        startServer(500);
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://localhost:" + server.getPort(), "");
        var response = client.send(request);

        assertEquals(500, response.statusCode());
    }

    private void startServer(int statusCode) throws Exception {
        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new StatusCodeHttp11ClientHandler(statusCode))
                .build();
        server.start();
    }

    private String readBody(software.amazon.smithy.java.http.api.HttpResponse response) {
        var buf = response.body().asByteBuffer();
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
