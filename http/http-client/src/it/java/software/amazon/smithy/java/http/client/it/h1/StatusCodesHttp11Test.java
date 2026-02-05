/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.StatusCodeHttp11ClientHandler;

/**
 * Tests various HTTP status codes.
 */
public class StatusCodesHttp11Test {

    private HttpClient client;
    private NettyTestServer server;

    @BeforeEach
    void setUp() {
        client = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .maxConnectionsPerRoute(10)
                        .maxTotalConnections(10)
                        .maxIdleTime(Duration.ofMinutes(1))
                        .dnsResolver(DnsResolver.staticMapping(Map.of(
                                "localhost",
                                List.of(InetAddress.getLoopbackAddress()))))
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

    @ParameterizedTest(name = "status {0}")
    @ValueSource(ints = {200, 204, 304, 404, 500})
    void handlesStatusCode(int statusCode) throws Exception {
        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new StatusCodeHttp11ClientHandler(statusCode))
                .build();
        server.start();

        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://localhost:" + server.getPort(), "");
        var response = client.send(request);

        assertEquals(statusCode, response.statusCode());
    }
}
