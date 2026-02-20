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
import software.amazon.smithy.java.http.client.it.server.h1.LargeHeadersHttp11ClientHandler;

/**
 * Tests handling of responses with many large headers.
 */
public class LargeHeadersHttp11Test extends BaseHttpClientIntegTest {

    private static final int HEADER_COUNT = 50;
    private static final int HEADER_VALUE_SIZE = 1000; // 1KB per header value

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new LargeHeadersHttp11ClientHandler(
                        RESPONSE_CONTENTS,
                        HEADER_COUNT,
                        HEADER_VALUE_SIZE));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1);
    }

    @Test
    void handlesLargeResponseHeaders() throws Exception {
        var request = plainTextRequest(HttpVersion.HTTP_1_1, "");
        var response = client.send(request);

        assertEquals(RESPONSE_CONTENTS, readBody(response));

        // Verify at least one large header was received
        String value = response.headers().firstValue("x-large-header-0");
        assertEquals(HEADER_VALUE_SIZE, value.length());
    }
}
