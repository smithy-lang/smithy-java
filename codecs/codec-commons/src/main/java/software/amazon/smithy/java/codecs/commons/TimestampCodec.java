/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.time.Instant;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Low-level utilities for reading/writing timestamp values directly from/to byte arrays.
 */
@SmithyInternalApi
public final class TimestampCodec {

    private TimestampCodec() {}

    private static final byte[] DIGIT_PAIRS = new byte[200];

    static {
        for (int i = 0; i < 100; i++) {
            DIGIT_PAIRS[i * 2] = (byte) ('0' + i / 10);
            DIGIT_PAIRS[i * 2 + 1] = (byte) ('0' + i % 10);
        }
    }

    private static final byte[][] DAY_NAMES = {
            null,
            {'M', 'o', 'n'},
            {'T', 'u', 'e'},
            {'W', 'e', 'd'},
            {'T', 'h', 'u'},
            {'F', 'r', 'i'},
            {'S', 'a', 't'},
            {'S', 'u', 'n'},
    };

    private static final int[] NANO_SCALE = {
            0,
            100_000_000,
            10_000_000,
            1_000_000,
            100_000,
            10_000,
            1_000,
            100,
            10,
            1
    };

    private static final byte[][] MONTH_NAMES = {
            null,
            {'J', 'a', 'n'},
            {'F', 'e', 'b'},
            {'M', 'a', 'r'},
            {'A', 'p', 'r'},
            {'M', 'a', 'y'},
            {'J', 'u', 'n'},
            {'J', 'u', 'l'},
            {'A', 'u', 'g'},
            {'S', 'e', 'p'},
            {'O', 'c', 't'},
            {'N', 'o', 'v'},
            {'D', 'e', 'c'},
    };

    /**
     * Parses an ISO-8601 timestamp (YYYY-MM-DDThh:mm:ss[.nnn]Z) directly from a byte array.
     * Returns null if the format doesn't match (caller should fall back to DateTimeFormatter).
     */
    public static Instant parseIso8601(byte[] buf, int pos, int end) {
        if (pos + 20 > end) {
            return null;
        }

        int d0 = digit(buf[pos]), d1 = digit(buf[pos + 1]), d2 = digit(buf[pos + 2]), d3 = digit(buf[pos + 3]);
        if ((d0 | d1 | d2 | d3) < 0)
            return null;
        int year = d0 * 1000 + d1 * 100 + d2 * 10 + d3;
        pos += 4;
        if (buf[pos++] != '-')
            return null;

        int m0 = digit(buf[pos]), m1 = digit(buf[pos + 1]);
        if ((m0 | m1) < 0)
            return null;
        int month = m0 * 10 + m1;
        pos += 2;
        if (month < 1 || month > 12)
            return null;
        if (buf[pos++] != '-')
            return null;

        int dy0 = digit(buf[pos]), dy1 = digit(buf[pos + 1]);
        if ((dy0 | dy1) < 0)
            return null;
        int day = dy0 * 10 + dy1;
        pos += 2;
        if (day < 1 || day > maxDayOfMonth(year, month))
            return null;

        if (buf[pos] != 'T')
            return null;
        pos++;

        int h0 = digit(buf[pos]), h1 = digit(buf[pos + 1]);
        if ((h0 | h1) < 0)
            return null;
        int hour = h0 * 10 + h1;
        pos += 2;
        if (buf[pos++] != ':')
            return null;

        int mn0 = digit(buf[pos]), mn1 = digit(buf[pos + 1]);
        if ((mn0 | mn1) < 0)
            return null;
        int minute = mn0 * 10 + mn1;
        pos += 2;
        if (buf[pos++] != ':')
            return null;

        int s0 = digit(buf[pos]), s1 = digit(buf[pos + 1]);
        if ((s0 | s1) < 0)
            return null;
        int second = s0 * 10 + s1;
        pos += 2;
        if (hour > 23 || minute > 59 || second > 59)
            return null;

        int nano = 0;
        if (pos < end && buf[pos] == '.') {
            pos++;
            int fracLen = 0;
            while (pos < end) {
                int d = buf[pos] - '0';
                if (d < 0 || d > 9)
                    break;
                if (fracLen < 9) {
                    nano = nano * 10 + d;
                }
                fracLen++;
                pos++;
            }
            if (fracLen == 0 || fracLen > 9)
                return null;
            nano *= NANO_SCALE[fracLen];
        }

        if (pos >= end || buf[pos] != 'Z') {
            return null;
        }
        pos++;
        if (pos != end) {
            return null;
        }

        long epochDay = computeEpochDay(year, month, day);
        long epochSecond = epochDay * 86400 + hour * 3600 + minute * 60 + second;
        return Instant.ofEpochSecond(epochSecond, nano);
    }

