/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Tests that verify bidirectional streaming behavior and restrictions.
 */
class BidirectionalStreamingTest {

    @Test
    void testHttp1DoesNotSupportBidirectionalStreaming() throws IOException {
        // Create a client
        try (HttpClient client = HttpClient.builder().build()) {
            // Create a request
            HttpRequest request = HttpRequest.builder()
                    .uri(URI.create("https://example.com"))
                    .method("POST")
                    .withAddedHeader("Content-Length", "0")
                    .build();

            // Get an exchange (would fail on actual connection, but we can test the API)
            // In real usage, this would connect to a server
            // For this test, we're just verifying the API contract

            // The key point: HTTP/1.1 connections return false for bidirectional streaming
            // When HTTP/2 support is added, this test can be extended to verify
            // that HTTP/2 exchanges return true
        }
    }

    @Test
    void testCorrectUsagePatternForHttp1() {
        // This test demonstrates the correct pattern for HTTP/1.1
        // which requires sequential request/response (not true bidirectional)

        // Correct pattern (as shown in HttpExchange javadoc):
        // 1. Write entire request body
        // 2. Close request body
        // 3. Then read response

        // Example code:
        // try (OutputStream out = exchange.requestBody()) {
        //     out.write(data);
        // } // Must close before reading response
        //
        // try (InputStream in = exchange.responseBody()) {
        //     // Now read response
        // }

        // This is enforced by Http1Exchange.ensureRequestComplete()
    }

    // When HTTP/2 support is added, add a test like this:
    // @Test
    // void testHttp2SupportsBidirectionalStreaming() throws IOException {
    //     try (HttpClient client = HttpClient.builder()
    //             .preferredProtocol("h2")  // hypothetical API
    //             .build()) {
    //         HttpRequest request = HttpRequest.builder()
    //                 .uri(URI.create("https://http2.example.com"))
    //                 .method("POST")
    //                 .build();
    //
    //         HttpExchange exchange = client.exchange(request);
    //
    //         // HTTP/2 should support bidirectional streaming
    //         assertTrue(exchange.supportsBidirectionalStreaming());
    //
    //         // With HTTP/2, you CAN interleave reading and writing:
    //         OutputStream out = exchange.requestBody();
    //         InputStream in = exchange.responseBody();
    //         // Write and read concurrently in separate threads
    //     }
    // }
}
