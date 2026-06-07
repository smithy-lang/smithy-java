/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
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
