/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class URLEncodingTest {

    @ParameterizedTest
    @MethodSource("roundTripCases")
    void roundTrip(String input) {
        String encoded = URLEncoding.encodeUnreserved(input, false);
        assertThat(URLEncoding.urlDecode(encoded), equalTo(input));
    }

    static Stream<Arguments> roundTripCases() {
        return Stream.of(
                // Simple ASCII
                Arguments.of("hello"),
                Arguments.of(""),
                // Unreserved chars pass through
                Arguments.of("abc-._~ABC123"),
                // Reserved/special ASCII chars
                Arguments.of("a b"),
                Arguments.of("a=b&c=d"),
                Arguments.of("foo@bar!baz"),
                // Multi-byte UTF-8
                Arguments.of("café"),
                Arguments.of("日本語"),
                Arguments.of("über"),
                // Emoji (surrogate pairs)
                Arguments.of("hello 😀 world"),
                Arguments.of("🎉🎊"),
                // Mixed
                Arguments.of("path/to/café?q=hello world&lang=日本語"));
    }

    @ParameterizedTest
    @MethodSource("encodeCases")
    void encode(String input, boolean preserveSlashes, String expected) {
        assertThat(URLEncoding.encodeUnreserved(input, preserveSlashes), equalTo(expected));
    }

    static Stream<Arguments> encodeCases() {
        return Stream.of(
                // Unreserved chars are not encoded
                Arguments.of("abc", false, "abc"),
                Arguments.of("A-Z_0.9~", false, "A-Z_0.9~"),
                // Space
                Arguments.of("a b", false, "a%20b"),
                // Slash handling
                Arguments.of("/foo/bar", false, "%2Ffoo%2Fbar"),
                Arguments.of("/foo/bar", true, "/foo/bar"),
                // Reserved chars
                Arguments.of("a=b", false, "a%3Db"),
                Arguments.of("a&b", false, "a%26b"),
                // Multi-byte UTF-8
                Arguments.of("é", false, "%C3%A9"),
                // Emoji
                Arguments.of("😀", false, "%F0%9F%98%80"));
    }

    @ParameterizedTest
    @MethodSource("decodeCases")
    void decode(String input, String expected) {
        assertThat(URLEncoding.urlDecode(input), equalTo(expected));
    }

    static Stream<Arguments> decodeCases() {
        return Stream.of(
                // Basic decoding
                Arguments.of("hello", "hello"),
                Arguments.of("a%20b", "a b"),
                Arguments.of("%C3%A9", "é"),
                // Plus is NOT treated as space (unlike URLDecoder)
                Arguments.of("a+b", "a+b"),
                // Malformed percent sequences pass through
                Arguments.of("%", "%"),
                Arguments.of("%G1", "%G1"),
                Arguments.of("%2", "%2"),
                Arguments.of("100%", "100%"),
                // Mixed encoded and literal
                Arguments.of("hello%20world", "hello world"),
                Arguments.of("%2Ffoo%2Fbar", "/foo/bar"));
    }

    @Test
    void decodeNull() {
        assertThat(URLEncoding.urlDecode(null), nullValue());
    }

    @ParameterizedTest
    @MethodSource("matchesUrlDecoderCases")
    void matchesUrlDecoderForPercentEncoded(String input) {
        assertThat(
                URLEncoding.urlDecode(input),
                equalTo(URLDecoder.decode(input, StandardCharsets.UTF_8)));
    }

    static Stream<Arguments> matchesUrlDecoderCases() {
        return Stream.of(
                Arguments.of("hello"),
                Arguments.of("a%20b"),
                Arguments.of("%C3%A9"),
                Arguments.of("hello%20world%21"),
                Arguments.of("%E6%97%A5%E6%9C%AC%E8%AA%9E"),
                Arguments.of("%F0%9F%98%80"),
                Arguments.of("no+encoding+here".replace("+", "%2B")));
    }

    @Test
    void divergesFromUrlDecoderOnPlus() {
        // URLDecoder treats + as space (HTML form encoding), we don't (RFC 3986)
        assertThat(URLEncoding.urlDecode("a+b"), equalTo("a+b"));
        assertThat(URLDecoder.decode("a+b", StandardCharsets.UTF_8), equalTo("a b"));
        assertThat(URLEncoding.urlDecode("a+b"), not(equalTo(URLDecoder.decode("a+b", StandardCharsets.UTF_8))));
    }
}
