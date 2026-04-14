/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.ConnectionTrackingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests that idle connections are cleaned up after timeout.
 */
public class IdleConnectionCleanupHttp11Test extends BaseHttpClientIntegTest {

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
                .maxIdleTime(Duration.ofMillis(100)); // Very short idle timeout
    }

    @Test
    void idleConnectionsAreCleanedUp() throws Exception {
        // First request creates connection
        var request1 = plainTextRequest(HttpVersion.HTTP_1_1, "");
        var response1 = client.send(request1);
        assertEquals(RESPONSE_CONTENTS, readBody(response1));
        assertEquals(1, trackingHandler.connectionCount());

        // Wait for idle timeout + cleanup interval
        Thread.sleep(300);

        // Second request should create new connection (old one was cleaned up)
        var request2 = plainTextRequest(HttpVersion.HTTP_1_1, "");
        var response2 = client.send(request2);
        assertEquals(RESPONSE_CONTENTS, readBody(response2));

        assertEquals(2, trackingHandler.connectionCount(), "Should create new connection after idle cleanup");
    }
}
