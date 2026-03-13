/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.DelayedResponseHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests that requests block when connection pool is exhausted, then succeed.
 */
public class ConnectionPoolExhaustionHttp11Test extends BaseHttpClientIntegTest {

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new DelayedResponseHttp11ClientHandler(
                        new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS),
                        200)); // 200ms delay
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxConnectionsPerRoute(1); // Only 1 connection allowed per route
    }

    @Test
    void blocksWhenPoolExhaustedThenSucceeds() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // First request holds the only connection for 200ms
            var future1 = CompletableFuture.supplyAsync(() -> {
                try {
                    var request = plainTextRequest(HttpVersion.HTTP_1_1, "");
                    var response = client.send(request);
                    return readBody(response);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            // Small delay to ensure first request acquires connection
            Thread.sleep(50);

            // Second request must wait for first to complete
            var future2 = CompletableFuture.supplyAsync(() -> {
                try {
                    var request = plainTextRequest(HttpVersion.HTTP_1_1, "");
                    var response = client.send(request);
                    return readBody(response);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            assertEquals(RESPONSE_CONTENTS, future1.join());
            assertEquals(RESPONSE_CONTENTS, future2.join());
        }
    }
}
