/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HeaderUtilsPropertyTest {

    @Test
    void normalizeValueMatchesByteByByteReference() {
        var random = new Random(9);
        for (int i = 0; i < 200_000; i++) {
            String value = i % 10 == 0 ? randomUtf16(random) : randomLatin1(random);
            assertNormalizeValue(value);
        }
    }

    @Test
    void everyInvalidByteIsRejectedInEveryLane() {
        for (int lane = 0; lane < 24; lane++) {
            for (int c = 0; c < 256; c++) {
                byte[] bytes = new byte[24];
                Arrays.fill(bytes, (byte) 'a');
                bytes[lane] = (byte) c;
                assertNormalizeValue(new String(bytes, StandardCharsets.ISO_8859_1));
            }
        }
    }

    static void assertNormalizeValue(String value) {
        String expected;
        try {
            expected = reference(value);
        } catch (IllegalArgumentException e) {
            expected = null;
        }
        String got;
        try {
            got = HeaderUtils.normalizeValue(value);
        } catch (IllegalArgumentException e) {
            got = null;
        }
        assertThat(got).as("value %s", escape(value)).isEqualTo(expected);
        if (expected != null && expected.equals(value)) {
            assertThat(got).as("unchanged value must be returned by identity").isSameAs(value);
        }
    }

    static String reference(String value) {
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c > 0xFF || (c < 0x20 && c != '\t') || c == 0x7F) {
                throw new IllegalArgumentException("Invalid header value: " + value);
            }
        }
        int start = 0;
        while (start < length && (value.charAt(start) == ' ' || value.charAt(start) == '\t')) {
            start++;
        }
        int end = length;
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\t')) {
            end--;
        }
        return start == 0 && end == length ? value : value.substring(start, end);
    }

    private static String randomLatin1(Random random) {
        int length = random.nextInt(41);
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            int k = random.nextInt(100);
            int c;
            if (k < 55) {
                c = 0x21 + random.nextInt(0x5E);
            } else if (k < 70) {
                c = ' ';
            } else if (k < 80) {
                c = '\t';
            } else if (k < 88) {
                c = random.nextInt(0x20);
            } else if (k < 91) {
                c = 0x7F;
            } else {
                c = 0x80 + random.nextInt(0x80);
            }
            bytes[i] = (byte) c;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static String randomUtf16(Random random) {
        int length = 1 + random.nextInt(40);
        var sb = new StringBuilder(length + 1);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(4) == 0 ? (char) (0x100 + random.nextInt(0x1000))
                    : (char) (0x20 + random.nextInt(0x5F)));
        }
        if (sb.chars().allMatch(c -> c < 0x100)) {
            sb.append('Ā');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        var sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c >= 0x7F) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
