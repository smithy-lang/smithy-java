/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class CompactStringAccessTest {

    @Test
    public void usesJdkOptimizationsWithoutJvmFlags() {
        assertThat(CompactStringAccess.isAvailable()).isTrue();
        assertThat(CompactStringAccess.isCountPositivesIntrinsic()).isTrue();
        assertThat(CompactStringAccess.isCompactStrings()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "plain ASCII", "0123456789abcdef0123456789abcdef0123456789abcdef"})
    public void asciiStringDecodesProvenAsciiBytes(String value) {
        byte[] bytes = ("<" + value + ">").getBytes(StandardCharsets.US_ASCII);
        assertThat(CompactStringAccess.asciiString(bytes, 1, value.length())).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain ASCII", "café"})
    public void readsLatin1Bytes(String value) {
        assertThat(CompactStringAccess.latin1Bytes(value))
                .isEqualTo(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    public void rejectsUtf16Strings() {
        assertThat(CompactStringAccess.latin1Bytes("€")).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 8, 15, 16, 31, 32, 127, 128, 255, 256, 299})
    public void countPositivesReturnsLengthForAllAscii(int len) {
        byte[] bytes = new byte[len + 7];
        Arrays.fill(bytes, (byte) 'a');
        assertThat(CompactStringAccess.countPositives(bytes, 3, len)).isEqualTo(len);
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "8, 0", "8, 4", "8, 7", "31, 15", "128, 127", "299, 149"})
    public void countPositivesReturnsSafePrefix(int len, int negativeAt) {
        byte[] bytes = new byte[len + 5];
        Arrays.fill(bytes, (byte) 'z');
        bytes[2 + negativeAt] = (byte) 0xE9;
        assertThat(CompactStringAccess.countPositives(bytes, 2, len)).isBetween(0, negativeAt);
    }

    @Test
    public void countPositivesIgnoresBytesOutsideRange() {
        byte[] bytes = {(byte) 0x80, 'a', 'b', 'c', (byte) 0x80};
        assertThat(CompactStringAccess.countPositives(bytes, 1, 3)).isEqualTo(3);
        assertThat(CompactStringAccess.countPositives(bytes, 1, 0)).isEqualTo(0);
    }
}
