/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

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

    /**
     * Static table entries as pre-allocated HeaderField instances.
     * Index 0 is unused (indices are 1-based per RFC 7541).
     *
     * <p>Names use HeaderNameRegistry constants for pointer equality.
     */
    private static final HeaderField[] ENTRIES = {
            null, // Index 0 unused
            new HeaderField(HeaderNames.PSEUDO_AUTHORITY, ""), // 1
            new HeaderField(HeaderNames.PSEUDO_METHOD, "GET"), // 2
            new HeaderField(HeaderNames.PSEUDO_METHOD, "POST"), // 3
            new HeaderField(HeaderNames.PSEUDO_PATH, "/"), // 4
            new HeaderField(HeaderNames.PSEUDO_PATH, "/index.html"), // 5
            new HeaderField(HeaderNames.PSEUDO_SCHEME, "http"), // 6
            new HeaderField(HeaderNames.PSEUDO_SCHEME, "https"), // 7
            new HeaderField(HeaderNames.PSEUDO_STATUS, "200"), // 8
            new HeaderField(HeaderNames.PSEUDO_STATUS, "204"), // 9
            new HeaderField(HeaderNames.PSEUDO_STATUS, "206"), // 10
            new HeaderField(HeaderNames.PSEUDO_STATUS, "304"), // 11
            new HeaderField(HeaderNames.PSEUDO_STATUS, "400"), // 12
            new HeaderField(HeaderNames.PSEUDO_STATUS, "404"), // 13
            new HeaderField(HeaderNames.PSEUDO_STATUS, "500"), // 14
            new HeaderField(HeaderNames.ACCEPT_CHARSET, ""), // 15
            new HeaderField(HeaderNames.ACCEPT_ENCODING, "gzip, deflate"), // 16
            new HeaderField(HeaderNames.ACCEPT_LANGUAGE, ""), // 17
            new HeaderField(HeaderNames.ACCEPT_RANGES, ""), // 18
            new HeaderField(HeaderNames.ACCEPT, ""), // 19
            new HeaderField(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, ""), // 20
            new HeaderField(HeaderNames.AGE, ""), // 21
            new HeaderField(HeaderNames.ALLOW, ""), // 22
            new HeaderField(HeaderNames.AUTHORIZATION, ""), // 23
            new HeaderField(HeaderNames.CACHE_CONTROL, ""), // 24
            new HeaderField(HeaderNames.CONTENT_DISPOSITION, ""), // 25
            new HeaderField(HeaderNames.CONTENT_ENCODING, ""), // 26
            new HeaderField(HeaderNames.CONTENT_LANGUAGE, ""), // 27
            new HeaderField(HeaderNames.CONTENT_LENGTH, ""), // 28
            new HeaderField(HeaderNames.CONTENT_LOCATION, ""), // 29
            new HeaderField(HeaderNames.CONTENT_RANGE, ""), // 30
            new HeaderField(HeaderNames.CONTENT_TYPE, ""), // 31
            new HeaderField(HeaderNames.COOKIE, ""), // 32
            new HeaderField(HeaderNames.DATE, ""), // 33
            new HeaderField(HeaderNames.ETAG, ""), // 34
            new HeaderField(HeaderNames.EXPECT, ""), // 35
            new HeaderField(HeaderNames.EXPIRES, ""), // 36
            new HeaderField(HeaderNames.FROM, ""), // 37
            new HeaderField(HeaderNames.HOST, ""), // 38
            new HeaderField(HeaderNames.IF_MATCH, ""), // 39
            new HeaderField(HeaderNames.IF_MODIFIED_SINCE, ""), // 40
            new HeaderField(HeaderNames.IF_NONE_MATCH, ""), // 41
            new HeaderField(HeaderNames.IF_RANGE, ""), // 42
            new HeaderField(HeaderNames.IF_UNMODIFIED_SINCE, ""), // 43
            new HeaderField(HeaderNames.LAST_MODIFIED, ""), // 44
            new HeaderField(HeaderNames.LINK, ""), // 45
            new HeaderField(HeaderNames.LOCATION, ""), // 46
            new HeaderField(HeaderNames.MAX_FORWARDS, ""), // 47
            new HeaderField(HeaderNames.PROXY_AUTHENTICATE, ""), // 48
            new HeaderField(HeaderNames.PROXY_AUTHORIZATION, ""), // 49
            new HeaderField(HeaderNames.RANGE, ""), // 50
            new HeaderField(HeaderNames.REFERER, ""), // 51
            new HeaderField(HeaderNames.REFRESH, ""), // 52
            new HeaderField(HeaderNames.RETRY_AFTER, ""), // 53
            new HeaderField(HeaderNames.SERVER, ""), // 54
            new HeaderField(HeaderNames.SET_COOKIE, ""), // 55
            new HeaderField(HeaderNames.STRICT_TRANSPORT_SECURITY, ""), // 56
            new HeaderField(HeaderNames.TRANSFER_ENCODING, ""), // 57
            new HeaderField(HeaderNames.USER_AGENT, ""), // 58
            new HeaderField(HeaderNames.VARY, ""), // 59
            new HeaderField(HeaderNames.VIA, ""), // 60
            new HeaderField(HeaderNames.WWW_AUTHENTICATE, "") // 61
    };

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
     * Empty buckets use EMPTY_BUCKET to avoid null checks.
     */
    private static final int[][] NAME_BUCKETS_BY_LEN;

    static {
        // Find max name length
        int maxLen = 0;
        for (int i = 1; i <= SIZE; i++) {
            int len = ENTRIES[i].name().length();
            if (len > maxLen) {
                maxLen = len;
            }
        }
        MAX_NAME_LEN = maxLen;

        // First pass: count entries per length
        int[] counts = new int[MAX_NAME_LEN + 1];
        for (int i = 1; i <= SIZE; i++) {
            counts[ENTRIES[i].name().length()]++;
        }

        // Allocate buckets (empty bucket for lengths with no entries)
        int[][] buckets = new int[MAX_NAME_LEN + 1][];
        for (int len = 0; len <= MAX_NAME_LEN; len++) {
            buckets[len] = counts[len] > 0 ? new int[counts[len]] : EMPTY_BUCKET;
        }

        // Second pass: fill buckets
        int[] pos = new int[MAX_NAME_LEN + 1];
        for (int i = 1; i <= SIZE; i++) {
            int len = ENTRIES[i].name().length();
            buckets[len][pos[len]++] = i;
        }

        NAME_BUCKETS_BY_LEN = buckets;
    }

    /**
     * Get the header field at the given index.
     *
     * @param index 1-based index into static table
     * @return header field
     */
    static HeaderField get(int index) {
        return ENTRIES[index];
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
            HeaderField e = ENTRIES[idx];
            var entryName = e.name();
            if ((entryName == name || entryName.equals(name)) && e.value().equals(value)) {
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
                var entry = ENTRIES[idx];
                var entryName = entry.name();
                if (entryName == name || entryName.equals(name)) {
                    return idx;
                }
            }
        }
        return -1;
    }
}
