/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.GoawayAfterFirstRequestHandler;

/**
 * Tests that GOAWAY is handled gracefully - subsequent requests use new connection.
 */
public class GoawayHttp2Test extends BaseHttpClientIntegTest {

    private GoawayAfterFirstRequestHandler handler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        handler = new GoawayAfterFirstRequestHandler(RESPONSE_CONTENTS);
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> handler);
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
    }

    @Test
    void handlesGoawayGracefully() throws Exception {
        // First request - server will send GOAWAY after response
        var request1 = plainTextRequest(HttpVersion.HTTP_2, "");
        var response1 = client.send(request1);
        assertEquals(RESPONSE_CONTENTS, readBody(response1));

        // Wait for GOAWAY to be processed
        Thread.sleep(200);

        // Second request - should use new connection due to GOAWAY
        var request2 = plainTextRequest(HttpVersion.HTTP_2, "");
        var response2 = client.send(request2);
        assertEquals(RESPONSE_CONTENTS, readBody(response2));

        assertEquals(2, handler.connectionCount(), "Should use new connection after GOAWAY");
    }
}
