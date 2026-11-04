/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class BigNumbersBenchmark {

    private static final int OFFSET = 3;

    @State(Scope.Thread)
    public static class DecimalState {
        @Param({
                "1234567890.123456789",
                "-123.45",
                "99999.99999",
                "1E-25",
                "123456789012345678901234567890.5",
                "7x1000",
        })
        public String decimalText;
        String text;
        byte[] buf;
        int length;

        @Setup(Level.Trial)
        public void setup() {
            text = decimalText.equals("7x1000") ? "7".repeat(1000) : decimalText;
            byte[] ascii = text.getBytes(StandardCharsets.US_ASCII);
            buf = new byte[ascii.length + 2 * OFFSET];
            System.arraycopy(ascii, 0, buf, OFFSET, ascii.length);
            length = ascii.length;
        }
    }

    @State(Scope.Thread)
    public static class IntegerState {
        @Param({
                "12345",
                "123456789012345678901",
                "-123456789012345678901",
                "7x1000",
        })
        public String integerText;
        String text;
        byte[] buf;
        int length;

        @Setup(Level.Trial)
        public void setup() {
            text = integerText.equals("7x1000") ? "7".repeat(1000) : integerText;
            byte[] ascii = text.getBytes(StandardCharsets.US_ASCII);
            buf = new byte[ascii.length + 2 * OFFSET];
            System.arraycopy(ascii, 0, buf, OFFSET, ascii.length);
            length = ascii.length;
        }
    }

    @Benchmark
    public BigDecimal jdkParseBigDecimalBytes(DecimalState s) {
        return new BigDecimal(new String(s.buf, OFFSET, s.length, StandardCharsets.US_ASCII));
    }

    @Benchmark
    public BigDecimal smithyParseBigDecimalBytes(DecimalState s) {
        return BigNumbers.parseBigDecimal(s.buf, OFFSET, s.length);
    }

    @Benchmark
    public BigDecimal jdkParseBigDecimalString(DecimalState s) {
        return new BigDecimal(s.text);
    }

    @Benchmark
    public BigDecimal smithyParseBigDecimalString(DecimalState s) {
        return BigNumbers.parseBigDecimal(s.text);
    }

    @Benchmark
    public BigInteger jdkParseBigIntegerBytes(IntegerState s) {
        return new BigInteger(new String(s.buf, OFFSET, s.length, StandardCharsets.US_ASCII));
    }

    @Benchmark
    public BigInteger smithyParseBigIntegerBytes(IntegerState s) {
        return BigNumbers.parseBigInteger(s.buf, OFFSET, s.length);
    }
}
