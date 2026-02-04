/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static software.amazon.smithy.java.io.ByteBufferUtils.getUTF8String;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FormUrlEncodedSinkTest {

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
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded(input);
        assertThat(getUTF8String(sink.finish()), equalTo(input));
    }

    @ParameterizedTest
    @MethodSource("reservedCharactersProvider")
    void reservedCharactersArePercentEncoded(String input, String expected) {
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded(input);
        assertThat(getUTF8String(sink.finish()), equalTo(expected));
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
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded(input);
        assertThat(getUTF8String(sink.finish()), equalTo(expected));
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
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded(input);
        assertThat(getUTF8String(sink.finish()), equalTo(expected));
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
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded(input);
        assertThat(getUTF8String(sink.finish()), equalTo(expected));
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
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded("");
        assertThat(getUTF8String(sink.finish()), equalTo(""));
    }

    @Test
    void writeUrlEncodedWithMixedContent() {
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded("Hello World! café 日本 🎉");
        assertThat(getUTF8String(sink.finish()),
                equalTo("Hello%20World%21%20caf%C3%A9%20%E6%97%A5%E6%9C%AC%20%F0%9F%8E%89"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 9, 10, 99, 100, 999, 1000, 12345, 999999, Integer.MAX_VALUE})
    void writeIntPositiveValues(int value) {
        var sink = new FormUrlEncodedSink();
        sink.writeInt(value);
        assertThat(getUTF8String(sink.finish()), equalTo(Integer.toString(value)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10, -999, Integer.MIN_VALUE})
    void writeIntNegativeValues(int value) {
        var sink = new FormUrlEncodedSink();
        sink.writeInt(value);
        assertThat(getUTF8String(sink.finish()), equalTo(Integer.toString(value)));
    }

    @Test
    void writeAsciiSimpleString() {
        var sink = new FormUrlEncodedSink();
        sink.writeAscii("Action=GetUser");
        assertThat(getUTF8String(sink.finish()), equalTo("Action=GetUser"));
    }

    @Test
    void writeAsciiEmptyString() {
        var sink = new FormUrlEncodedSink();
        sink.writeAscii("");
        assertThat(getUTF8String(sink.finish()), equalTo(""));
    }

    @Test
    void writeByteSingleByte() {
        var sink = new FormUrlEncodedSink();
        sink.writeByte('&');
        assertThat(getUTF8String(sink.finish()), equalTo("&"));
    }

    @Test
    void writeByteMultipleBytes() {
        var sink = new FormUrlEncodedSink();
        sink.writeByte('a');
        sink.writeByte('=');
        sink.writeByte('b');
        assertThat(getUTF8String(sink.finish()), equalTo("a=b"));
    }

    @Test
    void writeBytesFromArray() {
        var sink = new FormUrlEncodedSink();
        byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
        sink.writeBytes(data, 0, data.length);
        assertThat(getUTF8String(sink.finish()), equalTo("Hello"));
    }

    @Test
    void writeBytesWithOffset() {
        var sink = new FormUrlEncodedSink();
        byte[] data = "xxHelloxx".getBytes(StandardCharsets.UTF_8);
        sink.writeBytes(data, 2, 5);
        assertThat(getUTF8String(sink.finish()), equalTo("Hello"));
    }

    @Test
    void combineMultipleWriteOperations() {
        var sink = new FormUrlEncodedSink();
        sink.writeAscii("Action=Test");
        sink.writeByte('&');
        sink.writeAscii("Index=");
        sink.writeInt(42);
        sink.writeByte('&');
        sink.writeAscii("Name=");
        sink.writeUrlEncoded("hello world");
        assertThat(getUTF8String(sink.finish()), equalTo("Action=Test&Index=42&Name=hello%20world"));
    }

    @Test
    void bufferGrowsBeyondInitialCapacity() {
        var sink = new FormUrlEncodedSink(8);
        sink.writeAscii("This is a much longer string that exceeds the initial capacity");
        assertThat(getUTF8String(sink.finish()),
                equalTo("This is a much longer string that exceeds the initial capacity"));
    }

    @Test
    void bufferGrowsWithUrlEncodedContent() {
        var sink = new FormUrlEncodedSink(10);
        sink.writeUrlEncoded("Special chars: !@#$%^&*()");
        assertThat(getUTF8String(sink.finish()),
                equalTo("Special%20chars%3A%20%21%40%23%24%25%5E%26%2A%28%29"));
    }

    @Test
    void finishReturnsByteBufferWithCorrectPosition() {
        var sink = new FormUrlEncodedSink();
        sink.writeAscii("test");
        ByteBuffer result = sink.finish();
        assertThat(result.position(), equalTo(0));
        assertThat(result.remaining(), equalTo(4));
    }

    @Test
    void hexEncodingUsesUppercase() {
        var sink = new FormUrlEncodedSink();
        sink.writeUrlEncoded("ÿ");
        String result = getUTF8String(sink.finish());
        assertThat(result, equalTo("%C3%BF"));
        assertThat(result.contains("a") || result.contains("b")
                || result.contains("c")
                || result.contains("d")
                || result.contains("e")
                || result.contains("f"), equalTo(false));
    }

    @Test
    void unpairedHighSurrogateIsEncodedAsSingleCharacter() {
        var sink = new FormUrlEncodedSink();
        // High surrogate \uD83C without a following low surrogate
        sink.writeUrlEncoded("a\uD83Cb");
        String result = getUTF8String(sink.finish());
        // High surrogate encoded as 3-byte sequence, then 'b' passes through
        assertThat(result, equalTo("a%ED%A0%BCb"));
    }

    @Test
    void highSurrogateFollowedByNonSurrogateEncodesEachSeparately() {
        var sink = new FormUrlEncodedSink();
        // High surrogate \uD83C followed by regular char 'X' (not a low surrogate)
        sink.writeUrlEncoded("\uD83CX");
        String result = getUTF8String(sink.finish());
        // High surrogate encoded as 3-byte, then X passes through
        assertThat(result, equalTo("%ED%A0%BCX"));
    }

    @Test
    void highSurrogateAtEndOfStringIsEncoded() {
        var sink = new FormUrlEncodedSink();
        // High surrogate at end with no following character
        sink.writeUrlEncoded("test\uD83C");
        String result = getUTF8String(sink.finish());
        assertThat(result, equalTo("test%ED%A0%BC"));
    }

    @Test
    void lowSurrogateAloneIsEncoded() {
        var sink = new FormUrlEncodedSink();
        // Lone low surrogate (no preceding high surrogate)
        sink.writeUrlEncoded("a\uDE89b");
        String result = getUTF8String(sink.finish());
        assertThat(result, equalTo("a%ED%BA%89b"));
    }
}
