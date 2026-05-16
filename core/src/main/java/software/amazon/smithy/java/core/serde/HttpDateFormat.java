/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Performance-focused formatter and parser for the HTTP-date / IMF-fixdate format.
 */
final class HttpDateFormat {

    private HttpDateFormat() {}

    /** Length of a well-formed IMF-fixdate string. */
    static final int LENGTH = 29;

    /**
     * Concatenated three-letter day-of-week abbreviations, indexed by {@code (DayOfWeek.getValue() - 1) * 3}.
     * Order matches {@link java.time.DayOfWeek}: MONDAY → SUNDAY.
     */
    private static final char[] DAY_NAMES = "MonTueWedThuFriSatSun".toCharArray();

    /**
     * Concatenated three-letter month abbreviations, indexed by
     * {@code (monthValue - 1) * 3}. Order is calendar order, January → December.
     */
    private static final char[] MONTH_NAMES = "JanFebMarAprMayJunJulAugSepOctNovDec".toCharArray();

    /**
     * Slow-path formatter/parser used as a fallback when the input does not match strict IMF-fixdate.
     */
    private static final DateTimeFormatter FALLBACK = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
            .withZone(ZoneOffset.UTC)
            .withLocale(Locale.US);

    private static final long INSTANT_SECONDS_MIN = ChronoField.INSTANT_SECONDS.range().getMinimum();
    private static final long INSTANT_SECONDS_MAX = ChronoField.INSTANT_SECONDS.range().getMaximum();

    /**
     * Format the given {@link java.time.Instant} as an HTTP-date string.
     *
     * @param epochSecond seconds since the epoch (UTC).
     * @return the formatted IMF-fixdate string, always 29 characters long.
     */
    static String format(long epochSecond) {
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);

        int year = ldt.getYear();
        int month = ldt.getMonthValue();
        int day = ldt.getDayOfMonth();
        int hour = ldt.getHour();
        int minute = ldt.getMinute();
        int second = ldt.getSecond();
        int dowValue = ldt.getDayOfWeek().getValue();

