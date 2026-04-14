/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

class BufferedHttpExchangeTest {

    @Test
    void returnsRequest() {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("http://example.com"));
        var response = HttpResponse.create().setStatusCode(200);
        var exchange = new BufferedHttpExchange(request, response);

        assertEquals(request, exchange.request());
    }

    @Test
    void returnsResponseStatusCode() {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("http://example.com"));
        var response = HttpResponse.create().setStatusCode(404);
        var exchange = new BufferedHttpExchange(request, response);

        assertEquals(404, exchange.responseStatusCode());
    }

    @Test
    void returnsResponseHeaders() {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("http://example.com"));
        var response = HttpResponse.create()
                .setStatusCode(200)
                .addHeader("Content-Type", "application/json");
        var exchange = new BufferedHttpExchange(request, response);

        assertEquals("application/json", exchange.responseHeaders().firstValue("Content-Type"));
    }

    @Test
    void returnsResponseBody() throws IOException {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("http://example.com"));
        var response = HttpResponse.create()
                .setStatusCode(200)
                .setBody(DataStream.ofString("hello"));
        var exchange = new BufferedHttpExchange(request, response);
        var body = new String(exchange.responseBody().readAllBytes());

        assertEquals("hello", body);
    }

    @Test
    void requestBodyIsNoOp() throws IOException {
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(SmithyUri.of("http://example.com"));
        var response = HttpResponse.create().setStatusCode(200);
        var exchange = new BufferedHttpExchange(request, response);
        var out = exchange.requestBody();

        assertNotNull(out);
        out.write(new byte[] {1, 2, 3}); // should not throw
        out.close();
    }

    @Test
    void doesNotSupportBidirectionalStreaming() {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("http://example.com"));
        var response = HttpResponse.create().setStatusCode(200);
        var exchange = new BufferedHttpExchange(request, response);

        assertFalse(exchange.supportsBidirectionalStreaming());
    }

    @Test
    void closeDoesNotThrow() throws IOException {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(SmithyUri.of("http://example.com"));
        var response = HttpResponse.create().setStatusCode(200);
        var exchange = new BufferedHttpExchange(request, response);

        exchange.close(); // should not throw
    }
}
