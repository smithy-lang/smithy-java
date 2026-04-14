/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import software.amazon.smithy.java.http.api.HeaderName;

/**
 * HPACK static table from RFC 7541 Appendix A.
 *
 * <p>The static table consists of 61 predefined header field entries, where index 0 is unused.
 *
 * <p>This implementation uses length-based bucketing for fast lookups with zero per-lookup
 * allocations. Entries are grouped by header name length, so lookups only scan candidates
 * with matching name length (typically 1-3 entries per bucket).
 *
 * <p>Header names use constants from {@link HeaderName} to enable pointer comparisons.
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
        entry(1, HeaderName.PSEUDO_AUTHORITY.name(), "");
        entry(2, HeaderName.PSEUDO_METHOD.name(), "GET");
        entry(3, HeaderName.PSEUDO_METHOD.name(), "POST");
        entry(4, HeaderName.PSEUDO_PATH.name(), "/");
        entry(5, HeaderName.PSEUDO_PATH.name(), "/index.html");
        entry(6, HeaderName.PSEUDO_SCHEME.name(), "http");
        entry(7, HeaderName.PSEUDO_SCHEME.name(), "https");
        entry(8, HeaderName.PSEUDO_STATUS.name(), "200");
        entry(9, HeaderName.PSEUDO_STATUS.name(), "204");
        entry(10, HeaderName.PSEUDO_STATUS.name(), "206");
        entry(11, HeaderName.PSEUDO_STATUS.name(), "304");
        entry(12, HeaderName.PSEUDO_STATUS.name(), "400");
        entry(13, HeaderName.PSEUDO_STATUS.name(), "404");
        entry(14, HeaderName.PSEUDO_STATUS.name(), "500");
        entry(15, HeaderName.ACCEPT_CHARSET.name(), "");
        entry(16, HeaderName.ACCEPT_ENCODING.name(), "gzip, deflate");
        entry(17, HeaderName.ACCEPT_LANGUAGE.name(), "");
        entry(18, HeaderName.ACCEPT_RANGES.name(), "");
        entry(19, HeaderName.ACCEPT.name(), "");
        entry(20, HeaderName.ACCESS_CONTROL_ALLOW_ORIGIN.name(), "");
        entry(21, HeaderName.AGE.name(), "");
        entry(22, HeaderName.ALLOW.name(), "");
        entry(23, HeaderName.AUTHORIZATION.name(), "");
        entry(24, HeaderName.CACHE_CONTROL.name(), "");
        entry(25, HeaderName.CONTENT_DISPOSITION.name(), "");
        entry(26, HeaderName.CONTENT_ENCODING.name(), "");
        entry(27, HeaderName.CONTENT_LANGUAGE.name(), "");
        entry(28, HeaderName.CONTENT_LENGTH.name(), "");
        entry(29, HeaderName.CONTENT_LOCATION.name(), "");
        entry(30, HeaderName.CONTENT_RANGE.name(), "");
        entry(31, HeaderName.CONTENT_TYPE.name(), "");
        entry(32, HeaderName.COOKIE.name(), "");
        entry(33, HeaderName.DATE.name(), "");
        entry(34, HeaderName.ETAG.name(), "");
        entry(35, HeaderName.EXPECT.name(), "");
        entry(36, HeaderName.EXPIRES.name(), "");
        entry(37, HeaderName.FROM.name(), "");
        entry(38, HeaderName.HOST.name(), "");
        entry(39, HeaderName.IF_MATCH.name(), "");
        entry(40, HeaderName.IF_MODIFIED_SINCE.name(), "");
        entry(41, HeaderName.IF_NONE_MATCH.name(), "");
        entry(42, HeaderName.IF_RANGE.name(), "");
        entry(43, HeaderName.IF_UNMODIFIED_SINCE.name(), "");
        entry(44, HeaderName.LAST_MODIFIED.name(), "");
        entry(45, HeaderName.LINK.name(), "");
        entry(46, HeaderName.LOCATION.name(), "");
        entry(47, HeaderName.MAX_FORWARDS.name(), "");
        entry(48, HeaderName.PROXY_AUTHENTICATE.name(), "");
        entry(49, HeaderName.PROXY_AUTHORIZATION.name(), "");
        entry(50, HeaderName.RANGE.name(), "");
        entry(51, HeaderName.REFERER.name(), "");
        entry(52, HeaderName.REFRESH.name(), "");
        entry(53, HeaderName.RETRY_AFTER.name(), "");
        entry(54, HeaderName.SERVER.name(), "");
        entry(55, HeaderName.SET_COOKIE.name(), "");
        entry(56, HeaderName.STRICT_TRANSPORT_SECURITY.name(), "");
        entry(57, HeaderName.TRANSFER_ENCODING.name(), "");
        entry(58, HeaderName.USER_AGENT.name(), "");
        entry(59, HeaderName.VARY.name(), "");
        entry(60, HeaderName.VIA.name(), "");
        entry(61, HeaderName.WWW_AUTHENTICATE.name(), "");
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
