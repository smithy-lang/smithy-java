/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class JsonWriteUtilsTest {

    private static final byte FILL = (byte) 0xEE;

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "plain ASCII 123", "\u007f"})
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
}
