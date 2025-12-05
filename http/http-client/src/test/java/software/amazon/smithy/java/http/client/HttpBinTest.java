/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Integration tests using httpbin.org to verify HTTP client functionality.
 *
 * This test class validates the HTTP client against real-world scenarios using httpbin.org,
 * a free HTTP testing service.
 */
@Disabled("Integration test - requires external service")
class HttpBinTest {

    private static final String HTTPBIN_URL = "https://httpbin.org";

    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder().build())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    // ========================================================================
    // Example 1: Creating a Client
    // ========================================================================

    @Test
    void testClientCreation_withDefaults() throws IOException {
        HttpClient simpleClient = HttpClient.builder()
                .build();
        assertNotNull(simpleClient);
        simpleClient.close();
    }

    // ========================================================================
    // Example 2: Sending Request and Getting Normal Response (Non-Streaming)
    // ========================================================================

    @Test
    void testSimpleGetRequest() throws IOException {
        // Simple GET request
        HttpRequest getRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/get"))
                .method("GET")
                .build();

        HttpResponse response = client.send(getRequest);
        int status = response.statusCode();
        assertEquals(200, status, "Expected 200 OK status");

        // Read entire body into memory
        try (InputStream in = response.body().asInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertNotNull(body);
            assertTrue(body.contains("\"url\""), "Response should contain URL field");
        }
    }

    @Test
    void testPostRequestWithJsonBody() throws IOException {
        // POST request with JSON body
        String jsonPayload = "{\"name\":\"John\",\"email\":\"john@example.com\"}";
        byte[] jsonBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

        HttpRequest postRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/post"))
                .method("POST")
                .body(DataStream.ofInputStream(new ByteArrayInputStream(jsonBytes)))
                .withAddedHeader("Content-Type", "application/json")
                .withAddedHeader("Content-Length", String.valueOf(jsonBytes.length))
                .build();

        HttpResponse response = client.send(postRequest);
        assertEquals(200, response.statusCode(), "Expected 200 OK status");

        try (InputStream in = response.body().asInputStream()) {
            String responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(responseBody.contains("John"), "Response should contain posted data");
            assertTrue(responseBody.contains("john@example.com"), "Response should contain posted email");
        }
    }

    @Test
    void testRequestWithCustomHeaders() throws IOException {
        // Request with custom headers
        HttpRequest authedRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/headers"))
                .method("GET")
                .withAddedHeader("Authorization", "Bearer my-access-token")
                .withAddedHeader("X-API-Version", "2.0")
                .build();

        HttpResponse response = client.send(authedRequest);
        assertEquals(200, response.statusCode());

        try (InputStream in = response.body().asInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("Bearer my-access-token"), "Should echo Authorization header");
            assertTrue(body.contains("2.0"), "Should echo X-API-Version header");
        }
    }

    // ========================================================================
    // Example 3: Sending Streaming Request, Normal Response
    // ========================================================================

    @Test
    void testStreamingRequestWithChunkedEncoding() throws IOException {
        // Upload with streaming request using chunked encoding
        HttpRequest uploadRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/post"))
                .method("POST")
                .withAddedHeader("Content-Type", "application/octet-stream")
                .withAddedHeader("Transfer-Encoding", "chunked")
                .build();

        HttpExchange exchange = client.newExchange(uploadRequest);

        // Stream upload data in chunks
        try (OutputStream out = exchange.requestBody()) {
            // Simulate streaming data
            byte[] chunk1 = "Hello, ".getBytes(StandardCharsets.UTF_8);
            byte[] chunk2 = "World!".getBytes(StandardCharsets.UTF_8);

            out.write(chunk1);
            out.flush();
            out.write(chunk2);
            out.flush();
        }

        // Get buffered response
        int status = exchange.responseStatusCode();
        assertEquals(200, status, "Upload should succeed");

        try (InputStream responseBody = exchange.responseBody()) {
            String result = new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(result.contains("Hello, World!"), "Response should contain uploaded data");
        }

        exchange.close();
    }

    // ========================================================================
    // Example 4: Streaming Request and Response (Sequential - Not Bidirectional)
    // ========================================================================

    @Test
    void testStreamingRequestThenStreamingResponse() throws IOException {
        // Send data, then receive streaming response
        String data = "{\"test\":\"streaming\"}";
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);

        HttpRequest streamRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/post"))
                .method("POST")
                .withAddedHeader("Content-Type", "application/json")
                .withAddedHeader("Content-Length", String.valueOf(dataBytes.length))
                .build();

        var exchange = client.newExchange(streamRequest);

        // First: Stream the request
        try (OutputStream out = exchange.requestBody()) {
            out.write(dataBytes);
        }

        // Then: Stream the response
        try (InputStream in = exchange.responseBody();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            assertTrue(response.toString().contains("streaming"),
                    "Response should contain posted data");
        }

        exchange.close();
    }

    // ========================================================================
    // Example 5: Bidirectional Request/Response (True Streaming)
    // NOTE: This test is commented out because it requires preview features
    // (StructuredTaskScope) which need --enable-preview flag
    // ========================================================================

    // @Test
    // void testBidirectionalStreamingPattern() throws Exception {
    //     // This demonstrates the API pattern for bidirectional streaming
    //     // In practice, you'd use this with a server that supports SSE, WebSocket, etc.
    //     // Requires StructuredTaskScope which is a preview feature in Java 21
    // }

    // ========================================================================
    // Additional Tests: Error Handling and Edge Cases
    // ========================================================================

    @Test
    void test404NotFound() throws IOException {
        HttpRequest notFoundRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/status/404"))
                .method("GET")
                .build();

        HttpResponse response = client.send(notFoundRequest);
        assertEquals(404, response.statusCode(), "Should return 404");
    }

    @Test
    void testDelayedResponse() throws IOException {
        // httpbin.org provides /delay/N endpoint for testing timeouts
        HttpRequest delayRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/delay/1"))
                .method("GET")
                .build();

        HttpResponse response = client.send(delayRequest);
        assertEquals(200, response.statusCode(), "Should handle delayed response");
    }

    @Test
    void testResponseWithSpecificStatus() throws IOException {
        // Test specific status codes
        HttpRequest statusRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/status/201"))
                .method("GET")
                .build();

        HttpResponse response = client.send(statusRequest);
        assertEquals(201, response.statusCode(), "Should return requested status code");
    }

    @Test
    void testGzipCompression() throws IOException {
        // httpbin.org can return gzip compressed responses
        HttpRequest gzipRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/gzip"))
                .method("GET")
                .build();

        HttpResponse response = client.send(gzipRequest);
        assertEquals(200, response.statusCode());

        // Manually handle gzip decompression based on Content-Encoding header
        InputStream bodyStream = response.body().asInputStream();
        String contentEncoding = response.headers().firstValue("Content-Encoding");

        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
            bodyStream = new GZIPInputStream(bodyStream);
        }

        try (InputStream in = bodyStream) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("gzipped"), "Should receive gzipped response");
        }
    }

    // ========================================================================
    // Example 6: Expect: 100-continue
    // ========================================================================

    @Test
    void testExpect100Continue() throws IOException {
        // Large payload to justify using 100-continue
        String largePayload = "x".repeat(10000);
        byte[] payloadBytes = largePayload.getBytes(StandardCharsets.UTF_8);

        HttpRequest expectContinueRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/post"))
                .method("POST")
                .body(DataStream.ofInputStream(new ByteArrayInputStream(payloadBytes)))
                .withAddedHeader("Content-Type", "text/plain")
                .withAddedHeader("Content-Length", String.valueOf(payloadBytes.length))
                .withAddedHeader("Expect", "100-continue")
                .build();

        HttpResponse response = client.send(expectContinueRequest);
        assertEquals(200, response.statusCode(), "Should handle 100-continue and return 200");

        try (InputStream in = response.body().asInputStream()) {
            String responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // httpbin echoes the data back in the response
            assertTrue(responseBody.contains("\"data\": \"" + largePayload.substring(0, 20)),
                    "Response should contain posted data");
        }
    }

    @Test
    void testExpect100ContinueWithExchange() throws IOException {
        // Test streaming with 100-continue using exchange API
        HttpRequest expectContinueRequest = HttpRequest.builder()
                .uri(URI.create(HTTPBIN_URL + "/post"))
                .method("POST")
                .withAddedHeader("Content-Type", "application/octet-stream")
                .withAddedHeader("Transfer-Encoding", "chunked")
                .withAddedHeader("Expect", "100-continue")
                .build();

        HttpExchange exchange = client.newExchange(expectContinueRequest);

        // Write request body - client waits for 100 Continue before sending
        try (OutputStream out = exchange.requestBody()) {
            byte[] data = "Test data for 100-continue".getBytes(StandardCharsets.UTF_8);
            out.write(data);
        }

        // Read response
        assertEquals(200, exchange.responseStatusCode(), "Should receive 200 after 100-continue");

        try (InputStream in = exchange.responseBody()) {
            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.contains("Test data for 100-continue"),
                    "Response should contain posted data");
        }

        exchange.close();
    }
}
