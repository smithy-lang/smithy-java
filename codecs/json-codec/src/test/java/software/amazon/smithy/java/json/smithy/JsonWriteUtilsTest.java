/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codecs.commons.CompactStringAccess;

public class JsonWriteUtilsTest {

    private static final byte FILL = (byte) 0xEE;
    private static final String TEST_MODE_PROPERTY = "smithy.java.test.compactStringAccessMode";

    @ParameterizedTest
    @MethodSource("asciiBoundaryStrings")
    public void acceptedStringFitsExactReservationAtAnyOffset(String value) {
        for (int pos = 0; pos <= 8; pos++) {
            var buf = new byte[pos + value.length() + 2];
            Arrays.fill(buf, FILL);

            int end = JsonWriteUtils.tryWriteQuotedAscii(buf, pos, value);

            assertThat(end).isEqualTo(buf.length);
            assertThat(new String(buf, pos, value.length() + 2, StandardCharsets.UTF_8))
                    .isEqualTo('"' + value + '"');
            for (int i = 0; i < pos; i++) {
                assertThat(buf[i]).isEqualTo(FILL);
            }
        }
    }

    @Test
    public void acceleratedWordMasksAcceptExactlyUnescapedAscii() {
        assumeTrue(System.getProperty(TEST_MODE_PROPERTY) == null);
        assumeTrue(CompactStringAccess.latin1Bytes("a") != null);

        byte[] bytes = new byte[Long.BYTES];
        Arrays.fill(bytes, (byte) 'a');
        for (int lane = 0; lane < bytes.length; lane++) {
            for (int current = 0; current <= 0xFF; current++) {
                bytes[lane] = (byte) current;
                String value = new String(bytes, StandardCharsets.ISO_8859_1);
                boolean expected = current >= 0x20
                        && current < 0x80
                        && current != '"'
                        && current != '\\';

                int end = JsonWriteUtils.tryWriteQuotedAscii(new byte[bytes.length + 2], 0, value);

                assertThat(end >= 0)
                        .as("byte 0x%02X in lane %d", current, lane)
                        .isEqualTo(expected);
            }
            bytes[lane] = 'a';
        }
    }

    @Test
    public void forcedInitializationFailureUsesFallback() {
        assumeTrue("fallback".equals(System.getProperty(TEST_MODE_PROPERTY)));

        assertThat(CompactStringAccess.isAvailable()).isFalse();
        assertThat(CompactStringAccess.latin1Bytes("ASCII")).isNull();
        assertRepresentativeStrings();
    }

    @Test
    public void compactStringsDisabledUsesFallback() {
        assumeTrue("compactStringsDisabled".equals(System.getProperty(TEST_MODE_PROPERTY)));

        assertThat(CompactStringAccess.isAvailable()).isTrue();
        assertThat(CompactStringAccess.latin1Bytes("")).isNull();
        assertThat(CompactStringAccess.latin1Bytes("ASCII")).isNull();
        assertThat(CompactStringAccess.latin1Bytes("caf\u00e9")).isNull();
        assertRepresentativeStrings();
    }

    @Test
    public void acceptsExactlySingleByteUnescapedCharacters() {
        for (char c = 0; c < 0x200; c++) {
            boolean expectAccept = c >= 0x20 && c < 0x80 && c != '"' && c != '\\';
            var buf = new byte[3];

            int end = JsonWriteUtils.tryWriteQuotedAscii(buf, 0, String.valueOf(c));

            assertThat(end >= 0)
                    .as("U+%04X should %s", (int) c, expectAccept ? "be accepted" : "be rejected")
                    .isEqualTo(expectAccept);
            if (expectAccept) {
                assertThat(end).isEqualTo(3);
                assertThat(buf[1]).isEqualTo((byte) c);
            }
        }
    }

    @Test
    public void rejectionStaysInsideExactReservation() {
        for (char bad : rejectingChars()) {
            String value = "abc" + bad;
            int pos = 5;
            var buf = new byte[pos + value.length() + 2];
            Arrays.fill(buf, FILL);

            int end = JsonWriteUtils.tryWriteQuotedAscii(buf, pos, value);

            assertThat(end).as("char U+%04X", (int) bad).isEqualTo(-1);
            for (int i = pos + value.length(); i < buf.length; i++) {
                assertThat(buf[i]).as("byte %d after rejection", i).isEqualTo(FILL);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("hostileStrings")
    public void generalPathRewritesFromOriginalPosition(String value, String expectedJson) {
        int pos = 5;
        var exact = new byte[pos + value.length() + 2];
        Arrays.fill(exact, FILL);
        assertThat(JsonWriteUtils.tryWriteQuotedAscii(exact, pos, value)).isEqualTo(-1);

        var widened = Arrays.copyOf(exact, pos + JsonWriteUtils.maxQuotedStringBytes(value));
        int end = JsonWriteUtils.writeQuotedStringGeneral(widened, pos, value);

        assertThat(new String(widened, pos, end - pos, StandardCharsets.UTF_8)).isEqualTo(expectedJson);
        assertThat(widened[pos - 1]).isEqualTo(FILL);
    }

    @ParameterizedTest
    @MethodSource("fieldNameTokens")
    public void encodesFieldNameToken(String fieldName, String expectedJson) {
        assertThat(new String(JsonWriteUtils.encodeFieldNameToken(fieldName), StandardCharsets.UTF_8))
                .isEqualTo(expectedJson);
    }

    static List<String> asciiBoundaryStrings() {
        return List.of(0, 3, 4, 7, 8, 15, 16, 17, 23, 24, 25, 80)
                .stream()
                .map(JsonWriteUtilsTest::ascii)
                .toList();
    }

    private static List<Character> rejectingChars() {
        return List.of(
                '"',
                '\\',
                '\n',
                '\0',
                '\u001f',
                '\u0080',
                '\u00e9',
                '\u20ac',
                '\uffff',
                '\ud83d');
    }

    static List<Arguments> hostileStrings() {
        return List.of(
                Arguments.of("\"", "\"\\\"\""),
                Arguments.of("\0", "\"\\u0000\""),
                Arguments.of("\u00e9", "\"\u00e9\""),
                Arguments.of("\ud83d\ude00", "\"\ud83d\ude00\""),
                Arguments.of("\ud83d", "\"\\ud83d\""),
                Arguments.of("abc\"", "\"abc\\\"\""),
                Arguments.of("a".repeat(200) + "\"", "\"" + "a".repeat(200) + "\\\"\""));
    }

    static List<Arguments> fieldNameTokens() {
        return List.of(
                Arguments.of("plain", "\"plain\":"),
                Arguments.of("\u0001", "\"\\u0001\":"));
    }

    private static void assertRepresentativeStrings() {
        assertWritten("", "\"\"");
        assertWritten("plain ASCII", "\"plain ASCII\"");
        assertWritten("caf\u00e9", "\"caf\u00e9\"");
        assertWritten("\u20ac", "\"\u20ac\"");
        assertWritten("a\"b", "\"a\\\"b\"");
        assertWritten("\ud83d\ude00", "\"\ud83d\ude00\"");
    }

    private static void assertWritten(String value, String expected) {
        byte[] buffer = new byte[JsonWriteUtils.maxQuotedStringBytes(value)];
        int end = JsonWriteUtils.writeQuotedString(buffer, 0, value);
        assertThat(new String(buffer, 0, end, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    private static String ascii(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return alphabet.repeat((length + alphabet.length() - 1) / alphabet.length()).substring(0, length);
    }
}
