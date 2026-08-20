/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class JsonReadUtilsTest {

    private static final byte FILL = (byte) 0xEE;

    @Test
    public void weightsEveryDigitPosition() {
        var base = "1234567890".toCharArray();
        for (int i = 0; i < 10; i++) {
            for (char d = i == 0 ? '1' : '0'; d <= '9'; d++) {
                var chars = base.clone();
                chars[i] = d;
                var value = new String(chars);
                assertThat(parse(value)).as("%s (digit %c at %d)", value, d, i).isEqualTo(Long.parseLong(value));
            }
        }
    }

    @Test
    public void parsesTheRangeEndpoints() {
        assertThat(parse("1000000000")).isEqualTo(1_000_000_000L);
        assertThat(parse("9999999999")).isEqualTo(9_999_999_999L);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "123456789",
                    "1",
                    "0",
                    "0123456789",
                    "12345678901",
                    "1234567890123456789",
                    "-123456789",
                    "-1234567890",
                    "1234567890.5",
                    "1234567890.0",
                    "1234567890e2",
                    "1234567890E2",
                    "1234567890e-2"
            })
    public void declinesEverythingThatIsNotTenWholeDigits(String value) {
        assertThat(parse(value)).isEqualTo(-1);
    }

    @Test
    public void rejectsEveryNonDigitByteAtEveryPosition() {
        var valid = "1234567890".getBytes(StandardCharsets.US_ASCII);
        for (int pos = 0; pos < valid.length; pos++) {
            for (int candidate = 0; candidate <= 0xFF; candidate++) {
                if (candidate >= '0' && candidate <= '9') {
                    continue;
                }
                var value = valid.clone();
                value[pos] = (byte) candidate;
                assertThat(JsonReadUtils.tryParseTenDigitEpochSecond(value, 0, value.length))
                        .as("byte 0x%02X at position %d", candidate, pos)
                        .isEqualTo(-1);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(chars = {',', '}', ']', ' ', '\t', '\n', '\r'})
    public void acceptsValidNumberTerminators(char terminator) {
        assertThat(parse("1712345678" + terminator)).isEqualTo(1_712_345_678L);
    }

    @Test
    public void parsesAtAnyOffset() {
        for (int pos = 0; pos <= 9; pos++) {
            var buf = new byte[pos + 10 + 4];
            Arrays.fill(buf, FILL);
            System.arraycopy("1712345678".getBytes(StandardCharsets.US_ASCII), 0, buf, pos, 10);

            assertThat(JsonReadUtils.tryParseTenDigitEpochSecond(buf, pos, pos + 10))
                    .as("pos=%d", pos)
                    .isEqualTo(1_712_345_678L);
        }
    }

    @Test
    public void respectsEndRatherThanTheArrayLength() {
        var buf = "17123456789999".getBytes(StandardCharsets.US_ASCII);

        for (int end = 0; end < 10; end++) {
            assertThat(JsonReadUtils.tryParseTenDigitEpochSecond(buf, 0, end)).as("end=%d", end).isEqualTo(-1);
        }
        assertThat(JsonReadUtils.tryParseTenDigitEpochSecond(buf, 0, 10)).isEqualTo(1_712_345_678L);
        assertThat(JsonReadUtils.tryParseTenDigitEpochSecond(buf, 0, 11)).isEqualTo(-1);
    }

    @Test
    public void doesNotReadPastTheEndOfTheArray() {
        var exact = "1712345678".getBytes(StandardCharsets.US_ASCII);
        assertThat(exact).hasSize(10);
        assertThat(JsonReadUtils.tryParseTenDigitEpochSecond(exact, 0, exact.length)).isEqualTo(1_712_345_678L);

        for (int len = 0; len < 10; len++) {
            var shortBuf = Arrays.copyOf(exact, len);
            assertThat(JsonReadUtils.tryParseTenDigitEpochSecond(shortBuf, 0, len)).as("len=%d", len).isEqualTo(-1);
        }
    }

    private static long parse(String value) {
        var bytes = value.getBytes(StandardCharsets.US_ASCII);
        return JsonReadUtils.tryParseTenDigitEpochSecond(bytes, 0, bytes.length);
    }
}
