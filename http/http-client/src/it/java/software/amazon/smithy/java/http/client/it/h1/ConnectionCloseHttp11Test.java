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
import software.amazon.smithy.java.http.client.it.server.h1.ConnectionCloseHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.ConnectionTrackingHttp11ClientHandler;

/**
 * Tests that Connection: close header prevents connection reuse.
 */
public class ConnectionCloseHttp11Test extends BaseHttpClientIntegTest {

    private ConnectionTrackingHttp11ClientHandler trackingHandler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        trackingHandler = new ConnectionTrackingHttp11ClientHandler(
                new ConnectionCloseHttp11ClientHandler(RESPONSE_CONTENTS));
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> trackingHandler);
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1);
    }

    @Test
    void connectionNotReusedWhenServerSendsClose() throws Exception {
        // Send two requests
        for (int i = 0; i < 2; i++) {
            var request = plainTextRequest(HttpVersion.HTTP_1_1, "");
            var response = client.send(request);
            assertEquals(RESPONSE_CONTENTS, readBody(response));
        }

        // Each request should use a new connection since server sends Connection: close
        assertEquals(2, trackingHandler.requestCount());
        assertEquals(2, trackingHandler.connectionCount(), "Should create new connection for each request");
    }
}
