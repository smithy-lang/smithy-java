/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import software.amazon.smithy.java.http.api.HeaderNames;

/**
 * HPACK static table from RFC 7541 Appendix A.
 *
 * <p>The static table consists of 61 predefined header field entries, where index 0 is unused.
 *
 * <p>This implementation uses length-based bucketing for fast lookups with zero per-lookup
 * allocations. Entries are grouped by header name length, so lookups only scan candidates
 * with matching name length (typically 1-3 entries per bucket).
 *
 * <p>Header names use constants from {@link HeaderNames} to enable pointer comparisons.
 */
final class StaticTable {

    private StaticTable() {}

    /**
     * Number of entries in the static table.
     */
    static final int SIZE = 61;

    private static final String[] NAMES = new String[SIZE + 1];
    private static final String[] VALUES = new String[SIZE + 1];

    private static void entry(int index, String name, String value) {
        NAMES[index] = name;
        VALUES[index] = value;
    }

    static {
        // RFC 7541 Appendix A - Static Table Definition
        entry(1, HeaderNames.PSEUDO_AUTHORITY, "");
        entry(2, HeaderNames.PSEUDO_METHOD, "GET");
        entry(3, HeaderNames.PSEUDO_METHOD, "POST");
        entry(4, HeaderNames.PSEUDO_PATH, "/");
        entry(5, HeaderNames.PSEUDO_PATH, "/index.html");
        entry(6, HeaderNames.PSEUDO_SCHEME, "http");
        entry(7, HeaderNames.PSEUDO_SCHEME, "https");
        entry(8, HeaderNames.PSEUDO_STATUS, "200");
        entry(9, HeaderNames.PSEUDO_STATUS, "204");
        entry(10, HeaderNames.PSEUDO_STATUS, "206");
        entry(11, HeaderNames.PSEUDO_STATUS, "304");
        entry(12, HeaderNames.PSEUDO_STATUS, "400");
        entry(13, HeaderNames.PSEUDO_STATUS, "404");
        entry(14, HeaderNames.PSEUDO_STATUS, "500");
        entry(15, HeaderNames.ACCEPT_CHARSET, "");
        entry(16, HeaderNames.ACCEPT_ENCODING, "gzip, deflate");
        entry(17, HeaderNames.ACCEPT_LANGUAGE, "");
        entry(18, HeaderNames.ACCEPT_RANGES, "");
        entry(19, HeaderNames.ACCEPT, "");
        entry(20, HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "");
        entry(21, HeaderNames.AGE, "");
        entry(22, HeaderNames.ALLOW, "");
        entry(23, HeaderNames.AUTHORIZATION, "");
        entry(24, HeaderNames.CACHE_CONTROL, "");
        entry(25, HeaderNames.CONTENT_DISPOSITION, "");
        entry(26, HeaderNames.CONTENT_ENCODING, "");
        entry(27, HeaderNames.CONTENT_LANGUAGE, "");
        entry(28, HeaderNames.CONTENT_LENGTH, "");
        entry(29, HeaderNames.CONTENT_LOCATION, "");
        entry(30, HeaderNames.CONTENT_RANGE, "");
        entry(31, HeaderNames.CONTENT_TYPE, "");
        entry(32, HeaderNames.COOKIE, "");
        entry(33, HeaderNames.DATE, "");
        entry(34, HeaderNames.ETAG, "");
        entry(35, HeaderNames.EXPECT, "");
        entry(36, HeaderNames.EXPIRES, "");
        entry(37, HeaderNames.FROM, "");
        entry(38, HeaderNames.HOST, "");
        entry(39, HeaderNames.IF_MATCH, "");
        entry(40, HeaderNames.IF_MODIFIED_SINCE, "");
        entry(41, HeaderNames.IF_NONE_MATCH, "");
        entry(42, HeaderNames.IF_RANGE, "");
        entry(43, HeaderNames.IF_UNMODIFIED_SINCE, "");
        entry(44, HeaderNames.LAST_MODIFIED, "");
        entry(45, HeaderNames.LINK, "");
        entry(46, HeaderNames.LOCATION, "");
        entry(47, HeaderNames.MAX_FORWARDS, "");
        entry(48, HeaderNames.PROXY_AUTHENTICATE, "");
        entry(49, HeaderNames.PROXY_AUTHORIZATION, "");
        entry(50, HeaderNames.RANGE, "");
        entry(51, HeaderNames.REFERER, "");
        entry(52, HeaderNames.REFRESH, "");
        entry(53, HeaderNames.RETRY_AFTER, "");
        entry(54, HeaderNames.SERVER, "");
        entry(55, HeaderNames.SET_COOKIE, "");
        entry(56, HeaderNames.STRICT_TRANSPORT_SECURITY, "");
        entry(57, HeaderNames.TRANSFER_ENCODING, "");
        entry(58, HeaderNames.USER_AGENT, "");
        entry(59, HeaderNames.VARY, "");
        entry(60, HeaderNames.VIA, "");
        entry(61, HeaderNames.WWW_AUTHENTICATE, "");
    }

