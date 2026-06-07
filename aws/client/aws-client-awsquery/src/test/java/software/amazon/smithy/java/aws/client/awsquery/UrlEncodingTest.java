/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests URL encoding correctness of the encoding used by QueryFormSerializer.
 * Uses {@link AwsQuerySchemaExtensions#encodeName} which exercises the same
 * lookup tables and encoding logic.
 */
class UrlEncodingTest {

    private static String urlEncode(String input) {
        return new String(AwsQuerySchemaExtensions.encodeName(input), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyz",
            "0123456789",
            "-._~",
            "Hello",
            "test123",
            "a-b.c_d~e"
    })
    void unreservedCharactersPassThrough(String input) {
        assertThat(urlEncode(input), equalTo(input));
    }

    @ParameterizedTest
    @MethodSource("reservedCharactersProvider")
    void reservedCharactersArePercentEncoded(String input, String expected) {
        assertThat(urlEncode(input), equalTo(expected));
    }

    static Stream<Arguments> reservedCharactersProvider() {
        return Stream.of(
                Arguments.of(" ", "%20"),
                Arguments.of("!", "%21"),
                Arguments.of("#", "%23"),
                Arguments.of("$", "%24"),
                Arguments.of("%", "%25"),
                Arguments.of("&", "%26"),
                Arguments.of("'", "%27"),
                Arguments.of("(", "%28"),
                Arguments.of(")", "%29"),
                Arguments.of("*", "%2A"),
                Arguments.of("+", "%2B"),
                Arguments.of(",", "%2C"),
                Arguments.of("/", "%2F"),
                Arguments.of(":", "%3A"),
                Arguments.of(";", "%3B"),
                Arguments.of("=", "%3D"),
                Arguments.of("?", "%3F"),
                Arguments.of("@", "%40"),
                Arguments.of("[", "%5B"),
                Arguments.of("]", "%5D"),
                Arguments.of("hello world", "hello%20world"),
                Arguments.of("a=b&c=d", "a%3Db%26c%3Dd"),
                Arguments.of("foo/bar", "foo%2Fbar"));
    }

    @ParameterizedTest
    @MethodSource("utf8TwoByteProvider")
    void twoByteUtf8CharactersAreEncoded(String input, String expected) {
        assertThat(urlEncode(input), equalTo(expected));
    }

    static Stream<Arguments> utf8TwoByteProvider() {
        return Stream.of(
                Arguments.of("é", "%C3%A9"),
                Arguments.of("ñ", "%C3%B1"),
                Arguments.of("ü", "%C3%BC"),
                Arguments.of("café", "caf%C3%A9"),
                Arguments.of("©", "%C2%A9"));
    }

    @ParameterizedTest
    @MethodSource("utf8ThreeByteProvider")
    void threeByteUtf8CharactersAreEncoded(String input, String expected) {
        assertThat(urlEncode(input), equalTo(expected));
    }

    static Stream<Arguments> utf8ThreeByteProvider() {
        return Stream.of(
                Arguments.of("€", "%E2%82%AC"),
                Arguments.of("中", "%E4%B8%AD"),
                Arguments.of("日本", "%E6%97%A5%E6%9C%AC"),
                Arguments.of("☃", "%E2%98%83"));
    }

    @ParameterizedTest
    @MethodSource("utf8FourByteProvider")
    void fourByteUtf8SurrogatePairsAreEncoded(String input, String expected) {
        assertThat(urlEncode(input), equalTo(expected));
    }

    static Stream<Arguments> utf8FourByteProvider() {
        return Stream.of(
                Arguments.of("🎉", "%F0%9F%8E%89"),
                Arguments.of("😀", "%F0%9F%98%80"),
                Arguments.of("𝄞", "%F0%9D%84%9E"),
                Arguments.of("hello🎉world", "hello%F0%9F%8E%89world"));
    }

    @Test
    void writeUrlEncodedWithEmptyString() {
        assertThat(urlEncode(""), equalTo(""));
    }

    @Test
    void writeUrlEncodedWithMixedContent() {
        assertThat(urlEncode("Hello World! café 日本 🎉"),
                equalTo("Hello%20World%21%20caf%C3%A9%20%E6%97%A5%E6%9C%AC%20%F0%9F%8E%89"));
    }

    @Test
    void hexEncodingUsesUppercase() {
        String result = urlEncode("ÿ");
        assertThat(result, equalTo("%C3%BF"));
        assertThat(result.contains("a") || result.contains("b")
                || result.contains("c")
                || result.contains("d")
                || result.contains("e")
                || result.contains("f"), equalTo(false));
    }

    @Test
    void unpairedHighSurrogateIsEncodedAsSingleCharacter() {
        String result = urlEncode("a\uD83Cb");
        assertThat(result, equalTo("a%ED%A0%BCb"));
    }

    @Test
    void highSurrogateFollowedByNonSurrogateEncodesEachSeparately() {
        String result = urlEncode("\uD83CX");
        assertThat(result, equalTo("%ED%A0%BCX"));
    }

    @Test
    void highSurrogateAtEndOfStringIsEncoded() {
        String result = urlEncode("test\uD83C");
        assertThat(result, equalTo("test%ED%A0%BC"));
    }

    @Test
    void lowSurrogateAloneIsEncoded() {
        String result = urlEncode("a\uDE89b");
        assertThat(result, equalTo("a%ED%BA%89b"));
    }
}
