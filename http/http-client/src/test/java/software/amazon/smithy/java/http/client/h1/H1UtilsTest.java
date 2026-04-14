/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;

class H1UtilsTest {

    static Stream<Arguments> knownHeaders() {
        return Stream.of(
                // Common headers
                Arguments.of("date", HeaderName.DATE.name()),
                Arguments.of("vary", HeaderName.VARY.name()),
                Arguments.of("etag", HeaderName.ETAG.name()),
                Arguments.of("server", HeaderName.SERVER.name()),
                Arguments.of("trailer", HeaderName.TRAILER.name()),
                Arguments.of("expires", HeaderName.EXPIRES.name()),
                Arguments.of("upgrade", HeaderName.UPGRADE.name()),
                Arguments.of("location", HeaderName.LOCATION.name()),
                Arguments.of("connection", HeaderName.CONNECTION.name()),
                Arguments.of("keep-alive", HeaderName.KEEP_ALIVE.name()),
                Arguments.of("set-cookie", HeaderName.SET_COOKIE.name()),
                Arguments.of("content-type", HeaderName.CONTENT_TYPE.name()),
                Arguments.of("cache-control", HeaderName.CACHE_CONTROL.name()),
                Arguments.of("last-modified", HeaderName.LAST_MODIFIED.name()),
                Arguments.of("content-range", HeaderName.CONTENT_RANGE.name()),
                Arguments.of("accept-ranges", HeaderName.ACCEPT_RANGES.name()),
                Arguments.of("content-length", HeaderName.CONTENT_LENGTH.name()),
                Arguments.of("content-encoding", HeaderName.CONTENT_ENCODING.name()),
                Arguments.of("x-amzn-requestid", HeaderName.X_AMZN_REQUESTID.name()),
                Arguments.of("x-amz-request-id", HeaderName.X_AMZ_REQUEST_ID.name()),
                Arguments.of("www-authenticate", HeaderName.WWW_AUTHENTICATE.name()),
                Arguments.of("proxy-connection", HeaderName.PROXY_CONNECTION.name()),
                Arguments.of("transfer-encoding", HeaderName.TRANSFER_ENCODING.name()),
                Arguments.of("proxy-authenticate", HeaderName.PROXY_AUTHENTICATE.name()));
    }

    @ParameterizedTest
    @MethodSource("knownHeaders")
    void internsKnownHeader(String header, String expected) {
        byte[] buf = header.getBytes(StandardCharsets.US_ASCII);
        String result = HeaderName.canonicalize(buf, 0, buf.length);

        assertSame(expected, result);
    }

    @ParameterizedTest
    @MethodSource("knownHeaders")
    void internsKnownHeaderCaseInsensitive(String header, String expected) {
        byte[] buf = header.toUpperCase().getBytes(StandardCharsets.US_ASCII);
        String result = HeaderName.canonicalize(buf, 0, buf.length);

        assertSame(expected, result);
    }

    @Test
    void returnsNewStringForUnknownHeader() {
        byte[] buf = "x-custom".getBytes(StandardCharsets.US_ASCII);
        String result = HeaderName.canonicalize(buf, 0, buf.length);

        assertEquals("x-custom", result);
    }

    @Test
    void returnsNewStringForUnknownLengthMatch() {
        // Same length as "date" but different content
        byte[] buf = "test".getBytes(StandardCharsets.US_ASCII);
        String result = HeaderName.canonicalize(buf, 0, buf.length);

        assertEquals("test", result);
    }

    @Test
    void parseHeaderLineReturnsNullForMissingColon() {
        byte[] buf = "invalid header line".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        String result = H1Utils.parseHeaderLine(buf, buf.length, headers);

        assertNull(result);
    }

    @Test
    void parseHeaderLineReturnsNullForColonAtStart() {
        byte[] buf = ": value".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        String result = H1Utils.parseHeaderLine(buf, buf.length, headers);

        assertNull(result);
    }

    @Test
    void parseHeaderLineTrimsWhitespace() {
        byte[] buf = "name:   value   ".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        H1Utils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("value", headers.firstValue("name"));
    }

    @Test
    void parseHeaderLineTrimsTab() {
        byte[] buf = "name:\t\tvalue\t".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        H1Utils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("value", headers.firstValue("name"));
    }
}
