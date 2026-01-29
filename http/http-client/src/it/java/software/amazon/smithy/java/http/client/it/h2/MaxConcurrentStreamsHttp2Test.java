/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.DelayedResponseHttp2ClientHandler;

/**
 * Tests HTTP/2 concurrent streams behavior.
 */
public class MaxConcurrentStreamsHttp2Test extends BaseHttpClientIntegTest {

    private DelayedResponseHttp2ClientHandler handler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        handler = new DelayedResponseHttp2ClientHandler(RESPONSE_CONTENTS, 100);
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> handler);
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE)
                .maxConnectionsPerRoute(1); // Force single connection
    }

    @Test
    void manyConcurrentStreamsOnSingleConnection() throws Exception {
        int numRequests = 50;
        var futures = new ArrayList<CompletableFuture<String>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numRequests; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        var request = plainTextRequest(HttpVersion.HTTP_2, "");
                        var response = client.send(request);
                        return readBody(response);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor));
            }

            for (var future : futures) {
                assertEquals(RESPONSE_CONTENTS, future.join());
            }
        }

        // Verify we actually had concurrent streams (not serialized)
        assertTrue(handler.maxObservedConcurrent() > 1,
                "Should have multiple concurrent streams, observed: " + handler.maxObservedConcurrent());
    }
}
