/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.ConnectionTrackingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests that HTTP/1.1 connections are reused across multiple requests.
 */
public class ConnectionPoolReuseHttp11Test extends BaseHttpClientIntegTest {

    private static final int NUM_REQUESTS = 5;

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
                .maxConnectionsPerRoute(1);
    }

    @Test
    void connectionIsReusedForMultipleRequests() throws Exception {
        for (int i = 0; i < NUM_REQUESTS; i++) {
            var request = plainTextRequest(HttpVersion.HTTP_1_1, REQUEST_CONTENTS);
            var response = client.send(request);
            assertEquals(RESPONSE_CONTENTS, readBody(response));
        }

        assertEquals(NUM_REQUESTS, trackingHandler.requestCount(), "Should have received all requests");
        assertEquals(1, trackingHandler.connectionCount(), "Should reuse single connection");
    }
}
