/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.ChunkedResponseHttp11ClientHandler;

/**
 * Tests HTTP/1.1 chunked transfer encoding response.
 */
public class ChunkedResponseHttp11Test extends BaseHttpClientIntegTest {

    private static final List<String> CHUNKS = List.of("Hello ", "chunked ", "world!");

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new ChunkedResponseHttp11ClientHandler(CHUNKS));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1);
    }

    @Test
    void readsChunkedTransferEncodingResponse() throws Exception {
        var request = plainTextRequest(HttpVersion.HTTP_1_1, "");
        var response = client.send(request);

        assertEquals(String.join("", CHUNKS), readBody(response));
    }
}
