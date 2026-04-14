/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;

/**
 * Base class for HTTP client integration tests.
 * Provides common setup/teardown and utility methods.
 */
public abstract class BaseHttpClientIntegTest {

    protected static final String RESPONSE_CONTENTS = "Test response body";
    protected static final String REQUEST_CONTENTS = "Test request body";

    protected NettyTestServer server;
    protected HttpClient client;

    /**
     * Configure the test server.
     */
    protected abstract NettyTestServer.Builder configureServer(NettyTestServer.Builder builder);

    /**
     * Configure the connection pool.
     */
    protected abstract HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder);

    @BeforeEach
    void setUp() throws Exception {
        server = configureServer(NettyTestServer.builder()).build();
        server.start();

        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));

        var poolBuilder = HttpConnectionPool.builder()
                .maxConnectionsPerRoute(10)
                .maxTotalConnections(10)
                .maxIdleTime(Duration.ofMinutes(1))
                .dnsResolver(staticDns);

        poolBuilder = configurePool(poolBuilder);

        client = HttpClient.builder()
                .connectionPool(poolBuilder.build())
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

    protected String uri(String path) {
        return "http://localhost:" + server.getPort() + path;
    }

    protected String uri() {
        return uri("");
    }

    protected HttpRequest plainTextRequest(HttpVersion version, String body) {
        return TestUtils.plainTextRequest(version, uri(), body);
    }

    protected String readBody(HttpResponse response) throws IOException {
        try (var body = response.body().asInputStream()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
