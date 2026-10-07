/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.json.JsonSettings;

class JsonNumberFuzzTest {
    @FuzzTest
    void fuzzDecimal(FuzzedDataProvider input) {
        String text = decimalText(input);
        byte[] bytes = ("[" + text + ",12345678]").getBytes(StandardCharsets.US_ASCII);
        var deser = new SmithyJsonDeserializer(bytes, 1, bytes.length, JsonSettings.builder().build());
        JsonReadUtils.parseDouble(bytes, 1, bytes.length, deser);
        assertEquals(text.length() + 1, deser.parsedEndPos);
        assertEquals(Double.doubleToRawLongBits(Double.parseDouble(text)),
                Double.doubleToRawLongBits(deser.parsedDouble),
                () -> text);
    }

    @FuzzTest
    void fuzzDecimalSlices(FuzzedDataProvider input) {
        int offset = input.consumeInt(0, 8);
        String text = decimalText(input);
        byte[] bytes = ("x".repeat(offset) + text + "99999999").getBytes(StandardCharsets.US_ASCII);
        int end = offset + text.length();
        var deser = new SmithyJsonDeserializer(bytes, offset, end, JsonSettings.builder().build());
        JsonReadUtils.parseDouble(bytes, offset, end, deser);
        assertEquals(end, deser.parsedEndPos);
        assertEquals(Double.doubleToRawLongBits(Double.parseDouble(text)),
                Double.doubleToRawLongBits(deser.parsedDouble),
                () -> text);
    }

    private static String decimalText(FuzzedDataProvider input) {
        StringBuilder text = new StringBuilder(input.consumeBoolean() ? "-" : "");
        text.append(input.consumeInt(0, 9));
        if (text.charAt(text.length() - 1) != '0') {
            int digits = input.consumeInt(0, 24);
            for (int i = 0; i < digits; i++) {
                text.append((char) input.consumeInt('0', '9'));
            }
        }
        if (input.consumeBoolean()) {
            text.append('.');
            int digits = input.consumeInt(1, 24);
            for (int i = 0; i < digits; i++) {
                text.append((char) input.consumeInt('0', '9'));
            }
        }
        if (input.consumeBoolean()) {
            text.append(input.consumeBoolean() ? 'e' : 'E');
            int exponent = input.consumeInt(-400, 400);
            if (exponent >= 0 && input.consumeBoolean()) {
                text.append('+');
            }
            text.append(exponent);
        }
        return text.toString();
    }
}
