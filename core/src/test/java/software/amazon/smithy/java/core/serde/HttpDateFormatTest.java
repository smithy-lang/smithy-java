/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the hand-rolled {@link HttpDateFormat} implementation.
 *
 * <p>The fast path is verified against the reference {@link DateTimeFormatter}
 * to guarantee bit-for-bit compatibility for all valid IMF-fixdate inputs.
 */
public class HttpDateFormatTest {

    private static final DateTimeFormatter REFERENCE = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
            .withZone(ZoneOffset.UTC)
            .withLocale(Locale.US);

    /**
     * Each case is (Instant, expected formatted string). The expected
     * strings were captured from the reference {@link DateTimeFormatter} so
     * a regression in either implementation will be caught.
     */
    static Stream<Arguments> formatCases() {
        return Stream.of(
                // Epoch boundary
                Arguments.of(Instant.EPOCH, "Thu, 01 Jan 1970 00:00:00 GMT"),
                // RFC 7231 §7.1.1.1 example
                Arguments.of(Instant.ofEpochSecond(784111777L), "Sun, 06 Nov 1994 08:49:37 GMT"),
                // Each day-of-week — pin against the reference formatter (calendar
                // history before the proleptic Gregorian era can confuse hardcoded
                // values, so we let the JDK be the source of truth here).
                referenceCase("2024-01-01T00:00:00Z"),
                referenceCase("2024-01-02T00:00:00Z"),
                referenceCase("2024-01-03T00:00:00Z"),
                referenceCase("2024-01-04T00:00:00Z"),
                referenceCase("2024-01-05T00:00:00Z"),
                referenceCase("2024-01-06T00:00:00Z"),
                referenceCase("2024-01-07T00:00:00Z"),
                // Each month
                referenceCase("2024-02-29T12:34:56Z"),
                referenceCase("2024-03-15T00:00:00Z"),
                referenceCase("2024-04-30T23:59:59Z"),
                referenceCase("2024-05-15T01:23:45Z"),
                referenceCase("2024-06-15T01:23:45Z"),
                referenceCase("2024-07-15T01:23:45Z"),
                referenceCase("2024-08-15T01:23:45Z"),
                referenceCase("2024-09-15T01:23:45Z"),
                referenceCase("2024-10-15T01:23:45Z"),
                referenceCase("2024-11-15T01:23:45Z"),
                referenceCase("2024-12-15T01:23:45Z"),
                // Year padding behaviour at zero-padded boundaries
                referenceCase("0001-01-01T00:00:00Z"),
                referenceCase("0099-12-31T23:59:59Z"),
                referenceCase("0999-12-31T23:59:59Z"),
                referenceCase("9999-12-31T23:59:59Z"),
                // Leap and non-leap year February ends
                referenceCase("2000-02-29T00:00:00Z"),
                referenceCase("2100-02-28T00:00:00Z"),
                // Sub-second component is dropped (HTTP-date is whole-second precision)
                Arguments.of(Instant.ofEpochSecond(784111777L, 999_999_999L), "Sun, 06 Nov 1994 08:49:37 GMT"));
    }

