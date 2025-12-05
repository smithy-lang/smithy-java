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
import software.amazon.smithy.java.http.api.HttpHeaders;

class HttpUtilsTest {

    static Stream<Arguments> knownHeaders() {
        return Stream.of(
                // GROUP_4
                Arguments.of("date"),
                Arguments.of("vary"),
                Arguments.of("etag"),
                // GROUP_6
                Arguments.of("server"),
                // GROUP_7
                Arguments.of("trailer"),
                Arguments.of("expires"),
                Arguments.of("upgrade"),
                // GROUP_8
                Arguments.of("location"),
                // GROUP_10
                Arguments.of("connection"),
                Arguments.of("keep-alive"),
                Arguments.of("set-cookie"),
                // GROUP_12
                Arguments.of("content-type"),
                // GROUP_13
                Arguments.of("cache-control"),
                Arguments.of("last-modified"),
                Arguments.of("content-range"),
                Arguments.of("accept-ranges"),
                // GROUP_14
                Arguments.of("content-length"),
                // GROUP_16
                Arguments.of("content-encoding"),
                Arguments.of("x-amzn-requestid"),
                Arguments.of("x-amz-request-id"),
                Arguments.of("www-authenticate"),
                Arguments.of("proxy-connection"),
                // GROUP_17
                Arguments.of("transfer-encoding"),
                // GROUP_18
                Arguments.of("proxy-authenticate"));
    }

    @ParameterizedTest
    @MethodSource("knownHeaders")
    void internsKnownHeader(String header) {
        byte[] buf = header.getBytes(StandardCharsets.US_ASCII);
        String result = HttpUtils.internHeader(buf, 0, buf.length);

        assertSame(header, result);
    }

    @ParameterizedTest
    @MethodSource("knownHeaders")
    void internsKnownHeaderCaseInsensitive(String header) {
        byte[] buf = header.toUpperCase().getBytes(StandardCharsets.US_ASCII);
        String result = HttpUtils.internHeader(buf, 0, buf.length);

        assertSame(header, result);
    }

    @Test
    void returnsNewStringForUnknownHeader() {
        byte[] buf = "x-custom".getBytes(StandardCharsets.US_ASCII);
        String result = HttpUtils.internHeader(buf, 0, buf.length);

        assertEquals("x-custom", result);
    }

    @Test
    void returnsNewStringForUnknownLengthMatch() {
        // Same length as "date" but different content
        byte[] buf = "test".getBytes(StandardCharsets.US_ASCII);
        String result = HttpUtils.internHeader(buf, 0, buf.length);

        assertEquals("test", result);
    }

    @Test
    void parseHeaderLineReturnsNullForMissingColon() {
        byte[] buf = "invalid header line".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        String result = HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertNull(result);
    }

    @Test
    void parseHeaderLineReturnsNullForColonAtStart() {
        byte[] buf = ": value".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        String result = HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertNull(result);
    }

    @Test
    void parseHeaderLineTrimsWhitespace() {
        byte[] buf = "name:   value   ".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("value", headers.firstValue("name"));
    }

    @Test
    void parseHeaderLineTrimsTab() {
        byte[] buf = "name:\t\tvalue\t".getBytes(StandardCharsets.US_ASCII);
        var headers = HttpHeaders.ofModifiable();
        HttpUtils.parseHeaderLine(buf, buf.length, headers);

        assertEquals("value", headers.firstValue("name"));
    }
}
