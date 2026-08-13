/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class JsonReadUtilsTest {

    @Test
    public void parsesTenDigitEpochSeconds() {
        assertThat(parse("1000000000")).isEqualTo(1_000_000_000L);
        assertThat(parse("1712345678,")).isEqualTo(1_712_345_678L);
        assertThat(parse("9999999999}")).isEqualTo(9_999_999_999L);
    }

    @Test
    public void rejectsOtherNumericRepresentations() {
        assertThat(parse("0123456789")).isEqualTo(-1);
        assertThat(parse("123456789")).isEqualTo(-1);
        assertThat(parse("12345678901")).isEqualTo(-1);
        assertThat(parse("-123456789")).isEqualTo(-1);
        assertThat(parse("1234567890.5")).isEqualTo(-1);
        assertThat(parse("1234567890e2")).isEqualTo(-1);
        assertThat(parse("123456789x")).isEqualTo(-1);
    }

    private static long parse(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        return JsonReadUtils.tryParseTenDigitEpochSecond(bytes, 0, bytes.length);
    }
}