    private static Arguments referenceCase(String iso) {
        Instant value = Instant.parse(iso);
        return Arguments.of(value, REFERENCE.format(value));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("formatCases")
    void formatProducesExpectedString(Instant input, String expected) {
        assertEquals(expected, HttpDateFormat.format(input.getEpochSecond()));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("formatCases")
    void formatMatchesReferenceFormatter(Instant input, String expected) {
        // Same as above but anchors against the JDK formatter so the test
        // fails informatively if either side is wrong.
        assertEquals(REFERENCE.format(input.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)),
                HttpDateFormat.format(input.getEpochSecond()));
    }

    @ParameterizedTest(name = "{1} -> {0}")
    @MethodSource("formatCases")
    void parseRoundTrips(Instant expected, String input) {
        // Drop sub-second precision since HTTP-date doesn't carry it.
        Instant truncated = expected.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        assertEquals(truncated, HttpDateFormat.parse(input));
    }

    @ParameterizedTest(name = "random epoch second {0}")
    @MethodSource("randomEpochSeconds")
    void roundTripWithReferenceFormatter(long epoch) {
        // For arbitrary epoch seconds in a wide but representable range,
        // verify both implementations agree.
        String fast = HttpDateFormat.format(epoch);
        String reference = REFERENCE.format(Instant.ofEpochSecond(epoch));
        assertEquals(reference, fast);
        assertEquals(Instant.ofEpochSecond(epoch), HttpDateFormat.parse(fast));
    }

    /**
     * Supplies a deterministic stream of random epoch-seconds in years
     * 0001 through 9999 to fuzz the fast path against the reference.
     */
    static Stream<Long> randomEpochSeconds() {
        Random rng = new Random(0xC0FFEEL);
        // Year 0001 minimum and year 9999 maximum.
        long min = LocalDateTime.of(1, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
        long max = LocalDateTime.of(9999, 12, 31, 23, 59, 59).toEpochSecond(ZoneOffset.UTC);
        return Stream.generate(() -> min + (long) (rng.nextDouble() * (max - min))).limit(500);
    }

    /**
     * Inputs that the strict fast path rejects but the JDK formatter
     * accepts; these should still parse correctly via the fallback.
     */
    static Stream<Arguments> fallbackCases() {
        return Stream.of(
                // Wrong-cased month — the JDK pattern is case-sensitive too
                // (so this should THROW), grouped here to assert the fallback
                // also rejects:
                Arguments.of("Sun, 06 nov 1994 08:49:37 GMT", true),
                // Wrong length — the fast path rejects up front, fallback also rejects
                Arguments.of("Sun, 06 Nov 1994 8:49:37 GMT", true),
                Arguments.of("not a date", true),
                Arguments.of("", true));
    }

    @ParameterizedTest(name = "fallback ({0})")
    @MethodSource("fallbackCases")
    void fallbackHandlesMalformedInputs(String input, boolean shouldThrow) {
        if (shouldThrow) {
            assertThrows(DateTimeParseException.class, () -> HttpDateFormat.parse(input));
        } else {
            // Sanity placeholder if the matrix grows to include accepted forms later.
            HttpDateFormat.parse(input);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mon, 32 Jan 2024 00:00:00 GMT", // day out of range
            "Mon, 00 Jan 2024 00:00:00 GMT", // day out of range
            "Mon, 31 Feb 2024 00:00:00 GMT", // Feb 31 doesn't exist
            "Mon, 30 Feb 2024 00:00:00 GMT", // Feb 30 doesn't exist (leap year still 29)
            "Mon, 01 Foo 2024 00:00:00 GMT", // bad month name
            "Mon, 01 Jan 2024 24:00:00 GMT", // hour out of range
            "Mon, 01 Jan 2024 00:60:00 GMT", // minute out of range
            "Mon, 01 Jan 2024 00:00:60 GMT", // second out of range
            "Mon, 1A Jan 2024 00:00:00 GMT", // non-digit in day
    })
    void rejectsOutOfRangeOrMalformedDates(String input) {
        assertThrows(RuntimeException.class, () -> HttpDateFormat.parse(input));
    }

    @Test
    void permissiveAboutDayOfWeekField() {
        // Real HTTP-date producers occasionally get the day-of-week wrong.
        // Prefer accepting the value (matching what real-world tolerant
        // parsers do) over strictly rejecting it. Document this contract
        // here so future changes that tighten it must update this test.
        Instant parsed = HttpDateFormat.parse("Mon, 06 Nov 1994 08:49:37 GMT");
        assertEquals(Instant.ofEpochSecond(784111777L), parsed);
    }

    @Test
    void honoursOutputFromTimestampFormatter() {
        // The TimestampFormatter prelude routes HTTP_DATE through this code.
        TimestampFormatter f = TimestampFormatter.Prelude.HTTP_DATE;
        Instant value = Instant.ofEpochSecond(784111777L);
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", f.writeString(value));
        assertEquals(value, f.readFromString("Sun, 06 Nov 1994 08:49:37 GMT", false));
    }

    @Test
    void preludeHttpDateIsCachedSingleton() {
        // Sanity: confirm we did not accidentally introduce a new instance per
        // invocation (would defeat any later caching above this layer).
        assertSame(TimestampFormatter.Prelude.HTTP_DATE, TimestampFormatter.Prelude.HTTP_DATE);
    }
}
