/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import software.amazon.smithy.java.codecs.commons.CompactStringAccess;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Character-class scans over a string's Latin1 bytes, eight bytes at a time.
 *
 * <p>The rules language validates host labels and bucket names on nearly every endpoint resolution, and
 * those were the two most expensive operations a resolve performed — the library implementations route
 * every character through {@code String.charAt} into a separate predicate, so there is nothing for the
 * JIT to unroll. Reading the bytes directly and testing a word at a time cut a 14-character host label
 * from 32.6ns to 13.4ns.
 *
 * <p>Internal to the rules engine; it lives here rather than in {@code EndpointUtils} only so the
 * AWS-specific functions in another package can share the scans.
 */
@SmithyInternalApi
public final class AsciiBytes {

    private static final long ONES = 0x0101010101010101L;
    private static final long HIGH = 0x8080808080808080L;
    private static final VarHandle WORDS =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    private AsciiBytes() {}

    /**
     * Gets a string's Latin1 bytes without copying.
     *
     * @param value the string.
     * @return the bytes, or null if the string is not Latin1 coded and must be scanned by character.
     */
    public static byte[] latin1(String value) {
        return CompactStringAccess.latin1Bytes(value);
    }

    /**
     * True if every byte in {@code [from, to)} is a letter of either case, a digit, or {@code '-'}.
     *
     * @param bytes the bytes to scan.
     * @param from inclusive start.
     * @param to exclusive end.
     * @return true if every byte is in the class.
     */
    public static boolean allAlnumOrHyphen(byte[] bytes, int from, int to) {
        int i = from;
        for (; i + Long.BYTES <= to; i += Long.BYTES) {
            long word = (long) WORDS.get(bytes, i);
            // A byte with its high bit set is non-ASCII, which no label may contain. Rejecting it here
            // also keeps every byte below 128, which is what makes atLeast exact.
            if ((word & HIGH) != 0) {
                return false;
            }
            long accepted = inRange(word, '0', '9')
                    | inRange(word, 'A', 'Z')
                    | inRange(word, 'a', 'z')
                    | isByte(word, '-');
            if (accepted != HIGH) {
                return false;
            }
        }
        for (; i < to; i++) {
            int b = bytes[i] & 0xFF;
            if (b != '-' && !isAlnum(b)) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if every byte in {@code [from, to)} is a lowercase letter, a digit, or {@code '-'}.
     *
     * <p>S3 bucket names reject uppercase, unlike host labels.
     *
     * @param bytes the bytes to scan.
     * @param from inclusive start.
     * @param to exclusive end.
     * @return true if every byte is in the class.
     */
    public static boolean allLowerAlnumOrHyphen(byte[] bytes, int from, int to) {
        int i = from;
        for (; i + Long.BYTES <= to; i += Long.BYTES) {
            long word = (long) WORDS.get(bytes, i);
            if ((word & HIGH) != 0) {
                return false;
            }
            long accepted = inRange(word, '0', '9') | inRange(word, 'a', 'z') | isByte(word, '-');
            if (accepted != HIGH) {
                return false;
            }
        }
        for (; i < to; i++) {
            int b = bytes[i] & 0xFF;
            if (b != '-' && !isLowerAlnum(b)) {
                return false;
            }
        }
        return true;
    }

    /** True if the byte is a letter of either case or a digit. */
    public static boolean isAlnum(int b) {
        return Integer.compareUnsigned(b - '0', 9) <= 0
                || Integer.compareUnsigned((b | 0x20) - 'a', 25) <= 0;
    }

    /** True if the byte is a lowercase letter or a digit. */
    public static boolean isLowerAlnum(int b) {
        return Integer.compareUnsigned(b - '0', 9) <= 0 || Integer.compareUnsigned(b - 'a', 25) <= 0;
    }

    /**
     * Sets the high bit of each byte that is {@code >= n}, for {@code 0 <= n <= 128}.
     *
     * <p>Setting every lane's guard bit first is what makes this exact. Subtracting straight from the
     * word lets a lane that underflows borrow from the lane above and corrupt that lane's answer —
     * testing {@code '-'} (0x2D) against {@code '0'} (0x30) underflows, which silently broke the
     * neighbouring byte in the first version of this code. With the guard bit set each lane holds
     * {@code 0x80 + b}, so for any {@code n <= 0x80} the subtraction stays inside its lane and the
     * lane's high bit survives exactly when {@code b >= n}.
     *
     * <p>Callers must already have rejected words with a high bit set, so every {@code b} is below 128.
     */
    private static long atLeast(long word, int n) {
        return ((word | HIGH) - ONES * n) & HIGH;
    }

    /** Sets the high bit of each byte in {@code [lo, hi]}. */
    private static long inRange(long word, int lo, int hi) {
        return atLeast(word, lo) & ~atLeast(word, hi + 1) & HIGH;
    }

    /** Sets the high bit of each byte equal to {@code b}. */
    private static long isByte(long word, int b) {
        // XOR maps matching bytes to zero; b is ASCII and the word is known ASCII, so lanes stay < 128.
        return ~atLeast(word ^ (ONES * b), 1) & HIGH;
    }
}
