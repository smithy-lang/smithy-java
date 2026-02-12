/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.h2.hpack.HeaderField;

class H2ResponseHeaderProcessorTest {

    private static HeaderField hf(String name, String value) {
        return new HeaderField(name, value);
    }

    @Test
    void validResponseHeaders() throws IOException {
        var fields = List.of(
                hf(":status", "200"),
                hf("content-type", "application/json"),
                hf("content-length", "42"));

        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);

        assertEquals(200, result.statusCode());
        assertEquals(42, result.contentLength());
        assertEquals("application/json", result.headers().firstValue("content-type"));
    }

    @Test
    void informationalResponse() throws IOException {
        var fields = List.of(hf(":status", "100"));
        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);

        assertTrue(result.isInformational());
    }

    @Test
    void informationalResponseWithEndStreamThrows() {
        var fields = List.of(hf(":status", "100"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, true));

        assertTrue(ex.getMessage().contains("1xx response must not have END_STREAM"));
    }

    @Test
    void missingStatusThrows() {
        var fields = List.of(hf("content-type", "text/plain"));

        assertThrows(IOException.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
    }

    @Test
    void duplicateStatusThrows() {
        var fields = List.of(hf(":status", "200"), hf(":status", "201"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));

        assertTrue(ex.getMessage().contains("single :status"));
    }

    @Test
    void invalidStatusValueThrows() {
        var fields = List.of(hf(":status", "abc"));

        assertThrows(IOException.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));
    }

    @Test
    void pseudoHeaderAfterRegularHeaderThrows() {
        var fields = List.of(
                hf(":status", "200"),
                hf("content-type", "text/plain"),
                hf(":unknown", "value"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));

        assertTrue(ex.getMessage().contains("appears after regular header"));
    }

    @Test
    void requestPseudoHeaderInResponseThrows() {
        var fields = List.of(hf(":status", "200"), hf(":method", "GET"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));

        assertTrue(ex.getMessage().contains("Request pseudo-header"));
    }

    @Test
    void unknownPseudoHeaderThrows() {
        var fields = List.of(hf(":status", "200"), hf(":unknown", "value"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));

        assertTrue(ex.getMessage().contains("Unknown pseudo-header"));
    }

    @Test
    void invalidContentLengthThrows() {
        var fields = List.of(hf(":status", "200"), hf("content-length", "not-a-number"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));

        assertTrue(ex.getMessage().contains("Invalid Content-Length"));
    }

    @Test
    void multipleConflictingContentLengthThrows() {
        var fields = List.of(
                hf(":status", "200"),
                hf("content-length", "100"),
                hf("content-length", "200"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false));

        assertTrue(ex.getMessage().contains("Multiple Content-Length"));
    }

    @Test
    void duplicateIdenticalContentLengthAllowed() throws IOException {
        var fields = List.of(
                hf(":status", "200"),
                hf("content-length", "100"),
                hf("content-length", "100"));
        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);

        assertEquals(100, result.contentLength());
    }

    @Test
    void noContentLengthReturnsMinusOne() throws IOException {
        var fields = List.of(hf(":status", "200"));
        var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, 1, false);

        assertEquals(-1, result.contentLength());
    }

    @Test
    void validTrailers() throws IOException {
        var fields = List.of(
                hf("x-checksum", "abc123"),
                hf("x-request-id", "req-456"));

        var trailers = H2ResponseHeaderProcessor.processTrailers(fields, 1);

        assertEquals("abc123", trailers.firstValue("x-checksum"));
        assertEquals("req-456", trailers.firstValue("x-request-id"));
    }

    @Test
    void trailerWithPseudoHeaderThrows() {
        var fields = List.of(hf(":status", "200"));
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.processTrailers(fields, 1));

        assertTrue(ex.getMessage().contains("Trailer contains pseudo-header"));
    }

    @Test
    void emptyTrailersAllowed() throws IOException {
        var trailers = H2ResponseHeaderProcessor.processTrailers(List.of(), 1);

        assertTrue(trailers.map().isEmpty());
    }

    @Test
    void contentLengthMatchPasses() {
        Assertions.assertDoesNotThrow(() -> {
            H2ResponseHeaderProcessor.validateContentLength(100, 100, 1);
        });
    }

    @Test
    void contentLengthMismatchThrows() {
        var ex = assertThrows(H2Exception.class,
                () -> H2ResponseHeaderProcessor.validateContentLength(100, 50, 1));

        assertTrue(ex.getMessage().contains("Content-Length mismatch"));
        assertTrue(ex.getMessage().contains("expected 100"));
        assertTrue(ex.getMessage().contains("received 50"));
    }

    @Test
    void noContentLengthSkipsValidation() {
        Assertions.assertDoesNotThrow(() -> {
            H2ResponseHeaderProcessor.validateContentLength(-1, 999, 1);
        });
    }
}
