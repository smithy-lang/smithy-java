/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.crt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

class CrtHttpClientTransportTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsGetAndPutOverHttp1() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", exchange -> {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            byte[] responseBytes = (exchange.getRequestMethod() + ":" + new String(requestBytes, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "text/plain");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();

        var transport = new CrtHttpClientTransport();
        try {
            var uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/echo";
            HttpRequest request = HttpRequest.create()
                    .setMethod("PUT")
                    .setUri(URI.create(uri))
                    .setHttpVersion(HttpVersion.HTTP_1_1)
                    .setBody(DataStream.ofString("hello", "text/plain"))
                    .toUnmodifiable();

            HttpResponse response = transport.send(Context.create(), request);
            try (var body = response.body().asInputStream()) {
                assertThat(response.statusCode(), equalTo(200));
                assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8), equalTo("PUT:hello"));
            }
        } finally {
            transport.close();
        }
    }
}
