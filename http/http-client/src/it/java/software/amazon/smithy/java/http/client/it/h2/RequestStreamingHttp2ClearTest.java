/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

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
import software.amazon.smithy.java.http.client.it.server.h2.MultiplexingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.RequestCapturingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.TextResponseHttp2ClientHandler;

/**
 * Tests HTTP/2 streaming request body over cleartext (h2c with prior knowledge).
 */
public class RequestStreamingHttp2ClearTest extends BaseHttpClientIntegTest {

    private RequestCapturingHttp2ClientHandler requestCapturingHandler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        requestCapturingHandler = new RequestCapturingHttp2ClientHandler();
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new MultiplexingHttp2ClientHandler(
                        requestCapturingHandler,
                        new TextResponseHttp2ClientHandler(RESPONSE_CONTENTS)));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
    }

    @Test
    void canSendStreamingRequestAndReadResponse() throws Exception {
        var request = TestUtils.request(HttpVersion.HTTP_2, uri(), streamingBody(IPSUM_LOREM));

        var response = client.send(request);
        requestCapturingHandler.streamCompleted().join();

        assertEquals(String.join("", IPSUM_LOREM),
                requestCapturingHandler.capturedBody().toString(StandardCharsets.UTF_8));
        assertEquals(RESPONSE_CONTENTS, readBody(response));
    }
}
