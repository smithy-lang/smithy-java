/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class H2ResponseHeaderProcessorTest {

    // Helper to create flat header list
    private static List<String> headers(String... pairs) {
        return List.of(pairs);
    }

    @Test
    void validResponseHeaders() throws IOException {
        var fields = headers(
                ":status",
                "200",
                "content-type",
                "application/json",
                "content-length",
                "42");

        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);

        assertEquals(200, result.statusCode());
        assertEquals(42, result.contentLength());
        assertEquals("application/json", result.headers().firstValue("content-type"));
    }

    @Test
    void informationalResponse() throws IOException {
        var fields = headers(":status", "100");

        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);

        assertTrue(result.isInformational());
    }

    @Test
    void informationalResponseWithEndStreamThrows() {
        var fields = headers(":status", "100");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, true));
        assertTrue(ex.getMessage().contains("1xx response must not have END_STREAM"));
    }

    @Test
    void missingStatusThrows() {
        var fields = headers("content-type", "text/plain");

        assertThrows(IOException.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
    }

    @Test
    void duplicateStatusThrows() {
        var fields = headers(":status", "200", ":status", "201");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
        assertTrue(ex.getMessage().contains("single :status"));
    }

    @Test
    void invalidStatusValueThrows() {
        var fields = headers(":status", "abc");

        assertThrows(IOException.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
    }

    @Test
    void pseudoHeaderAfterRegularHeaderThrows() {
        var fields = headers(
                ":status",
                "200",
                "content-type",
                "text/plain",
                ":unknown",
                "value");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
        assertTrue(ex.getMessage().contains("appears after regular header"));
    }

    @Test
    void requestPseudoHeaderInResponseThrows() {
        var fields = headers(":status", "200", ":method", "GET");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
        assertTrue(ex.getMessage().contains("Request pseudo-header"));
    }

    @Test
    void unknownPseudoHeaderThrows() {
        var fields = headers(":status", "200", ":unknown", "value");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
        assertTrue(ex.getMessage().contains("Unknown pseudo-header"));
    }

    @Test
    void invalidContentLengthThrows() {
        var fields = headers(":status", "200", "content-length", "not-a-number");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
        assertTrue(ex.getMessage().contains("Invalid Content-Length"));
    }

    @Test
    void multipleConflictingContentLengthThrows() {
        var fields = headers(
                ":status",
                "200",
                "content-length",
                "100",
                "content-length",
                "200");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
        assertTrue(ex.getMessage().contains("Multiple Content-Length"));
    }

    @Test
    void duplicateIdenticalContentLengthAllowed() throws IOException {
        var fields = headers(
                ":status",
                "200",
                "content-length",
                "100",
                "content-length",
                "100");

        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);
        assertEquals(100, result.contentLength());
    }

    @Test
    void noContentLengthReturnsMinusOne() throws IOException {
        var fields = headers(":status", "200");

        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);
        assertEquals(-1, result.contentLength());
    }

    // === processTrailers ===

    @Test
    void validTrailers() throws IOException {
        var fields = headers(
                "x-checksum",
                "abc123",
                "x-request-id",
                "req-456");

        var trailers = H2ResponseHeaderProcessor.processTrailers(fields, 1);

        assertEquals("abc123", trailers.firstValue("x-checksum"));
        assertEquals("req-456", trailers.firstValue("x-request-id"));
    }

    @Test
    void trailerWithPseudoHeaderThrows() {
        var fields = headers(":status", "200");

        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processTrailers(fields, 1));
        assertTrue(ex.getMessage().contains("Trailer contains pseudo-header"));
    }

    @Test
    void emptyTrailersAllowed() throws IOException {
        var trailers = H2ResponseHeaderProcessor.processTrailers(List.of(), 1);
        assertTrue(trailers.map().isEmpty());
    }

    // === validateContentLength ===

    @Test
    void contentLengthMatchPasses() throws IOException {
        H2ResponseHeaderProcessor.validateContentLength(100, 100, 1);
    }

    @Test
    void contentLengthMismatchThrows() {
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.validateContentLength(100, 50, 1));
        assertTrue(ex.getMessage().contains("Content-Length mismatch"));
    }

    @Test
    void noContentLengthSkipsValidation() throws IOException {
        H2ResponseHeaderProcessor.validateContentLength(-1, 999, 1);
    }
}
