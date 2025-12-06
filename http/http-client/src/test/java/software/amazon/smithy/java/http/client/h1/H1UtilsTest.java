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
import software.amazon.smithy.java.http.api.HeaderNames;
import software.amazon.smithy.java.http.api.HttpHeaders;

class H1UtilsTest {

    static Stream<Arguments> knownHeaders() {
        return Stream.of(
                // Common headers
                Arguments.of("date", HeaderNames.DATE),
                Arguments.of("vary", HeaderNames.VARY),
                Arguments.of("etag", HeaderNames.ETAG),
                Arguments.of("server", HeaderNames.SERVER),
                Arguments.of("trailer", HeaderNames.TRAILER),
                Arguments.of("expires", HeaderNames.EXPIRES),
                Arguments.of("upgrade", HeaderNames.UPGRADE),
                Arguments.of("location", HeaderNames.LOCATION),
                Arguments.of("connection", HeaderNames.CONNECTION),
                Arguments.of("keep-alive", HeaderNames.KEEP_ALIVE),
                Arguments.of("set-cookie", HeaderNames.SET_COOKIE),
                Arguments.of("content-type", HeaderNames.CONTENT_TYPE),
                Arguments.of("cache-control", HeaderNames.CACHE_CONTROL),
                Arguments.of("last-modified", HeaderNames.LAST_MODIFIED),
                Arguments.of("content-range", HeaderNames.CONTENT_RANGE),
                Arguments.of("accept-ranges", HeaderNames.ACCEPT_RANGES),
                Arguments.of("content-length", HeaderNames.CONTENT_LENGTH),
                Arguments.of("content-encoding", HeaderNames.CONTENT_ENCODING),
                Arguments.of("x-amzn-requestid", HeaderNames.X_AMZN_REQUESTID),
                Arguments.of("x-amz-request-id", HeaderNames.X_AMZ_REQUEST_ID),
                Arguments.of("www-authenticate", HeaderNames.WWW_AUTHENTICATE),
                Arguments.of("proxy-connection", HeaderNames.PROXY_CONNECTION),
                Arguments.of("transfer-encoding", HeaderNames.TRANSFER_ENCODING),
                Arguments.of("proxy-authenticate", HeaderNames.PROXY_AUTHENTICATE));
    }

    @ParameterizedTest
    @MethodSource("knownHeaders")
    void internsKnownHeader(String header, String expected) {
        byte[] buf = header.getBytes(StandardCharsets.US_ASCII);
        String result = HeaderNames.canonicalize(buf, 0, buf.length);

        assertSame(expected, result);
    }

    @ParameterizedTest
    @MethodSource("knownHeaders")
    void internsKnownHeaderCaseInsensitive(String header, String expected) {
        byte[] buf = header.toUpperCase().getBytes(StandardCharsets.US_ASCII);
        String result = HeaderNames.canonicalize(buf, 0, buf.length);

        assertSame(expected, result);
    }

    @Test
    void returnsNewStringForUnknownHeader() {
        byte[] buf = "x-custom".getBytes(StandardCharsets.US_ASCII);
        String result = HeaderNames.canonicalize(buf, 0, buf.length);

        assertEquals("x-custom", result);
    }

    @Test
    void returnsNewStringForUnknownLengthMatch() {
        // Same length as "date" but different content
        byte[] buf = "test".getBytes(StandardCharsets.US_ASCII);
        String result = HeaderNames.canonicalize(buf, 0, buf.length);

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
