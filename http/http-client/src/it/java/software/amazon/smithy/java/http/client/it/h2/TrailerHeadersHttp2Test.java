/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.TrailerResponseHttp2ClientHandler;

/**
 * Tests HTTP/2 response with trailer headers.
 */
public class TrailerHeadersHttp2Test extends BaseHttpClientIntegTest {

    private static final Map<String, String> TRAILERS = Map.of(
            "x-checksum",
            "abc123",
            "x-request-id",
            "req-456");

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new TrailerResponseHttp2ClientHandler(RESPONSE_CONTENTS, TRAILERS));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
    }

    @Test
    void readsResponseWithTrailers() throws Exception {
        var request = plainTextRequest(HttpVersion.HTTP_2, "");

        try (var exchange = client.newExchange(request)) {
            exchange.requestBody().close();

            var body = new String(exchange.responseBody().readAllBytes());
            assertEquals(RESPONSE_CONTENTS, body);

            var trailers = exchange.responseTrailerHeaders();
            assertNotNull(trailers, "Should have trailer headers");
            assertEquals("abc123", trailers.firstValue("x-checksum"));
            assertEquals("req-456", trailers.firstValue("x-request-id"));
        }
    }
}
