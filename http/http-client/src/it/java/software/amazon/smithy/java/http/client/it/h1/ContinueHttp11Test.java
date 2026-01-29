/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.ContinueHttp11ClientHandler;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Tests HTTP/1.1 100-continue handling.
 */
public class ContinueHttp11Test extends BaseHttpClientIntegTest {

    private ContinueHttp11ClientHandler handler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        handler = new ContinueHttp11ClientHandler(RESPONSE_CONTENTS);
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> handler);
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1);
    }

    @Test
    void handles100ContinueCorrectly() throws Exception {
        var headers = HttpHeaders.ofModifiable();
        headers.addHeader("content-type", "text/plain");
        headers.addHeader("expect", "100-continue");
        headers.addHeader("content-length", String.valueOf(REQUEST_CONTENTS.length()));

        var request = HttpRequest.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .uri(new URI(uri()))
                .method("POST")
                .headers(headers)
                .body(DataStream.ofString(REQUEST_CONTENTS))
                .build();

        var response = client.send(request);

        assertEquals(RESPONSE_CONTENTS, readBody(response));
        assertEquals(REQUEST_CONTENTS, handler.capturedBody().toString(StandardCharsets.UTF_8));
    }
}
