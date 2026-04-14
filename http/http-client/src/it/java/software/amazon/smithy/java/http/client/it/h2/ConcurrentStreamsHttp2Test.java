/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.ConnectionTrackingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.TextResponseHttp2ClientHandler;

/**
 * Tests that multiple HTTP/2 streams are multiplexed on a single connection.
 */
public class ConcurrentStreamsHttp2Test extends BaseHttpClientIntegTest {

    private ConnectionTrackingHttp2ClientHandler trackingHandler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        trackingHandler = new ConnectionTrackingHttp2ClientHandler(
                new TextResponseHttp2ClientHandler(RESPONSE_CONTENTS));
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> trackingHandler);
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE)
                .maxConnectionsPerRoute(1); // Force all streams onto single connection
    }

    @Test
    void multipleConcurrentStreamsOnSameConnection() throws Exception {
        int numRequests = 10;
        var futures = new ArrayList<CompletableFuture<String>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numRequests; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        var request = plainTextRequest(HttpVersion.HTTP_2, REQUEST_CONTENTS);
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

        assertEquals(numRequests, trackingHandler.requestCount(), "Should have received all requests");
        assertEquals(1, trackingHandler.connectionCount(), "All streams should use single connection");
    }
}
