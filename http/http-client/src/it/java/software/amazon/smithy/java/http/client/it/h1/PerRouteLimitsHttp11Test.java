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
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.ConnectionTrackingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests that per-route connection limits are independent.
 */
public class PerRouteLimitsHttp11Test {

    private NettyTestServer server1;
    private NettyTestServer server2;
    private HttpClient client;
    private ConnectionTrackingHttp11ClientHandler handler1;
    private ConnectionTrackingHttp11ClientHandler handler2;

    @BeforeEach
    void setUp() throws Exception {
        handler1 = new ConnectionTrackingHttp11ClientHandler(
                new TextResponseHttp11ClientHandler("response1"));
        handler2 = new ConnectionTrackingHttp11ClientHandler(
                new TextResponseHttp11ClientHandler("response2"));

        server1 = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> handler1)
                .build();
        server1.start();

        server2 = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> handler2)
                .build();
        server2.start();

        // Map different hostnames to loopback
        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "host1.test",
                List.of(InetAddress.getLoopbackAddress()),
                "host2.test",
                List.of(InetAddress.getLoopbackAddress())));

        client = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .maxConnectionsPerRoute(1) // Only 1 connection per route
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
        if (server1 != null) {
            server1.stop();
        }
        if (server2 != null) {
            server2.stop();
        }
    }

    @Test
    void differentRoutesHaveIndependentLimits() throws Exception {
        // Send request to host1
        var request1 = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://host1.test:" + server1.getPort(), "");
        var response1 = client.send(request1);
        assertEquals("response1", readBody(response1));

        // Send request to host2 - should work even though host1 has a connection
        var request2 = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://host2.test:" + server2.getPort(), "");
        var response2 = client.send(request2);
        assertEquals("response2", readBody(response2));

        // Both routes should have 1 connection each
        assertEquals(1, handler1.connectionCount());
        assertEquals(1, handler2.connectionCount());
    }

    private String readBody(HttpResponse response) {
        var buf = response.body().asByteBuffer();
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
