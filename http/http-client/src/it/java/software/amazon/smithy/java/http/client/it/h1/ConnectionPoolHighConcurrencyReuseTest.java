/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.ConnectionTrackingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests that HTTP/1.1 connections are properly reused under high concurrency
 * when concurrency exceeds maxConnections.
 *
 * <p>This tests the fix for a race condition where threads waiting on acquirePermit()
 * would create new connections instead of reusing ones released while waiting.
 */
public class ConnectionPoolHighConcurrencyReuseTest extends BaseHttpClientIntegTest {

    private static final int MAX_CONNECTIONS = 5;
    private static final int CONCURRENCY = 50;
    private static final int REQUESTS_PER_THREAD = 10;

    private ConnectionTrackingHttp11ClientHandler trackingHandler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        trackingHandler = new ConnectionTrackingHttp11ClientHandler(
                new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS));
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> trackingHandler);
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxConnectionsPerRoute(MAX_CONNECTIONS)
                .maxTotalConnections(MAX_CONNECTIONS);
    }

    @Test
    void connectionsAreReusedUnderHighConcurrency() throws Exception {
        int totalRequests = CONCURRENCY * REQUESTS_PER_THREAD;
        var successCount = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new CompletableFuture[CONCURRENCY];

            for (int i = 0; i < CONCURRENCY; i++) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        try {
                            var request = plainTextRequest(HttpVersion.HTTP_1_1, "");
                            var response = client.send(request);
                            if (RESPONSE_CONTENTS.equals(readBody(response))) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, executor);
            }

            CompletableFuture.allOf(futures).join();
        }

        assertEquals(totalRequests, successCount.get(), "All requests should succeed");
        assertEquals(totalRequests, trackingHandler.requestCount(), "Server should receive all requests");

        // Connections should be reused, not created for every request.
        // We should have at most maxConnections connections.
        int connectionCount = trackingHandler.connectionCount();
        assertTrue(connectionCount <= MAX_CONNECTIONS,
                "Should create at most " + MAX_CONNECTIONS + " connections, but created " + connectionCount);
    }
}