    /**
     * Parses an HTTP-date (e.g. "Mon, 01 Jan 2024 12:00:00 GMT") directly from a byte array.
     * Returns null if the format doesn't match.
     */
    public static Instant parseHttpDate(byte[] buf, int pos, int end) {
        if (pos + 29 > end) {
            return null;
        }

        pos += 3;
        if (buf[pos] != ',' || buf[pos + 1] != ' ')
            return null;
        pos += 2;

        int dy0 = digit(buf[pos]), dy1 = digit(buf[pos + 1]);
        if ((dy0 | dy1) < 0)
            return null;
        int day = dy0 * 10 + dy1;
        pos += 2;
        if (day < 1)
            return null;
        if (buf[pos++] != ' ')
            return null;

        int month = parseMonthName(buf[pos], buf[pos + 1], buf[pos + 2]);
        if (month == -1)
            return null;
        pos += 3;
        if (buf[pos++] != ' ')
            return null;

        int y0 = digit(buf[pos]), y1 = digit(buf[pos + 1]), y2 = digit(buf[pos + 2]), y3 = digit(buf[pos + 3]);
        if ((y0 | y1 | y2 | y3) < 0)
            return null;
        int year = y0 * 1000 + y1 * 100 + y2 * 10 + y3;
        pos += 4;
        if (day > maxDayOfMonth(year, month))
            return null;
        if (buf[pos++] != ' ')
            return null;

        int h0 = digit(buf[pos]), h1 = digit(buf[pos + 1]);
        if ((h0 | h1) < 0)
            return null;
        int hour = h0 * 10 + h1;
        pos += 2;
        if (buf[pos++] != ':')
            return null;

        int mn0 = digit(buf[pos]), mn1 = digit(buf[pos + 1]);
        if ((mn0 | mn1) < 0)
            return null;
        int minute = mn0 * 10 + mn1;
        pos += 2;
        if (buf[pos++] != ':')
            return null;

        int s0 = digit(buf[pos]), s1 = digit(buf[pos + 1]);
        if ((s0 | s1) < 0)
            return null;
        int second = s0 * 10 + s1;
        pos += 2;

        if (hour > 23 || minute > 59 || second > 59)
            return null;

        if (buf[pos] != ' ')
            return null;
        pos++;
        if (pos + 3 > end || buf[pos] != 'G' || buf[pos + 1] != 'M' || buf[pos + 2] != 'T')
            return null;

        long epochDay = computeEpochDay(year, month, day);
        long epochSecond = epochDay * 86400 + hour * 3600 + minute * 60 + second;
        return Instant.ofEpochSecond(epochSecond);
    }

    /**
     * Parses an epoch-seconds timestamp from a byte array.
     * Handles both integer (e.g. "1234567890") and fractional (e.g. "1234567890.123") forms.
     * Returns null if the format doesn't match.
     */
    public static Instant parseEpochSeconds(byte[] buf, int pos, int end) {
        if (pos >= end) {
            return null;
        }

        boolean negative = false;
        if (buf[pos] == '-') {
            negative = true;
            pos++;
            if (pos >= end)
                return null;
        }

        long seconds = 0;
        int intStart = pos;
        while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
            long prev = seconds;
            seconds = seconds * 10 + (buf[pos] - '0');
            if (seconds < prev)
                return null; // overflow
            pos++;
        }
        if (pos == intStart)
            return null;

        int nano = 0;
        boolean hasExponent = false;
        if (pos < end && buf[pos] == '.') {
            pos++;
            int fracStart = pos;
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
            int fracLen = pos - fracStart;

            hasExponent = pos < end && (buf[pos] == 'e' || buf[pos] == 'E');
            if (!hasExponent) {
                if (fracLen > 9)
                    return null;
                for (int i = fracStart; i < pos; i++) {
                    nano = nano * 10 + (buf[i] - '0');
                }
                nano *= NANO_SCALE[fracLen];
            }
        } else {
            hasExponent = pos < end && (buf[pos] == 'e' || buf[pos] == 'E');
        }

