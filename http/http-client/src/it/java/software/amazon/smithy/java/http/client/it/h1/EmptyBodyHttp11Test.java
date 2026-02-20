/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.EmptyResponseHttp11ClientHandler;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Tests empty request and response bodies.
 */
public class EmptyBodyHttp11Test extends BaseHttpClientIntegTest {

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new EmptyResponseHttp11ClientHandler());
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1);
    }

    @Test
    void handlesEmptyRequestAndResponseBody() throws Exception {
        // Request with no body
        var request = HttpRequest.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .uri(new URI(uri()))
                .method("GET")
                .headers(HttpHeaders.ofModifiable())
                .body(DataStream.ofEmpty())
                .build();

        var response = client.send(request);

        assertEquals(204, response.statusCode());
        assertEquals("", readBody(response));
    }

    @Test
    void handlesPostWithEmptyBody() throws Exception {
        var headers = HttpHeaders.ofModifiable();
        headers.addHeader("content-length", "0");

        var request = HttpRequest.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .uri(new URI(uri()))
                .method("POST")
                .headers(headers)
                .body(DataStream.ofEmpty())
                .build();

        var response = client.send(request);

        assertEquals(204, response.statusCode());
        assertEquals("", readBody(response));
    }
}
