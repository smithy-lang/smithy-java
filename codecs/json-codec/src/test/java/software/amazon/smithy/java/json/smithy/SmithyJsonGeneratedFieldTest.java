/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.json.JsonSettings;

final class SmithyJsonGeneratedFieldTest {
    @Test
    void nextFieldFastPathsAcceptWhitespaceAfterComma() {
        byte[] source = """
                {"a":1,
                  "mediumName":2,\t "aFieldNameLongerThan16":3}
                """.getBytes(StandardCharsets.UTF_8);
        var reader = new SmithyJsonDeserializer(source, 0, source.length, JsonSettings.builder().build());
        byte[] shortToken = token("a");
        byte[] mediumToken = token("mediumName");
        byte[] longToken = token("aFieldNameLongerThan16");

        try {
            assertThat(reader.generatedBeginObject()).isTrue();
            assertThat(reader.generatedTryReadField8(
                    pack(token("wrong"), 0, token("wrong").length),
                    mask(token("wrong").length),
                    token("wrong").length))
                    .isFalse();
            assertThat(reader.generatedTryReadField8(
                    pack(shortToken, 0, shortToken.length),
                    mask(shortToken.length),
                    shortToken.length))
                    .isTrue();
            assertThat(reader.generatedReadInteger()).isEqualTo(1);
            assertThat(reader.generatedTryReadNextField16(
                    pack(mediumToken, 0, Long.BYTES),
                    pack(mediumToken, Long.BYTES, mediumToken.length - Long.BYTES),
                    mask(mediumToken.length - Long.BYTES),
                    mediumToken.length))
                    .isTrue();
            assertThat(reader.generatedReadInteger()).isEqualTo(2);
            assertThat(reader.generatedTryReadNextField(longToken)).isTrue();
            assertThat(reader.generatedReadInteger()).isEqualTo(3);
            assertThat(reader.generatedObjectHasNext()).isFalse();
        } finally {
            reader.close();
        }
    }

    private static byte[] token(String name) {
        return ('"' + name + "\":").getBytes(StandardCharsets.UTF_8);
    }

    private static long pack(byte[] value, int offset, int length) {
        long result = 0;
        for (int i = 0; i < length; i++) {
            result |= (long) (value[offset + i] & 0xff) << (i << 3);
        }
        return result;
    }

    private static long mask(int length) {
        return length == Long.BYTES ? -1L : (1L << (length << 3)) - 1;
    }
}
