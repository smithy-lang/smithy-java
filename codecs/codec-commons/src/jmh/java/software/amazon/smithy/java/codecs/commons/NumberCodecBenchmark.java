/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class NumberCodecBenchmark {

    private byte[] intBuf;
    private byte[] longBuf;
    private byte[] doubleBuf;
    private byte[] writeBuf;
    private static final int INT_VALUE = 1234567;
    private static final long LONG_VALUE = 123456789012345L;
    private static final double DOUBLE_VALUE = 3.141592653589793;

    @State(Scope.Thread)
    public static class DecimalDoubleState {
        @Param({
                "101.125",
                "0.5",
                "12345.678",
                "3.141592653589793",
                "0.30000000000000004",
                "1786755723.5",
                "-0.00125",
                "1.25e-100",
                "1.25e100"
        })
        public String decimalText;
        double value;
        byte[] writeBuf;

        @Setup(Level.Trial)
        public void setup() {
            value = Double.parseDouble(decimalText);
            if (value == (double) (long) value) {
                throw new IllegalArgumentException("Input takes NumberCodec's integer fast path: " + decimalText);
            }
            writeBuf = new byte[NumberCodec.DOUBLE_MAX_BYTES];
        }
    }

    @State(Scope.Thread)
    public static class DecimalFloatState {
        @Param({
                "101.125",
                "0.5",
                "12345.678",
                "3.141592653589793",
                "0.30000000000000004",
                "65536.5",
                "-0.00125",
                "1.25e-30",
                "1.25e30"
        })
        public String decimalText;
        float value;
        byte[] writeBuf;

        @Setup(Level.Trial)
        public void setup() {
            value = Float.parseFloat(decimalText);
            if (value == (float) (int) value) {
                throw new IllegalArgumentException("Input takes NumberCodec's integer fast path: " + decimalText);
            }
            writeBuf = new byte[NumberCodec.FLOAT_MAX_BYTES];
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        intBuf = Integer.toString(INT_VALUE).getBytes(StandardCharsets.US_ASCII);
        longBuf = Long.toString(LONG_VALUE).getBytes(StandardCharsets.US_ASCII);
        doubleBuf = Double.toString(DOUBLE_VALUE).getBytes(StandardCharsets.US_ASCII);
        writeBuf = new byte[32];
    }

    // --- parseInt: input is byte[], produce int ---

    @Benchmark
    public void jdkParseInt(Blackhole bh) {
        bh.consume(Integer.parseInt(new String(intBuf, 0, intBuf.length, StandardCharsets.US_ASCII)));
    }

    @Benchmark
    public void smithyParseInt(Blackhole bh) {
        bh.consume(NumberCodec.parseInt(intBuf, 0, intBuf.length));
    }

    @Benchmark
    public void jdkParseLong(Blackhole bh) {
        bh.consume(Long.parseLong(new String(longBuf, 0, longBuf.length, StandardCharsets.US_ASCII)));
    }

    @Benchmark
    public void smithyParseLong(Blackhole bh) {
        bh.consume(NumberCodec.parseLong(longBuf, 0, longBuf.length));
    }

    @Benchmark
    public void jdkParseDouble(Blackhole bh) {
        bh.consume(Double.parseDouble(new String(doubleBuf, 0, doubleBuf.length, StandardCharsets.US_ASCII)));
    }

    @Benchmark
    public void smithyParseDouble(Blackhole bh) {
        bh.consume(NumberCodec.parseDouble(doubleBuf, 0, doubleBuf.length));
    }

    @Benchmark
    public void schubfachWriteDecimalDouble(DecimalDoubleState state, Blackhole bh) {
        bh.consume(Schubfach.writeDouble(state.writeBuf, 0, state.value));
    }

    @Benchmark
    public void zmijWriteDecimalDouble(DecimalDoubleState state, Blackhole bh) {
        bh.consume(Zmij.writeDouble(state.writeBuf, 0, state.value));
    }

    @Benchmark
    public void schubfachWriteDecimalFloat(DecimalFloatState state, Blackhole bh) {
        bh.consume(Schubfach.writeFloat(state.writeBuf, 0, state.value));
    }

    @Benchmark
    public void zmijWriteDecimalFloat(DecimalFloatState state, Blackhole bh) {
        bh.consume(Zmij.writeFloat(state.writeBuf, 0, state.value));
    }

    @Benchmark
    public void smithyWriteDecimalDouble(DecimalDoubleState state, Blackhole bh) {
        bh.consume(NumberCodec.writeDouble(state.writeBuf, 0, state.value));
    }

    @Benchmark
    public void smithyWriteDecimalFloat(DecimalFloatState state, Blackhole bh) {
        bh.consume(NumberCodec.writeFloat(state.writeBuf, 0, state.value));
    }

    @Benchmark
    public void jdkWriteInt(Blackhole bh) {
        byte[] bytes = Integer.toString(INT_VALUE).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, writeBuf, 0, bytes.length);
        bh.consume(writeBuf);
    }

    @Benchmark
    public void smithyWriteInt(Blackhole bh) {
        bh.consume(NumberCodec.writeInt(writeBuf, 0, INT_VALUE));
    }

    @Benchmark
    public void jdkWriteLong(Blackhole bh) {
        byte[] bytes = Long.toString(LONG_VALUE).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, writeBuf, 0, bytes.length);
        bh.consume(writeBuf);
    }

    @Benchmark
    public void smithyWriteLong(Blackhole bh) {
        bh.consume(NumberCodec.writeLong(writeBuf, 0, LONG_VALUE));
    }

    @Benchmark
    public void jdkWriteDouble(Blackhole bh) {
        byte[] bytes = Double.toString(DOUBLE_VALUE).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, writeBuf, 0, bytes.length);
        bh.consume(writeBuf);
    }

    @Benchmark
    public void smithyWriteDouble(Blackhole bh) {
        bh.consume(NumberCodec.writeDouble(writeBuf, 0, DOUBLE_VALUE));
    }
}