        char[] buf = new char[LENGTH];
        int dowIdx = (dowValue - 1) * 3;
        buf[0] = DAY_NAMES[dowIdx];
        buf[1] = DAY_NAMES[dowIdx + 1];
        buf[2] = DAY_NAMES[dowIdx + 2];
        buf[3] = ',';
        buf[4] = ' ';
        buf[5] = (char) ('0' + day / 10);
        buf[6] = (char) ('0' + day % 10);
        buf[7] = ' ';
        int monIdx = (month - 1) * 3;
        buf[8] = MONTH_NAMES[monIdx];
        buf[9] = MONTH_NAMES[monIdx + 1];
        buf[10] = MONTH_NAMES[monIdx + 2];
        buf[11] = ' ';
        buf[12] = (char) ('0' + (year / 1000) % 10);
        buf[13] = (char) ('0' + (year / 100) % 10);
        buf[14] = (char) ('0' + (year / 10) % 10);
        buf[15] = (char) ('0' + year % 10);
        buf[16] = ' ';
        buf[17] = (char) ('0' + hour / 10);
        buf[18] = (char) ('0' + hour % 10);
        buf[19] = ':';
        buf[20] = (char) ('0' + minute / 10);
        buf[21] = (char) ('0' + minute % 10);
        buf[22] = ':';
        buf[23] = (char) ('0' + second / 10);
        buf[24] = (char) ('0' + second % 10);
        buf[25] = ' ';
        buf[26] = 'G';
        buf[27] = 'M';
        buf[28] = 'T';
        return new String(buf);
    }

    /**
     * Format the given instant using the JDK fallback formatter.
     */
    static String formatFallback(Instant value) {
        return FALLBACK.format(value);
    }

    /**
     * Parse an HTTP-date string into an {@link java.time.Instant} epoch second.
     *
     * <p>Strictly handles the IMF-fixdate form. For non-conforming inputs (different lengths, lowercase day or
     * month names, missing GMT, etc.) delegates to the JDK {@link DateTimeFormatter} — the same parser used
     * by the previous implementation, so behavioural compatibility is preserved.
     *
     * @param value the input string.
     * @return the parsed instant.
     * @throws DateTimeParseException if the input cannot be parsed by either
     *         the fast path or the JDK fallback.
     */
    static Instant parse(String value) {
        if (value.length() != LENGTH || !validateLayout(value)) {
            return FALLBACK.parse(value, Instant::from);
        }

        // Layout matched; from here on, malformed fields throw rather than silently round-trip through the fallback
        // (which can be too lenient about overflow values like hour 24). Throw the same exception type
        // the fallback would for consistent caller-side handling.
        try {
            int day = parseTwoDigit(value, 5);
            int month = parseMonthName(value, 8);
            int year = parseYear(value, 12);
            int hour = parseTwoDigit(value, 17);
            int minute = parseTwoDigit(value, 20);
            int second = parseTwoDigit(value, 23);
            LocalDate date = LocalDate.of(year, month, day);
            LocalTime time = LocalTime.of(hour, minute, second);
            return date.atTime(time).toInstant(ZoneOffset.UTC);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new DateTimeParseException("Invalid HTTP-date: " + value, value, 0, e);
        }
    }

    /**
     * Verify that the fixed-position separators of an IMF-fixdate are present.
     */
    private static boolean validateLayout(String s) {
        return s.charAt(3) == ','
                && s.charAt(4) == ' '
                && s.charAt(7) == ' '
                && s.charAt(11) == ' '
                && s.charAt(16) == ' '
                && s.charAt(19) == ':'
                && s.charAt(22) == ':'
                && s.charAt(25) == ' '
                && s.charAt(26) == 'G'
                && s.charAt(27) == 'M'
                && s.charAt(28) == 'T';
    }

    private static int parseTwoDigit(String s, int idx) {
        char a = s.charAt(idx);
        char b = s.charAt(idx + 1);
        if (a < '0' || a > '9' || b < '0' || b > '9') {
            throw new NumberFormatException();
        }
        return (a - '0') * 10 + (b - '0');
    }

    private static int parseYear(String s, int idx) {
        int year = 0;
        for (int i = 0; i < 4; i++) {
            char c = s.charAt(idx + i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException();
            }
            year = year * 10 + (c - '0');
        }
        return year;
    }

    /**
     * Map a 3-character month abbreviation to its 1-based month number.
     * Uses a switch on the first letter (well-predicted by the JIT) and compares the trailing two letters directly.
     */
    private static int parseMonthName(String s, int idx) {
        char a = s.charAt(idx);
        char b = s.charAt(idx + 1);
        char c = s.charAt(idx + 2);
        switch (a) {
            case 'J':
                if (b == 'a' && c == 'n') {
                    return 1;
                } else if (b == 'u' && c == 'n') {
                    return 6;
                } else if (b == 'u' && c == 'l') {
                    return 7;
                }
                break;
            case 'F':
                if (b == 'e' && c == 'b') {
                    return 2;
                }
                break;
            case 'M':
                if (b == 'a' && c == 'r') {
                    return 3;
                } else if (b == 'a' && c == 'y') {
                    return 5;
                }
                break;
            case 'A':
                if (b == 'p' && c == 'r') {
                    return 4;
                } else if (b == 'u' && c == 'g') {
                    return 8;
                }
                break;
            case 'S':
                if (b == 'e' && c == 'p') {
                    return 9;
                }
                break;
            case 'O':
                if (b == 'c' && c == 't') {
                    return 10;
                }
                break;
            case 'N':
                if (b == 'o' && c == 'v') {
                    return 11;
                }
                break;
            case 'D':
                if (b == 'e' && c == 'c') {
                    return 12;
                }
                break;
        }
        throw new IllegalArgumentException("Unknown month abbreviation: " + s.substring(idx, idx + 3));
    }

    /**
     * Range-check a long that came from {@link Instant#getEpochSecond()}. Year is supported through 9999 (4 digits),
     * so epoch-seconds outside that range fall back to the JDK formatter to either succeed or fail uniformly.
     */
    static boolean isYearRepresentable(long epochSecond) {
        // 0001-01-01T00:00:00Z = -62135596800
        // 9999-12-31T23:59:59Z =  253402300799
        return epochSecond >= -62135596800L && epochSecond <= 253402300799L
        // Also need to bound the field range
                && epochSecond >= INSTANT_SECONDS_MIN
                && epochSecond <= INSTANT_SECONDS_MAX;
    }
}
