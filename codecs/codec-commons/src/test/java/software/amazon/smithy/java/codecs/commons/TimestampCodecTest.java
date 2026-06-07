/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TimestampCodecTest {

    private static final DateTimeFormatter HTTP_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZone(ZoneOffset.UTC);

    private static Instant parse8601(String s) {
        byte[] buf = s.getBytes(StandardCharsets.US_ASCII);
        return TimestampCodec.parseIso8601(buf, 0, buf.length);
    }

    private static Instant parseHttp(String s) {
        byte[] buf = s.getBytes(StandardCharsets.US_ASCII);
        return TimestampCodec.parseHttpDate(buf, 0, buf.length);
    }

    private static Instant parseEpoch(String s) {
        byte[] buf = s.getBytes(StandardCharsets.US_ASCII);
        return TimestampCodec.parseEpochSeconds(buf, 0, buf.length);
    }

    private static Instant jdkParseEpochSeconds(String s) {
        try {
            BigDecimal epoch = new BigDecimal(s);
            if (Math.max(epoch.scale(), 0) > 9) {
                return null;
            }
            BigDecimal[] parts = epoch.divideAndRemainder(BigDecimal.ONE);
            long seconds = parts[0].longValueExact();
            long nanos = parts[1].movePointRight(9).longValueExact();
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (Exception e) {
            return null;
        }
    }

    @Nested
    class ParseIso8601 {

        @ParameterizedTest
        @ValueSource(strings = {
                "2024-01-15T10:30:00Z",
                "2024-01-15T10:30:00.1Z",
                "2024-01-15T10:30:00.123Z",
                "2024-01-15T10:30:00.123456789Z",
                // Midnight and end of day
                "2024-01-01T00:00:00Z",
                "2024-12-31T23:59:59Z",
                // Leap year Feb 29
                "2024-02-29T00:00:00Z",
                // Non-leap year Feb 28
                "2023-02-28T12:00:00Z",
                // Year boundaries
                "0001-01-01T00:00:00Z",
                "9999-12-31T23:59:59Z",
                // Century-divisible non-leap year
                "1900-02-28T00:00:00Z",
                // Century-divisible leap year (400 rule)
                "2000-02-29T00:00:00Z",
                // Single fractional digit
                "2024-01-01T00:00:00.5Z",
                // 9 fractional digits (max allowed)
                "2024-01-01T00:00:00.000000001Z",
                // Various time boundaries that kill hour/min/sec conditional mutations
                "2024-01-01T23:59:59Z",
                "2024-01-01T00:00:01Z",
                "2024-01-01T01:01:01Z",
                // Month 12, day 31 (boundary for month > 12 check)
                "2024-12-01T00:00:00Z",
                // Month 01 (boundary for month < 1 check)
                "2024-01-31T00:00:00Z",
                // Day 01 (boundary for day < 1 check)
                "2024-06-01T00:00:00Z",
                // Before epoch with negative year math in computeEpochDay
                "0100-01-01T00:00:00Z"
        })
        void parsesValid(String input) {
            Instant expected = Instant.parse(input);
            assertEquals(expected, parse8601(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                // Too short
                "2024-01-15T10:30:0Z",
                // Missing Z
                "2024-01-15T10:30:00",
                // Trailing garbage after Z
                "2024-01-15T10:30:00Zx",
                // Invalid month
                "2024-13-15T10:30:00Z",
                "2024-00-15T10:30:00Z",
                // Invalid day
                "2024-01-32T10:30:00Z",
                "2024-01-00T10:30:00Z",
                // Feb 29 in non-leap year
                "2023-02-29T00:00:00Z",
                // Feb 29 in century non-leap year
                "1900-02-29T00:00:00Z",
                // Invalid hour/min/sec
                "2024-01-15T24:00:00Z",
                "2024-01-15T10:60:00Z",
                "2024-01-15T10:30:60Z",
                // Bad separators
                "2024/01/15T10:30:00Z",
                "2024-01-15 10:30:00Z",
                // Empty fractional
                "2024-01-15T10:30:00.Z",
                // Over 9 fractional digits
                "2024-01-15T10:30:00.1234567890Z",
                // Non-digit in date (year position 0)
                "x024-01-15T10:30:00Z",
                // Non-digit in month
                "2024-x1-15T10:30:00Z",
                // Non-digit in day
                "2024-01-x5T10:30:00Z",
                // Non-digit in hour
                "2024-01-15Tx0:30:00Z",
                // Non-digit in minute
                "2024-01-15T10:x0:00Z",
                // Non-digit in second
                "2024-01-15T10:30:x0Z",
                // Apr 31 (30-day month boundary)
                "2024-04-31T00:00:00Z",
                // Jun 31
                "2024-06-31T00:00:00Z"
        })
        void rejectsInvalid(String input) {
            assertNull(parse8601(input));
        }
    }

    @Nested
    class ParseHttpDate {

        @ParameterizedTest
        @ValueSource(strings = {
                "Mon, 01 Jan 2024 12:00:00 GMT",
                "Thu, 29 Feb 2024 00:00:00 GMT",
                "Sat, 01 Jan 2000 00:00:00 GMT",
                "Sun, 31 Dec 2023 23:59:59 GMT",
                // All month names
                "Wed, 15 Jan 2025 00:00:00 GMT",
                "Sat, 15 Feb 2025 00:00:00 GMT",
                "Sat, 15 Mar 2025 00:00:00 GMT",
                "Tue, 15 Apr 2025 00:00:00 GMT",
                "Thu, 15 May 2025 00:00:00 GMT",
                "Sun, 15 Jun 2025 00:00:00 GMT",
                "Tue, 15 Jul 2025 00:00:00 GMT",
                "Fri, 15 Aug 2025 00:00:00 GMT",
                "Mon, 15 Sep 2025 00:00:00 GMT",
                "Wed, 15 Oct 2025 00:00:00 GMT",
                "Sat, 15 Nov 2025 00:00:00 GMT",
                "Mon, 15 Dec 2025 00:00:00 GMT",
                // Boundary time values
                "Mon, 01 Jan 2024 00:00:00 GMT",
                "Mon, 01 Jan 2024 23:59:59 GMT"
        })
        void parsesValid(String input) {
            Instant expected = HTTP_FORMATTER.parse(input, Instant::from);
            assertEquals(expected, parseHttp(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                // Too short
                "Mon, 01 Jan 2024 12:00:00 GM",
                // Bad month -- 'J' first char, 'a' second but wrong third (not 'n')
                "Mon, 01 Jax 2024 12:00:00 GMT",
                // Bad month -- 'J' first char, 'u' second but wrong third (not 'n' or 'l')
                "Mon, 01 Jux 2024 12:00:00 GMT",
                // Bad month -- 'J' first char, wrong second char entirely
                "Mon, 01 Jxx 2024 12:00:00 GMT",
                // Bad month -- 'F' first char but wrong combo
                "Mon, 01 Fxx 2024 12:00:00 GMT",
                // Bad month -- 'M' first char, 'a' second but wrong third (not 'r' or 'y')
                "Mon, 01 Max 2024 12:00:00 GMT",
                // Bad month -- 'M' first char, wrong second char
                "Mon, 01 Mxx 2024 12:00:00 GMT",
                // Bad month -- 'A' first char, 'p' second but wrong third
                "Mon, 01 Apx 2024 12:00:00 GMT",
                // Bad month -- 'A' first char, 'u' second but wrong third
                "Mon, 01 Aux 2024 12:00:00 GMT",
                // Bad month -- 'A' first char, wrong second char
                "Mon, 01 Axx 2024 12:00:00 GMT",
                // Bad month -- 'S' first char but wrong combo
                "Mon, 01 Sxx 2024 12:00:00 GMT",
                // Bad month -- 'O' first char but wrong combo
                "Mon, 01 Oxx 2024 12:00:00 GMT",
                // Bad month -- 'N' first char but wrong combo
                "Mon, 01 Nxx 2024 12:00:00 GMT",
                // Bad month -- 'D' first char but wrong combo
                "Mon, 01 Dxx 2024 12:00:00 GMT",
                // Bad month -- wrong first char entirely
                "Mon, 01 Xxx 2024 12:00:00 GMT",
                // Missing comma
                "Mon  01 Jan 2024 12:00:00 GMT",
                // Invalid day (Feb 29 non-leap)
                "Wed, 29 Feb 2023 00:00:00 GMT",
                // Invalid time
                "Mon, 01 Jan 2024 25:00:00 GMT",
                "Mon, 01 Jan 2024 12:60:00 GMT",
                "Mon, 01 Jan 2024 12:00:60 GMT",
                // Not GMT
                "Mon, 01 Jan 2024 12:00:00 UTC",
                // Day 00
                "Mon, 00 Jan 2024 12:00:00 GMT",
                // Non-digit in day
                "Mon, x1 Jan 2024 12:00:00 GMT",
                // Non-digit in year
                "Mon, 01 Jan x024 12:00:00 GMT",
                // Non-digit in hour
                "Mon, 01 Jan 2024 x2:00:00 GMT",
                // Non-digit in minute
                "Mon, 01 Jan 2024 12:x0:00 GMT",
                // Non-digit in second
                "Mon, 01 Jan 2024 12:00:x0 GMT"
        })
        void rejectsInvalid(String input) {
            assertNull(parseHttp(input));
        }
    }

    @Nested
    class ParseEpochSeconds {

        @ParameterizedTest
        @ValueSource(strings = {
                "0",
                "1",
                "1234567890",
                // Negative
                "-1",
                "-62135596800",
                // Fractional seconds
                "1.5",
                "1.001",
                "1.000000001",
                "0.1",
                // Negative with fraction
                "-1.5",
                "-0.001",
                // Scientific notation -- positive exponent
                "1e0",
                "1e3",
                "1E3",
                // Scientific notation -- negative exponent (fractional result)
                "15e-1",
                // Scientific notation -- with decimal point
                "1.5e9",
                "1.5e3",
                // Scientific notation -- explicit + sign on exponent
                "1e+3",
                // Negative with scientific notation
                "-1.5e3",
                "-15e-1",
                // Large mantissa with exponent
                "123456789012345678e-8",
                // Fractional with varying digit counts
                "1.1e1",
                "1.12e2",
                "1.123e3",
                // Exponent that exactly cancels fractional
                "1.5e0",
                // fracDigits exactly 9 (boundary for fracDigits > 9 rejection)
                "1e-9",
                // Shift=0 produces integer from sci notation with decimal
                "1.5e1",
                // Negative with sci notation and fractional nanos
                "-1e0",
                "-1.5e0",
                // Exponent with explicit + sign and multi-digit exponent
                "1e+10",
                // Integer-only mantissa with negative exponent
                "123e-2",
                "1234567e-7"
        })
        void parsesValid(String input) {
            Instant expected = jdkParseEpochSeconds(input);
            assertNotNull(expected, "JDK reference failed to parse: " + input);
            assertEquals(expected, parseEpoch(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "-",
                "abc",
                // Overflow
                "99999999999999999999999",
                // More than 9 fractional digits (non-exponent)
                "1.0000000001",
                // Exponent too large
                "1e21",
                // Trailing garbage
                "123abc",
                // Just exponent sign, no digits
                "1e",
                "1e+",
                "1e-",
                // Negative with nothing after
                "-e1"
        })
        void rejectsInvalid(String input) {
            assertNull(parseEpoch(input));
        }
    }

    @Nested
    class WriteEpochSeconds {

        static Stream<Arguments> cases() {
            return Stream.of(
                    Instant.EPOCH,
                    Instant.ofEpochSecond(1),
                    Instant.ofEpochSecond(-1),
                    Instant.ofEpochSecond(1234567890),
                    // With nanos -- various trailing-zero patterns
                    Instant.ofEpochSecond(1, 500000000),
                    Instant.ofEpochSecond(1, 100000000),
                    Instant.ofEpochSecond(1, 1),
                    Instant.ofEpochSecond(1, 123456789),
                    Instant.ofEpochSecond(1, 120000000),
                    Instant.ofEpochSecond(0, 100000000),
                    // Negative with nanos -- exercises the epochSecond+1 / 10^9-nano path
                    Instant.ofEpochSecond(-1, 500000000),
                    Instant.ofEpochSecond(-2, 500000000),
                    Instant.ofEpochSecond(-1, 999000000),
                    Instant.ofEpochSecond(-1, 1),
                    // Negative zero seconds with nano (writes "-0.xxx")
                    Instant.ofEpochSecond(-1, 999999999))
                    .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("cases")
        void writeThenParseMatchesOriginal(Instant instant) {
            byte[] buf = new byte[64];
            int end = TimestampCodec.writeEpochSeconds(buf,
                    0,
                    instant.getEpochSecond(),
                    instant.getNano());
            Instant reparsed = TimestampCodec.parseEpochSeconds(buf, 0, end);
            assertEquals(instant, reparsed);
        }
    }

    @Nested
    class WriteIso8601 {

        static Stream<Arguments> cases() {
            return Stream.of(
                    Instant.parse("2024-01-15T10:30:00Z"),
                    Instant.parse("2024-01-15T10:30:00.1Z"),
                    Instant.parse("2024-01-15T10:30:00.123Z"),
                    Instant.parse("2024-01-15T10:30:00.123456789Z"),
                    Instant.EPOCH,
                    // Before epoch (exercises negative epochDay in civil date conversion)
                    Instant.parse("1969-12-31T23:59:59Z"),
                    // Trailing zeros stripped from fractional
                    Instant.parse("2024-01-01T00:00:00.120Z"),
                    // Year 0100 -- exercises early-year civil date conversion
                    Instant.parse("0100-03-01T12:00:00Z"))
                    .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("cases")
        void writeMatchesJdkAndRoundTrips(Instant instant) {
            byte[] buf = new byte[64];
            int end = TimestampCodec.writeIso8601(buf, 0, instant);
            String written = new String(buf, 0, end, StandardCharsets.US_ASCII);

            assertEquals(instant, Instant.parse(written));

            Instant selfParsed = TimestampCodec.parseIso8601(buf, 0, end);
            if (selfParsed != null) {
                assertEquals(instant, selfParsed);
            }
        }
    }

    @Nested
    class WriteHttpDate {

        static Stream<Arguments> cases() {
            return Stream.of(
                    Instant.parse("2024-01-01T12:00:00Z"),
                    Instant.parse("2024-02-29T00:00:00Z"),
                    Instant.EPOCH,
                    Instant.parse("2023-12-31T23:59:59Z"),
                    // Before epoch (exercises negative epochDay in dow/civil date calc)
                    Instant.parse("1969-06-15T08:30:00Z"))
                    .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("cases")
        void writeMatchesJdkFormatter(Instant instant) {
            byte[] buf = new byte[64];
            int end = TimestampCodec.writeHttpDate(buf, 0, instant);
            String written = new String(buf, 0, end, StandardCharsets.US_ASCII);

            String expected = HTTP_FORMATTER.format(instant);
            assertEquals(expected, written);

            Instant selfParsed = TimestampCodec.parseHttpDate(buf, 0, end);
            assertEquals(instant, selfParsed);
        }
    }

    @Nested
    class RoundTrips {

        @Test
        void iso8601RoundTrip() {
            Instant original = Instant.parse("2024-06-15T14:30:45.123456789Z");
            byte[] buf = new byte[64];
            int end = TimestampCodec.writeIso8601(buf, 0, original);
            Instant parsed = TimestampCodec.parseIso8601(buf, 0, end);
            assertEquals(original, parsed);
        }

        @Test
        void httpDateRoundTrip() {
            Instant original = Instant.parse("2024-06-15T14:30:45Z");
            byte[] buf = new byte[64];
            int end = TimestampCodec.writeHttpDate(buf, 0, original);
            Instant parsed = TimestampCodec.parseHttpDate(buf, 0, end);
            assertEquals(original, parsed);
        }

        @Test
        void epochSecondsRoundTrip() {
            Instant original = Instant.ofEpochSecond(1718458245L, 123456789);
            byte[] buf = new byte[64];
            int end = TimestampCodec.writeEpochSeconds(buf,
                    0,
                    original.getEpochSecond(),
                    original.getNano());
            Instant parsed = TimestampCodec.parseEpochSeconds(buf, 0, end);
            assertEquals(original, parsed);
        }

        @Test
        void negativeEpochSecondsRoundTrip() {
            Instant original = Instant.ofEpochSecond(-100, 500000000);
            byte[] buf = new byte[64];
            int end = TimestampCodec.writeEpochSeconds(buf,
                    0,
                    original.getEpochSecond(),
                    original.getNano());
            Instant parsed = TimestampCodec.parseEpochSeconds(buf, 0, end);
            assertEquals(original, parsed);
        }
    }
}
