/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class TimestampCodecBenchmark {

    private static final Instant TEST_INSTANT = Instant.parse("2025-06-15T14:30:45.123456789Z");
    private static final Instant TEST_INSTANT_WHOLE = Instant.ofEpochSecond(1750000000L);

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter HTTP_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZone(ZoneOffset.UTC);

    private byte[] iso8601Buf;
    private byte[] httpDateBuf;
    private byte[] epochSecondsBuf;
    private byte[] epochSecondsFracBuf;
    private byte[] writeBuf;

    @Setup(Level.Trial)
    public void setup() {
        iso8601Buf = "2025-06-15T14:30:45.123456789Z".getBytes(StandardCharsets.US_ASCII);
        httpDateBuf = "Sun, 15 Jun 2025 14:30:45 GMT".getBytes(StandardCharsets.US_ASCII);
        epochSecondsBuf = "1750000000".getBytes(StandardCharsets.US_ASCII);
        epochSecondsFracBuf = "1750000045.123456789".getBytes(StandardCharsets.US_ASCII);
        writeBuf = new byte[64];
    }

    // --- parseIso8601 ---

    @Benchmark
    public void jdkParseIso8601(Blackhole bh) {
        String s = new String(iso8601Buf, StandardCharsets.US_ASCII);
        bh.consume(Instant.parse(s));
    }

    @Benchmark
    public void smithyParseIso8601(Blackhole bh) {
        bh.consume(TimestampCodec.parseIso8601(iso8601Buf, 0, iso8601Buf.length));
    }

    @Benchmark
    public void jdkParseHttpDate(Blackhole bh) {
        String s = new String(httpDateBuf, StandardCharsets.US_ASCII);
        bh.consume(HTTP_FORMATTER.parse(s, Instant::from));
    }

    @Benchmark
    public void smithyParseHttpDate(Blackhole bh) {
        bh.consume(TimestampCodec.parseHttpDate(httpDateBuf, 0, httpDateBuf.length));
    }

    @Benchmark
    public void jdkParseEpochSeconds(Blackhole bh) {
        String s = new String(epochSecondsBuf, StandardCharsets.US_ASCII);
        bh.consume(Instant.ofEpochSecond(Long.parseLong(s)));
    }

    @Benchmark
    public void smithyParseEpochSeconds(Blackhole bh) {
        bh.consume(TimestampCodec.parseEpochSeconds(epochSecondsBuf, 0, epochSecondsBuf.length));
    }

    @Benchmark
    public void jdkParseEpochSecondsFrac(Blackhole bh) {
        String s = new String(epochSecondsFracBuf, StandardCharsets.US_ASCII);
        int dot = s.indexOf('.');
        long secs = Long.parseLong(s.substring(0, dot));
        String frac = s.substring(dot + 1);
        int nano = Integer.parseInt(frac) * (int) Math.pow(10, 9 - frac.length());
        bh.consume(Instant.ofEpochSecond(secs, nano));
    }

    @Benchmark
    public void smithyParseEpochSecondsFrac(Blackhole bh) {
        bh.consume(TimestampCodec.parseEpochSeconds(epochSecondsFracBuf, 0, epochSecondsFracBuf.length));
    }

    // --- writeIso8601 ---

    @Benchmark
    public void jdkWriteIso8601(Blackhole bh) {
        byte[] bytes = ISO_FORMATTER.format(TEST_INSTANT).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, writeBuf, 0, bytes.length);
        bh.consume(writeBuf);
    }

    @Benchmark
    public void smithyWriteIso8601(Blackhole bh) {
        bh.consume(TimestampCodec.writeIso8601(writeBuf, 0, TEST_INSTANT));
    }

    @Benchmark
    public void jdkWriteHttpDate(Blackhole bh) {
        byte[] bytes = HTTP_FORMATTER.format(TEST_INSTANT).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, writeBuf, 0, bytes.length);
        bh.consume(writeBuf);
    }

    @Benchmark
    public void smithyWriteHttpDate(Blackhole bh) {
        bh.consume(TimestampCodec.writeHttpDate(writeBuf, 0, TEST_INSTANT));
    }

    @Benchmark
    public void jdkWriteEpochSeconds(Blackhole bh) {
        Instant inst = TEST_INSTANT;
        String s;
        if (inst.getNano() == 0) {
            s = Long.toString(inst.getEpochSecond());
        } else {
            s = inst.getEpochSecond() + "." + String.format("%09d", inst.getNano()).replaceAll("0+$", "");
        }
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, writeBuf, 0, bytes.length);
        bh.consume(writeBuf);
    }

    @Benchmark
    public void smithyWriteEpochSeconds(Blackhole bh) {
        bh.consume(
                TimestampCodec.writeEpochSeconds(writeBuf, 0, TEST_INSTANT.getEpochSecond(), TEST_INSTANT.getNano()));
    }

    @Benchmark
    public void jdkWriteEpochSecondsWhole(Blackhole bh) {
        byte[] bytes = Long.toString(TEST_INSTANT_WHOLE.getEpochSecond()).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, writeBuf, 0, bytes.length);
        bh.consume(writeBuf);
    }

    @Benchmark
    public void smithyWriteEpochSecondsWhole(Blackhole bh) {
        bh.consume(TimestampCodec.writeEpochSeconds(writeBuf,
                0,
                TEST_INSTANT_WHOLE.getEpochSecond(),
                TEST_INSTANT_WHOLE.getNano()));
    }
}
