/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import static java.lang.String.format;

final class HashUtil {
    public static final int CHUNK_SIZE = 8;

    /**
     * <b>READ THE FULL JAVADOC BEFORE USING THIS METHOD.</b>
     *
     * <p>This method is only guaranteed to return identical hashes for identical data within
     * a single process. The implementation of this method and the values it returns can change
     * without notice. Do not store the hashes returned by this method anywhere other than in memory.
     *
     * @param bytes bytes to hash
     * @return a hash
     */
    public static int hash(byte[] bytes) {
        return hash(bytes, 0, bytes.length);
    }

    public static int hash(byte[] bytes, int off, int len) {
        if (len < CHUNK_SIZE) return hashSmall(bytes, off, len);

        int hs0 = 1, hs1 = 1, hs2 = 1, hs3 = 1, hs4 = 1, hs5 = 1, hs6 = 1, hs7 = 1;
        int i = off;
        for (; i + CHUNK_SIZE <= off + len; i += CHUNK_SIZE) {
            hs0 = 31 * hs0 + bytes[i];
            hs1 = 31 * hs1 + bytes[i + 1];
            hs2 = 31 * hs2 + bytes[i + 2];
            hs3 = 31 * hs3 + bytes[i + 3];
            hs4 = 31 * hs4 + bytes[i + 4];
            hs5 = 31 * hs5 + bytes[i + 5];
            hs6 = 31 * hs6 + bytes[i + 6];
            hs7 = 31 * hs7 + bytes[i + 7];
        }

        return finishInline(bytes, i, off + len - i, hs0, hs1, hs2, hs3, hs4, hs5, hs6, hs7);
    }

    private static int finishInline(
        byte[] bytes,
        int i,
        int rem,
        int hs0,
        int hs1,
        int hs2,
        int hs3,
        int hs4,
        int hs5,
        int hs6,
        int hs7
    ) {
        switch (rem) {
            case 7:
                hs0 = 31 * hs0 + bytes[i++];
            case 6:
                hs1 = 31 * hs1 + bytes[i++];
            case 5:
                hs2 = 31 * hs2 + bytes[i++];
            case 4:
                hs3 = 31 * hs3 + bytes[i++];
            case 3:
                hs4 = 31 * hs4 + bytes[i++];
            case 2:
                hs5 = 31 * hs5 + bytes[i++];
            case 1:
                hs6 = 31 * hs6 + bytes[i];
            case 0:
                break;
        }

        return calculateHashInline(hs0, hs1, hs2, hs3, hs4, hs5, hs6, hs7);
    }

    static int update(int[] components, byte[] bytes, int off, int len) {
        int i = off;
        for (; i + CHUNK_SIZE <= off + len; i += CHUNK_SIZE) {
            for (int j = 0; j < CHUNK_SIZE; j++) {
                components[j] = 31 * components[j] + bytes[i + j];
            }
        }
        return i;
    }

    static void finish(int[] components, byte[] bytes, int off, int len) {
        if (len >= CHUNK_SIZE) {
            throw new IllegalArgumentException(format("update must receive fewer than %d bytes", CHUNK_SIZE));
        }

        switch (len) {
            case 7:
                components[0] = 31 * components[0] + bytes[off++];
            case 6:
                components[1] = 31 * components[1] + bytes[off++];
            case 5:
                components[2] = 31 * components[2] + bytes[off++];
            case 4:
                components[3] = 31 * components[3] + bytes[off++];
            case 3:
                components[4] = 31 * components[4] + bytes[off++];
            case 2:
                components[5] = 31 * components[5] + bytes[off++];
            case 1:
                components[6] = 31 * components[6] + bytes[off];
            case 0:
                break;
        }
    }

    static int calculateHashInline(int c1, int c2, int c3, int c4, int c5, int c6, int c7, int c8) {
        return c1 ^ c2 + c3 ^ c4 + c5 ^ c6 + c7 ^ c8;
    }

    static int hashSmall(byte[] b, int o, int l) {
        int res = 1;
        for (int i = o; i < o + l; i++) {
            res = 31 * res + b[i];
        }
        return res;
    }

    private HashUtil() {}
}
