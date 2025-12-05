/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

class BufferedHttpExchangeTest {

    @Test
    void returnsRequest() {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("http://example.com"))
                .build();
        var response = HttpResponse.builder().statusCode(200).build();
        var exchange = new BufferedHttpExchange(request, response);

        assertEquals(request, exchange.request());
    }

    @Test
    void returnsResponseStatusCode() {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("http://example.com"))
                .build();
        var response = HttpResponse.builder().statusCode(404).build();
        var exchange = new BufferedHttpExchange(request, response);

        assertEquals(404, exchange.responseStatusCode());
    }

    @Test
    void returnsResponseHeaders() {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("http://example.com"))
                .build();
        var response = HttpResponse.builder()
                .statusCode(200)
                .withAddedHeader("Content-Type", "application/json")
                .build();
        var exchange = new BufferedHttpExchange(request, response);

        assertEquals("application/json", exchange.responseHeaders().firstValue("Content-Type"));
    }

    @Test
    void returnsResponseBody() throws IOException {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("http://example.com"))
                .build();
        var response = HttpResponse.builder()
                .statusCode(200)
                .body(DataStream.ofString("hello"))
                .build();
        var exchange = new BufferedHttpExchange(request, response);
        var body = new String(exchange.responseBody().readAllBytes());

        assertEquals("hello", body);
    }

    @Test
    void requestBodyIsNoOp() throws IOException {
        var request = HttpRequest.builder()
                .method("POST")
                .uri(URI.create("http://example.com"))
                .build();
        var response = HttpResponse.builder().statusCode(200).build();
        var exchange = new BufferedHttpExchange(request, response);
        var out = exchange.requestBody();

        assertNotNull(out);
        out.write(new byte[] {1, 2, 3}); // should not throw
        out.close();
    }

    @Test
    void doesNotSupportBidirectionalStreaming() {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("http://example.com"))
                .build();
        var response = HttpResponse.builder().statusCode(200).build();
        var exchange = new BufferedHttpExchange(request, response);

        assertFalse(exchange.supportsBidirectionalStreaming());
    }

    @Test
    void closeDoesNotThrow() throws IOException {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("http://example.com"))
                .build();
        var response = HttpResponse.builder().statusCode(200).build();
        var exchange = new BufferedHttpExchange(request, response);

        exchange.close(); // should not throw
    }
}
