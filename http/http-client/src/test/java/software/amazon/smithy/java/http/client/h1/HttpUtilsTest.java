/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpHeaders;

class HttpUtilsTest {

    @Test
    void internHeaderReturnsInternedStringForKnownHeaders() {
        var buf = "content-type".getBytes(StandardCharsets.US_ASCII);
        var result = HttpUtils.internHeader(buf, 0, buf.length);

        assertSame("content-type", result);
    }

    @Test
    void internHeaderIsCaseInsensitive() {
        var buf = "Content-Type".getBytes(StandardCharsets.US_ASCII);
        var result = HttpUtils.internHeader(buf, 0, buf.length);

        assertSame("content-type", result);
    }

    @Test
    void internHeaderReturnsNewStringForUnknownHeaders() {
        var buf = "x-custom-header".getBytes(StandardCharsets.US_ASCII);
        var result = HttpUtils.internHeader(buf, 0, buf.length);

        assertEquals("x-custom-header", result);
    }

    @Test
    void parseHeaderLineAddsHeader() {
        var buf = "Content-Type: application/json".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        var name = HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("content-type", name);
        assertEquals("application/json", headers.firstValue("content-type"));
    }

    @Test
    void parseHeaderLineTrimsWhitespace() {
        var buf = "Content-Type:   application/json  ".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("application/json", headers.firstValue("content-type"));
    }

    @Test
    void parseHeaderLineReturnsNullForMalformedLine() {
        var buf = "no-colon-here".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        var name = HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertNull(name);
    }

    @Test
    void parseHeaderLineHandlesEmptyValue() {
        var buf = "X-Empty:".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("", headers.firstValue("X-Empty"));
    }

    @Test
    void parseHeaderLineReturnsNullForColonAtStart() {
        var buf = ": value".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        var name = HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertNull(name);
    }

    @Test
    void parseHeaderLineHandlesValueWithColons() {
        var buf = "Location: http://example.com:8080/path".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("http://example.com:8080/path", headers.firstValue("location"));
    }

    @Test
    void parseHeaderLineHandlesTabWhitespace() {
        var buf = "Content-Type:\t application/json".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("application/json", headers.firstValue("content-type"));
    }

    @Test
    void internHeaderHandlesOffset() {
        var buf = "XXXcontent-typeYYY".getBytes(StandardCharsets.US_ASCII);
        var result = HttpUtils.internHeader(buf, 3, 12);

        assertSame("content-type", result);
    }
}