    /**
     * Maximum header name length in the static table.
     */
    private static final int MAX_NAME_LEN;

    /**
     * Empty bucket for lengths with no entries (avoids null checks in lookups).
     */
    private static final int[] EMPTY_BUCKET = new int[0];

    /**
     * Buckets of static table indices grouped by header name length.
     * NAME_BUCKETS_BY_LEN[len] contains indices of entries whose name has that length.
     */
    private static final int[][] NAME_BUCKETS_BY_LEN;

    static {
        // Build length-based buckets for fast lookup
        int maxLen = 0;
        for (int i = 1; i <= SIZE; i++) {
            int len = NAMES[i].length();
            if (len > maxLen) {
                maxLen = len;
            }
        }
        MAX_NAME_LEN = maxLen;

        // Count entries per length
        int[] counts = new int[MAX_NAME_LEN + 1];
        for (int i = 1; i <= SIZE; i++) {
            counts[NAMES[i].length()]++;
        }

        // Allocate buckets
        int[][] buckets = new int[MAX_NAME_LEN + 1][];
        for (int len = 0; len <= MAX_NAME_LEN; len++) {
            buckets[len] = counts[len] > 0 ? new int[counts[len]] : EMPTY_BUCKET;
        }

        // Fill buckets
        int[] pos = new int[MAX_NAME_LEN + 1];
        for (int i = 1; i <= SIZE; i++) {
            int len = NAMES[i].length();
            buckets[len][pos[len]++] = i;
        }

        NAME_BUCKETS_BY_LEN = buckets;
    }

    /**
     * Get the header name at the given index.
     *
     * @param index 1-based index into static table
     * @return header name
     */
    static String getName(int index) {
        return NAMES[index];
    }

    /**
     * Get the header value at the given index.
     *
     * @param index 1-based index into static table
     * @return header value
     */
    static String getValue(int index) {
        return VALUES[index];
    }

    /**
     * Find index for a full match (name + value).
     *
     * @param name header name
     * @param value header value
     * @return index if found, -1 otherwise
     */
    static int findFullMatch(String name, String value) {
        int len = name.length();
        if (len > MAX_NAME_LEN) {
            return -1;
        }
        for (int idx : NAME_BUCKETS_BY_LEN[len]) {
            String entryName = NAMES[idx];
            if (name.equals(entryName) && value.equals(VALUES[idx])) {
                return idx;
            }
        }
        return -1;
    }

    /**
     * Find index for a name-only match.
     *
     * @param name header name
     * @return index of first entry with this name, -1 if not found
     */
    static int findNameMatch(String name) {
        int len = name.length();
        if (len <= MAX_NAME_LEN) {
            for (int idx : NAME_BUCKETS_BY_LEN[len]) {
                if (name.equals(NAMES[idx])) {
                    return idx;
                }
            }
        }
        return -1;
    }
}
