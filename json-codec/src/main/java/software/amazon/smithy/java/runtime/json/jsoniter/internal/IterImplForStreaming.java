/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

final class IterImplForStreaming {

    static long readLongSlowPath(final JsonIterator iter, long value) {
        value = -value; // add negatives to avoid redundant checks for Long.MIN_VALUE on each iteration
        long multmin = -922337203685477580L; // limit / 10

        for (int i = iter.head; i < iter.tail; i++) {
            int ind = IterImplNumber.intDigits[iter.buf[i]];
            if (ind == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                return value;
            }
            if (value < multmin) {
                throw iter.reportError("Value is too large for long");
            }
            value = (value << 3) + (value << 1) - ind;
            if (value >= 0) {
                throw iter.reportError("Value is too large for long");
            }
        }

        iter.head = iter.tail;
        return value;
    }

    static int readIntSlowPath(final JsonIterator iter, int value) {
        value = -value; // add negatives to avoid redundant checks for Integer.MIN_VALUE on each iteration
        int multmin = -214748364; // limit / 10

        for (int i = iter.head; i < iter.tail; i++) {
            int ind = IterImplNumber.intDigits[iter.buf[i]];
            if (ind == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                return value;
            }
            if (value < multmin) {
                throw iter.reportError("Value is too large for int");
            }
            value = (value << 3) + (value << 1) - ind;
            if (value >= 0) {
                throw iter.reportError("Value is too large for int");
            }
        }

        iter.head = iter.tail;
        return value;
    }

    static double readDoubleSlowPath(final JsonIterator iter) {
        return IterImplForStreaming.parseJsonNumber(iter).doubleValue();
    }

    static void assertNotLeadingZero(JsonIterator iter) {
        try {
            byte nextByte = iter.buf[iter.head];
            int ind2 = IterImplNumber.intDigits[nextByte];
            if (ind2 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                return;
            }
            throw iter.reportError("Leading zero is invalid");
        } catch (ArrayIndexOutOfBoundsException e) {
            iter.head = iter.tail;
        }
    }

    public static Number parseJsonNumber(final JsonIterator iter) {
        long integralPart = 0;
        long decimalPart = 0;
        int decimalDivisor = 1; // Tracks how many places after the decimal
        boolean negative = false;
        boolean hasDecimal = false;
        boolean hasExponent = false;
        int exponent = 0;
        boolean exponentNegative = false;

        var buf = iter.buf;
        var position = iter.head;
        var tail = iter.tail;

        if (position == tail) {
            throw iter.reportError("Expected a number");
        }

        // Is it negative: ["-"] digit
        if (buf[position] == '-') {
            negative = true;
            if (++position == tail) {
                throw iter.reportError("Invalid number: '-' with no number value");
            }
        }

        // Parse the integral part of the number: 1*DIGIT
        switch (buf[position]) {
            case '0':
                // Handle zero explicitly to avoid leading zeros
                position++;
                if (position < tail && buf[position] >= '0' && buf[position] <= '9') {
                    throw iter.reportError("Invalid number: leading zeros are not allowed");
                }
                break;
            case '1', '2', '3', '4', '5', '6', '7', '8', '9':
                do {
                    integralPart = integralPart * 10 + (buf[position] - '0');
                    position++;
                } while (position < tail && buf[position] >= '0' && buf[position] <= '9');
                break;
            default:
                throw iter.reportError("Invalid number: unexpected character");
        }

        // Parse the decimal part if it exists: ["." 1*DIGIT]
        if (position < tail && buf[position] == '.') {
            hasDecimal = true;
            position++; // Move past the decimal point

            if (position >= tail || buf[position] < '0' || buf[position] > '9') {
                throw iter.reportError("Invalid number: digits must follow decimal point");
            }

            do {
                decimalPart = decimalPart * 10 + (buf[position] - '0');
                decimalDivisor *= 10;
                position++;
            } while (position < tail && buf[position] >= '0' && buf[position] <= '9');
        }

        // Parse exponent if present: ["e" / "E" ["-"/"+"] 1*DIGIT]
        if (position < tail && (buf[position] == 'e' || buf[position] == 'E')) {
            hasExponent = true;
            position++; // skip 'e' | 'E'

            if (buf[position] == '-') {
                exponentNegative = true;
                position++;
            } else if (buf[position] == '+') {
                position++;
            }

            if (position >= tail || buf[position] < '0' || buf[position] > '9') {
                throw iter.reportError("Invalid number: digits must follow exponent");
            }

            // Parse the digits of the exponent part
            while (position < tail && buf[position] >= '0' && buf[position] <= '9') {
                exponent = exponent * 10 + (buf[position] - '0');
                position++;
            }

            if (exponentNegative) {
                exponent = -exponent;
            }
        }

        // If there is no decimal or exponent, return as Long or Integer
        if (!hasDecimal && !hasExponent) {
            iter.head = position;
            if (negative) {
                integralPart = -integralPart;
            }
            if (integralPart >= Integer.MIN_VALUE && integralPart <= Integer.MAX_VALUE) {
                return (int) integralPart;
            }
            return integralPart;
        }

        // Handle decimal numbers
        double result = (double) integralPart;

        if (hasDecimal) {
            result += (double) decimalPart / decimalDivisor;
        }

        if (hasExponent) {
            result *= Math.pow(10, exponent);
        }

        if (negative) {
            result = -result;
        }

        if (Double.isFinite(result)) {
            iter.head = position;
            return result;
        }

        // Use a BigDecimal as a last resort.
        String numberString = new String(buf, iter.head, position - iter.head, StandardCharsets.US_ASCII);
        iter.head = position;

        try {
            return new BigDecimal(numberString);
        } catch (NumberFormatException e) {
            // e.g., the number has too many decimals.
            throw iter.reportError("Invalid number: " + e.getMessage());
        }
    }
}