        if (hasExponent) {
            return parseEpochSecondsBigDecimal(buf, intStart - (negative ? 1 : 0), end);
        }

        if (pos != end)
            return null;

        if (negative) {
            if (nano == 0) {
                if (-seconds < Instant.MIN.getEpochSecond())
                    return null;
                return Instant.ofEpochSecond(-seconds);
            }
            long es = -seconds - 1;
            if (es < Instant.MIN.getEpochSecond())
                return null;
            return Instant.ofEpochSecond(es, 1_000_000_000 - nano);
        }
        if (seconds > Instant.MAX.getEpochSecond())
            return null;
        return Instant.ofEpochSecond(seconds, nano);
    }

    private static Instant parseEpochSecondsBigDecimal(byte[] buf, int start, int end) {
        int pos = start;
        boolean negative = pos < end && buf[pos] == '-';
        if (negative)
            pos++;

        long mantissa = 0;
        int mantissaDigits = 0;
        int pointOffset = -1; // digits after the decimal point

        while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
            if (mantissaDigits < 18) {
                mantissa = mantissa * 10 + (buf[pos] - '0');
            }
            mantissaDigits++;
            pos++;
        }
        if (pos < end && buf[pos] == '.') {
            pos++;
            pointOffset = 0;
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                if (mantissaDigits < 18) {
                    mantissa = mantissa * 10 + (buf[pos] - '0');
                    mantissaDigits++;
                }
                pointOffset++;
                pos++;
            }
        }

        if (pos >= end || (buf[pos] != 'e' && buf[pos] != 'E'))
            return null;
        pos++;

        boolean expNegative = false;
        if (pos < end && (buf[pos] == '+' || buf[pos] == '-')) {
            expNegative = buf[pos] == '-';
            pos++;
        }
        if (pos >= end)
            return null;

        int exponent = 0;
        while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
            exponent = exponent * 10 + (buf[pos] - '0');
            if (exponent > 20)
                return null; // way outside long range
            pos++;
        }
        if (pos != end)
            return null;
        if (expNegative)
            exponent = -exponent;

        int shift = exponent - (pointOffset >= 0 ? pointOffset : 0);

        long seconds;
        int nano = 0;

        if (shift >= 0) {
            seconds = mantissa;
            for (int i = 0; i < shift; i++) {
                seconds *= 10;
                if (seconds < 0)
                    return null; // overflow
            }
        } else {
            int fracDigits = -shift;
            if (fracDigits > 9)
                return null;
            long divisor = 1;
            for (int i = 0; i < fracDigits; i++)
                divisor *= 10;
            seconds = mantissa / divisor;
            long fracPart = mantissa % divisor;
            for (int i = fracDigits; i < 9; i++)
                fracPart *= 10;
            nano = (int) fracPart;
        }

        if (negative) {
            if (nano == 0) {
                seconds = -seconds;
                if (seconds > 0)
                    return null; // overflow
                if (seconds < Instant.MIN.getEpochSecond())
                    return null;
                return Instant.ofEpochSecond(seconds);
            }
            long es = -seconds - 1;
            if (es < Instant.MIN.getEpochSecond())
                return null;
            return Instant.ofEpochSecond(es, 1_000_000_000 - nano);
        }
        if (seconds > Instant.MAX.getEpochSecond())
            return null;
        return Instant.ofEpochSecond(seconds, nano);
    }

    private static int parseMonthName(byte c0, byte c1, byte c2) {
        switch (c0) {
            case 'J':
                if (c1 == 'a' && c2 == 'n')
                    return 1;
                if (c1 == 'u' && c2 == 'n')
                    return 6;
                if (c1 == 'u' && c2 == 'l')
                    return 7;
                return -1;
            case 'F':
                if (c1 == 'e' && c2 == 'b')
                    return 2;
                return -1;
            case 'M':
                if (c1 == 'a' && c2 == 'r')
                    return 3;
                if (c1 == 'a' && c2 == 'y')
                    return 5;
                return -1;
            case 'A':
                if (c1 == 'p' && c2 == 'r')
                    return 4;
                if (c1 == 'u' && c2 == 'g')
                    return 8;
                return -1;
            case 'S':
                if (c1 == 'e' && c2 == 'p')
                    return 9;
                return -1;
            case 'O':
                if (c1 == 'c' && c2 == 't')
                    return 10;
                return -1;
            case 'N':
                if (c1 == 'o' && c2 == 'v')
                    return 11;
                return -1;
            case 'D':
                if (c1 == 'e' && c2 == 'c')
                    return 12;
                return -1;
            default:
                return -1;
        }
    }

    /**
     * Writes an epoch-seconds timestamp directly. Writes "seconds" for whole seconds
     * or "seconds.nanos" for fractional, with trailing zeros stripped.
     */
    public static int writeEpochSeconds(byte[] buf, int pos, long epochSecond, int nano) {
        if (nano == 0) {
            return NumberCodec.writeLong(buf, pos, epochSecond);
        }

        int fraction = nano;
        if (epochSecond < 0) {
            epochSecond += 1;
            fraction = 1_000_000_000 - nano;
            if (epochSecond == 0) {
                buf[pos++] = '-';
                buf[pos++] = '0';
            } else {
                pos = NumberCodec.writeLong(buf, pos, epochSecond);
            }
        } else {
            pos = NumberCodec.writeLong(buf, pos, epochSecond);
        }

        buf[pos++] = '.';

        int hi = fraction / 1_000_000;
        int mid = (fraction / 1_000) % 1_000;
        int lo = fraction % 1_000;
        buf[pos++] = (byte) ('0' + hi / 100);
        buf[pos++] = (byte) ('0' + (hi / 10) % 10);
        buf[pos++] = (byte) ('0' + hi % 10);
        buf[pos++] = (byte) ('0' + mid / 100);
        buf[pos++] = (byte) ('0' + (mid / 10) % 10);
        buf[pos++] = (byte) ('0' + mid % 10);
        buf[pos++] = (byte) ('0' + lo / 100);
        buf[pos++] = (byte) ('0' + (lo / 10) % 10);
        buf[pos++] = (byte) ('0' + lo % 10);
        while (buf[pos - 1] == '0') {
            pos--;
        }
        return pos;
    }

    /**
     * Writes an ISO-8601 timestamp directly to the byte buffer without quotes.
     * Produces output like {@code 2025-01-15T10:30:00Z} or {@code 2025-01-15T10:30:00.123Z}.
     */
    public static int writeIso8601(byte[] buf, int pos, Instant value) {
        long epochSecond = value.getEpochSecond();
        int nano = value.getNano();

        long epochDay;
        int secondOfDay;
        if (epochSecond >= 0) {
            epochDay = epochSecond / 86400;
            secondOfDay = (int) (epochSecond - epochDay * 86400);
        } else {
            epochDay = Math.floorDiv(epochSecond, 86400);
            secondOfDay = (int) Math.floorMod(epochSecond, 86400);
        }
        int hour = secondOfDay / 3600;
        int minute = (secondOfDay % 3600) / 60;
        int second = secondOfDay % 60;
        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int month = (int) (mp < 10 ? mp + 3 : mp - 9);
        int year = (int) (yoe + era * 400 + (month <= 2 ? 1 : 0));

        if (year >= 0 && year <= 9999) {
            int hi = year / 100;
            int lo = year - hi * 100;
            buf[pos++] = DIGIT_PAIRS[hi * 2];
            buf[pos++] = DIGIT_PAIRS[hi * 2 + 1];
            buf[pos++] = DIGIT_PAIRS[lo * 2];
            buf[pos++] = DIGIT_PAIRS[lo * 2 + 1];
        } else {
            String yearStr = String.format("%04d", year);
            for (int i = 0; i < yearStr.length(); i++) {
                buf[pos++] = (byte) yearStr.charAt(i);
            }
        }

        buf[pos++] = '-';
        buf[pos++] = DIGIT_PAIRS[month * 2];
        buf[pos++] = DIGIT_PAIRS[month * 2 + 1];
        buf[pos++] = '-';
        buf[pos++] = DIGIT_PAIRS[day * 2];
        buf[pos++] = DIGIT_PAIRS[day * 2 + 1];
        buf[pos++] = 'T';
        buf[pos++] = DIGIT_PAIRS[hour * 2];
        buf[pos++] = DIGIT_PAIRS[hour * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[minute * 2];
        buf[pos++] = DIGIT_PAIRS[minute * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[second * 2];
        buf[pos++] = DIGIT_PAIRS[second * 2 + 1];

        if (nano != 0) {
            buf[pos++] = '.';
            // Write all 9 digits in 3 groups of 3 (avoids repeated division loops)
            int hi = nano / 1_000_000;
            int mid = (nano / 1_000) % 1_000;
            int lo = nano % 1_000;
            buf[pos] = (byte) ('0' + hi / 100);
            buf[pos + 1] = (byte) ('0' + (hi / 10) % 10);
            buf[pos + 2] = (byte) ('0' + hi % 10);
            buf[pos + 3] = (byte) ('0' + mid / 100);
            buf[pos + 4] = (byte) ('0' + (mid / 10) % 10);
            buf[pos + 5] = (byte) ('0' + mid % 10);
            buf[pos + 6] = (byte) ('0' + lo / 100);
            buf[pos + 7] = (byte) ('0' + (lo / 10) % 10);
            buf[pos + 8] = (byte) ('0' + lo % 10);
            pos += 9;
            while (buf[pos - 1] == '0') {
                pos--;
            }
        }

        buf[pos++] = 'Z';
        return pos;
    }

    /**
     * Writes an HTTP-date timestamp directly to the byte buffer without quotes.
     * Produces output like {@code Sat, 01 Jan 2026 00:00:00 GMT}.
     */
    public static int writeHttpDate(byte[] buf, int pos, Instant value) {
        long epochSecond = value.getEpochSecond();

        long epochDay;
        int secondOfDay;
        if (epochSecond >= 0) {
            epochDay = epochSecond / 86400;
            secondOfDay = (int) (epochSecond - epochDay * 86400);
        } else {
            epochDay = Math.floorDiv(epochSecond, 86400);
            secondOfDay = (int) Math.floorMod(epochSecond, 86400);
        }
        int hour = secondOfDay / 3600;
        int minute = (secondOfDay % 3600) / 60;
        int second = secondOfDay % 60;
        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int month = (int) (mp < 10 ? mp + 3 : mp - 9);
        int year = (int) (yoe + era * 400 + (month <= 2 ? 1 : 0));

        int dow = (int) Math.floorMod(epochDay + 3, 7) + 1;

        byte[] dayName = DAY_NAMES[dow];
        buf[pos++] = dayName[0];
        buf[pos++] = dayName[1];
        buf[pos++] = dayName[2];
        buf[pos++] = ',';
        buf[pos++] = ' ';

        buf[pos++] = DIGIT_PAIRS[day * 2];
        buf[pos++] = DIGIT_PAIRS[day * 2 + 1];
        buf[pos++] = ' ';

        byte[] monthName = MONTH_NAMES[month];
        buf[pos++] = monthName[0];
        buf[pos++] = monthName[1];
        buf[pos++] = monthName[2];
        buf[pos++] = ' ';

        if (year >= 0 && year <= 9999) {
            int hi = year / 100;
            int lo = year - hi * 100;
            buf[pos++] = DIGIT_PAIRS[hi * 2];
            buf[pos++] = DIGIT_PAIRS[hi * 2 + 1];
            buf[pos++] = DIGIT_PAIRS[lo * 2];
            buf[pos++] = DIGIT_PAIRS[lo * 2 + 1];
        } else {
            String yearStr = String.format("%04d", year);
            for (int i = 0; i < yearStr.length(); i++) {
                buf[pos++] = (byte) yearStr.charAt(i);
            }
        }

        buf[pos++] = ' ';
        buf[pos++] = DIGIT_PAIRS[hour * 2];
        buf[pos++] = DIGIT_PAIRS[hour * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[minute * 2];
        buf[pos++] = DIGIT_PAIRS[minute * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[second * 2];
        buf[pos++] = DIGIT_PAIRS[second * 2 + 1];
        buf[pos++] = ' ';
        buf[pos++] = 'G';
        buf[pos++] = 'M';
        buf[pos++] = 'T';
        return pos;
    }

    private static int maxDayOfMonth(int year, int month) {
        switch (month) {
            case 2:
                return (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private static long computeEpochDay(int year, int month, int day) {
        long y = year;
        long m = month;
        if (m <= 2) {
            y--;
            m += 9;
        } else {
            m -= 3;
        }
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * m + 2) / 5 + day - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    private static int digit(byte b) {
        int d = b - '0';
        if (d < 0 || d > 9)
            return -1;
        return d;
    }
}
