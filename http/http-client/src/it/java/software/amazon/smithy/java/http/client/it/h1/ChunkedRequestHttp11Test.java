/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.http.client.it.TestUtils.IPSUM_LOREM;
import static software.amazon.smithy.java.http.client.it.TestUtils.streamingBody;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.MultiplexingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.RequestCapturingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests HTTP/1.1 chunked transfer encoding for request body.
 */
public class ChunkedRequestHttp11Test extends BaseHttpClientIntegTest {

    private RequestCapturingHttp11ClientHandler requestCapturingHandler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        requestCapturingHandler = new RequestCapturingHttp11ClientHandler();
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new MultiplexingHttp11ClientHandler(
                        requestCapturingHandler,
                        new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS)));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1);
    }

    @Test
    void canSendChunkedRequestBody() throws Exception {
        // streamingBody has unknown content length (-1), so it will use chunked encoding
        var request = TestUtils.request(HttpVersion.HTTP_1_1, uri(), streamingBody(IPSUM_LOREM));

        var response = client.send(request);
        var responseBody = readBody(response);

        assertEquals(String.join("", IPSUM_LOREM),
                requestCapturingHandler.capturedBody().toString(StandardCharsets.UTF_8));
        assertEquals(RESPONSE_CONTENTS, responseBody);
    }
}
