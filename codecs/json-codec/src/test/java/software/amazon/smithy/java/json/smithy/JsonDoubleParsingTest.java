/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.json.JsonSettings;

class JsonDoubleParsingTest {

    private static double parse(String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        try (var deser = new SmithyJsonDeserializer(b, 0, b.length, JsonSettings.builder().build())) {
            return deser.readDouble(PreludeSchemas.DOUBLE);
        }
    }

    private static float parseFloat(String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        try (var deser = new SmithyJsonDeserializer(b, 0, b.length, JsonSettings.builder().build())) {
            return deser.readFloat(PreludeSchemas.FLOAT);
        }
    }

    private static void assertBitsEqual(String text) {
        double expected = Double.parseDouble(text);
        double actual = parse(text);
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual), text);
    }

    private static void assertFloatMatchesDoubleCast(String text) {
        float expected = (float) Double.parseDouble(text);
        float actual = parseFloat(text);
        assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-0",
            "0.0",
            "-0.0",
            "0.000",
            "1",
            "-1",
            "75",
            "101.125",
            "1.125",
            "3.14",
            "3.141592653589793",
            "0.30000000000000004",
            "0.1",
            "0.2",
            "0.3",
            "1e22",
            "1E22",
            "1e-22",
            "9007199254740992",
            "9007199254740993",
            "9007199254740993.0",
            "900719925474099.3",
            "90071992547409.93",
            "123456789012345678",
            "12345678901234567.8",
            "1234567890123456.78",
            "0.123456789012345678",
            "0.000000000000000001",
            "1.7976931348623157e308",
            "4.9e-324",
            "2.2250738585072014E-308",
            "1e23",
            "1e-23",
            "1e400",
            "1e-400",
            "1.5e3",
            "1.5E+3",
            "1.5e-3",
            "12345678.9",
            "0.5",
            "2.5",
            "1024.0",
            "1609459200",
            "1786755723.5"
    })
    void matchesJdkParseDouble(String text) {
        assertBitsEqual(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.",
            ".5",
            "+1",
            "1e5f",
            "1.5d",
            "0x1p3",
            "NaN",
            "Infinity",
            "-Infinity",
            "007",
            "00.5",
            "1e",
            "1e+",
            "1_0"
    })
    void rejectsNonJsonSyntax(String text) {
        assertThrows(SerializationException.class, () -> parse(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-0.0",
            "101.25",
            "1.125",
            "16777216",
            "16777217",
            "16777217.00000000000000001",
            "1.5e3",
            "0.1",
            "3.4028235e38",
            "1.4e-45",
            "1e10",
            "1e-10",
            "1e11",
            "1e-11",
            "12345.678"
    })
    void readFloatMatchesDoubleCast(String text) {
        assertFloatMatchesDoubleCast(text);
    }

    @Test
    void readFloatPreservesExistingDoubleRounding() {
        String text = "16777217.00000000000000001";
        // This pins the pre-existing readFloat double-rounding limitation: parse a double, then cast.
        // When readFloat gains correct float rounding, this assertion must use 16777218.0f.
        assertEquals(16777216.0f, parseFloat(text));
        assertEquals(16777218.0f, Float.parseFloat(text));
    }

    @Test
    @Tag("stress")
    void randomShortestReprDoublesRoundTrip() {
        Random random = new Random(20260918);
        for (int i = 0; i < 300_000; i++) {
            double value = Double.longBitsToDouble(random.nextLong());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                continue;
            }
            assertBitsEqual(Double.toString(value));
        }
    }

    @Test
    @Tag("stress")
    void randomDigitStringsMatchJdk() {
        Random random = new Random(42);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500_000; i++) {
            sb.setLength(0);
            if (random.nextBoolean()) {
                sb.append('-');
            }
            int intDigits = random.nextInt(19);
            if (intDigits == 0) {
                sb.append('0');
            } else {
                sb.append((char) ('1' + random.nextInt(9)));
                for (int k = 1; k < intDigits; k++) {
                    sb.append((char) ('0' + random.nextInt(10)));
                }
            }
            int fracDigits = random.nextInt(20);
            if (fracDigits > 0) {
                sb.append('.');
                for (int k = 0; k < fracDigits; k++) {
                    sb.append((char) ('0' + random.nextInt(10)));
                }
            }
            if (random.nextInt(4) == 0) {
                sb.append(random.nextBoolean() ? 'e' : 'E');
                int exp = random.nextInt(60) - 30;
                if (exp >= 0 && random.nextBoolean()) {
                    sb.append('+');
                }
                sb.append(exp);
            }
            assertBitsEqual(sb.toString());
        }
    }

    @Test
    @Tag("stress")
    void nearHalfwayDecimalsMatchJdk() {
        Random random = new Random(7);
        for (int i = 0; i < 100_000; i++) {
            double a = Double.longBitsToDouble(random.nextLong() & 0x7FFFFFFFFFFFFFFFL);
            if (Double.isNaN(a) || Double.isInfinite(a) || a == 0 || a < 1e-300 || a > 1e300) {
                continue;
            }
            double b = Math.nextUp(a);
            BigDecimal mid = new BigDecimal(a).add(new BigDecimal(b)).divide(BigDecimal.valueOf(2));
            for (int precision = 16; precision <= 18; precision++) {
                assertBitsEqual(mid.round(new MathContext(precision, RoundingMode.DOWN))
                        .toString());
                assertBitsEqual(mid.round(new MathContext(precision, RoundingMode.UP))
                        .toString());
            }
        }
    }

    @Test
    @Tag("stress")
    void randomFloatsMatchDoubleCast() {
        Random random = new Random(99);
        for (int i = 0; i < 300_000; i++) {
            float value = Float.intBitsToFloat(random.nextInt());
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                continue;
            }
            assertFloatMatchesDoubleCast(Float.toString(value));
        }
        DoubleSupplier small = () -> (random.nextInt(2_000_000) - 1_000_000) / 1000.0;
        for (int i = 0; i < 200_000; i++) {
            assertFloatMatchesDoubleCast(Double.toString(small.getAsDouble()));
        }
    }

}
