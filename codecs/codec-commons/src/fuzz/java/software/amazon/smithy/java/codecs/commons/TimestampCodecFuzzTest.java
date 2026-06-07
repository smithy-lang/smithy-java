/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

class TimestampCodecFuzzTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final DateTimeFormatter HTTP_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZone(ZoneOffset.UTC);

    @FuzzTest
    void fuzzParseIso8601(byte[] input) {
        Instant smithyResult = assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                return TimestampCodec.parseIso8601(input, 0, input.length);
            } catch (Exception e) {
                throw new AssertionError("parseIso8601 threw on input length=" + input.length, e);
            }
        });

        String str = new String(input, StandardCharsets.US_ASCII);
        Instant jdkResult = null;
        try {
            jdkResult = Instant.parse(str);
        } catch (DateTimeParseException e) {
            // JDK couldn't parse it
        }

        if (smithyResult != null && jdkResult != null) {
            assertEquals(jdkResult, smithyResult, "parseIso8601 mismatch for: " + str);
        }
        if (smithyResult != null && jdkResult == null) {
            throw new AssertionError("parseIso8601 accepted input that JDK rejected: " + str);
        }
    }

    @FuzzTest
    void fuzzWriteIso8601RoundTrip(long epochSecond, int nano) {
        nano = Math.abs(nano % 1_000_000_000);
        if (epochSecond < -62167219200L || epochSecond > 253402300799L) {
            return;
        }
        Instant instant = Instant.ofEpochSecond(epochSecond, nano);

        byte[] buf = new byte[64];
        int endPos = assertTimeoutPreemptively(TIMEOUT, () -> TimestampCodec.writeIso8601(buf, 0, instant));
        String written = new String(buf, 0, endPos, StandardCharsets.US_ASCII);

        Instant parsed = Instant.parse(written);
        assertEquals(instant, parsed, "writeIso8601 round-trip mismatch for epoch=" + epochSecond + " nano=" + nano);

        Instant selfParsed = TimestampCodec.parseIso8601(buf, 0, endPos);
        assertNotNull(selfParsed, "parseIso8601 returned null for our own output: " + written);
        assertEquals(instant, selfParsed, "self round-trip mismatch for: " + written);
    }

    @FuzzTest
    void fuzzWriteHttpDateRoundTrip(long epochSecond) {
        // Start at year 0001 -- HTTP-date has no year 0 concept and JDK formatter disagrees with
        // proleptic Gregorian for year <= 0
        if (epochSecond < -62135596800L || epochSecond > 253402300799L) {
            return;
        }
        Instant instant = Instant.ofEpochSecond(epochSecond);

        byte[] buf = new byte[64];
        int endPos = assertTimeoutPreemptively(TIMEOUT, () -> TimestampCodec.writeHttpDate(buf, 0, instant));
        String written = new String(buf, 0, endPos, StandardCharsets.US_ASCII);

        String jdkFormatted = HTTP_FORMATTER.format(instant);
        assertEquals(jdkFormatted, written, "writeHttpDate mismatch for epoch=" + epochSecond);

        Instant selfParsed = TimestampCodec.parseHttpDate(buf, 0, endPos);
        assertNotNull(selfParsed, "parseHttpDate returned null for our own output: " + written);
        assertEquals(instant, selfParsed, "HTTP-date self round-trip mismatch for: " + written);
    }

    @FuzzTest
    void fuzzWriteEpochSecondsRoundTrip(long epochSecond, int nano) {
        nano = Math.abs(nano % 1_000_000_000);
        // Constrain to Instant's valid range so we can verify the full round-trip
        if (epochSecond < Instant.MIN.getEpochSecond() || epochSecond > Instant.MAX.getEpochSecond()) {
            return;
        }
        Instant instant = Instant.ofEpochSecond(epochSecond, nano);
        final long es = instant.getEpochSecond();
        final int n = instant.getNano();

        byte[] buf = new byte[64];
        int endPos = assertTimeoutPreemptively(TIMEOUT, () -> TimestampCodec.writeEpochSeconds(buf, 0, es, n));
        String written = new String(buf, 0, endPos, StandardCharsets.US_ASCII);

        Instant selfParsed = TimestampCodec.parseEpochSeconds(buf, 0, endPos);
        assertNotNull(selfParsed, "parseEpochSeconds returned null for our own output: " + written);
        assertEquals(instant, selfParsed, "epoch-seconds self round-trip mismatch for: " + written);
    }

    @FuzzTest
    void fuzzParseEpochSeconds(byte[] input) {
        Instant smithyResult = assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                return TimestampCodec.parseEpochSeconds(input, 0, input.length);
            } catch (Exception e) {
                throw new AssertionError("parseEpochSeconds threw on input length=" + input.length, e);
            }
        });

        // Differential: parse with JDK approach
        String str = new String(input, StandardCharsets.US_ASCII);
        Instant jdkResult = jdkParseEpochSeconds(str);

        if (smithyResult != null && jdkResult != null) {
            assertEquals(jdkResult, smithyResult, "parseEpochSeconds mismatch for: " + str);
        }
        if (smithyResult != null && jdkResult == null) {
            throw new AssertionError("parseEpochSeconds accepted input that JDK rejected: " + str);
        }
        if (smithyResult == null && jdkResult != null) {
            // Our parser is stricter than BigDecimal -- acceptable differences:
            boolean hasLeadingPlus = !str.isEmpty() && str.charAt(0) == '+';
            boolean hasWhitespace = !str.equals(str.trim());
            boolean missingIntegerPart = str.startsWith(".") || str.startsWith("-.");
            if (!hasLeadingPlus && !hasWhitespace && !missingIntegerPart) {
                throw new AssertionError("parseEpochSeconds rejected input that JDK accepted: " + str);
            }
        }

        // If we parsed it, verify round-trip
        if (smithyResult != null) {
            byte[] buf = new byte[64];
            int endPos = TimestampCodec.writeEpochSeconds(buf,
                    0,
                    smithyResult.getEpochSecond(),
                    smithyResult.getNano());
            Instant reparsed = TimestampCodec.parseEpochSeconds(buf, 0, endPos);
            assertNotNull(reparsed, "Re-parse failed for smithy output");
            assertEquals(smithyResult, reparsed, "epoch-seconds re-parse mismatch");
        }
    }

    private static Instant jdkParseEpochSeconds(String str) {
        try {
            BigDecimal epoch = new BigDecimal(str);
            if (Math.max(epoch.scale(), 0) > 9)
                return null;
            BigDecimal[] parts = epoch.divideAndRemainder(java.math.BigDecimal.ONE);
            long seconds = parts[0].longValueExact();
            long nanos = parts[1].movePointRight(9).longValueExact();
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (Exception e) {
            return null;
        }
    }

    @FuzzTest
    void fuzzParseHttpDate(byte[] input) {
        Instant smithyResult = assertTimeoutPreemptively(TIMEOUT, () -> {
            try {
                return TimestampCodec.parseHttpDate(input, 0, input.length);
            } catch (Exception e) {
                throw new AssertionError("parseHttpDate threw on input length=" + input.length, e);
            }
        });
        String str = new String(input, StandardCharsets.US_ASCII);
        Instant jdkResult = null;
        try {
            jdkResult = HTTP_FORMATTER.parse(str, Instant::from);
        } catch (Exception e) {
            // JDK couldn't parse it
        }

        if (smithyResult != null && jdkResult != null) {
            assertEquals(jdkResult, smithyResult, "parseHttpDate mismatch for: " + str);
        }
        if (smithyResult != null && jdkResult == null) {
            throw new AssertionError("parseHttpDate accepted input that JDK rejected: " + str);
        }
    }
}
