/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.PartialResponseHttp2ClientHandler;

/**
 * Tests that server closing connection mid-response throws IOException.
 */
public class ServerCloseMidStreamHttp2Test extends BaseHttpClientIntegTest {

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new PartialResponseHttp2ClientHandler());
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
    }

    @Test
    void handlesServerClosingConnectionMidResponse() {
        var request = plainTextRequest(HttpVersion.HTTP_2, "");

        assertThrows(IOException.class, () -> {
            var response = client.send(request);
            // Try to read full body - should fail since server closed mid-stream
            response.body().asInputStream().readAllBytes();
        });
    }
}
