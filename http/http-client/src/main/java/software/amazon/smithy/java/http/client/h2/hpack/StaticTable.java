/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

/**
 * HPACK static table from RFC 7541 Appendix A.
 *
 * <p>The static table consists of 61 predefined header field entries, where index 0 is unused.
 *
 * <p>This implementation uses length-based bucketing for fast lookups with zero per-lookup
 * allocations. Entries are grouped by header name length, so lookups only scan candidates
 * with matching name length (typically 1-3 entries per bucket).
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
     */
    private static final HeaderField[] ENTRIES = {
            null, // Index 0 unused
            new HeaderField(":authority", ""), // 1
            new HeaderField(":method", "GET"), // 2
            new HeaderField(":method", "POST"), // 3
            new HeaderField(":path", "/"), // 4
            new HeaderField(":path", "/index.html"), // 5
            new HeaderField(":scheme", "http"), // 6
            new HeaderField(":scheme", "https"), // 7
            new HeaderField(":status", "200"), // 8
            new HeaderField(":status", "204"), // 9
            new HeaderField(":status", "206"), // 10
            new HeaderField(":status", "304"), // 11
            new HeaderField(":status", "400"), // 12
            new HeaderField(":status", "404"), // 13
            new HeaderField(":status", "500"), // 14
            new HeaderField("accept-charset", ""), // 15
            new HeaderField("accept-encoding", "gzip, deflate"), // 16
            new HeaderField("accept-language", ""), // 17
            new HeaderField("accept-ranges", ""), // 18
            new HeaderField("accept", ""), // 19
            new HeaderField("access-control-allow-origin", ""), // 20
            new HeaderField("age", ""), // 21
            new HeaderField("allow", ""), // 22
            new HeaderField("authorization", ""), // 23
            new HeaderField("cache-control", ""), // 24
            new HeaderField("content-disposition", ""), // 25
            new HeaderField("content-encoding", ""), // 26
            new HeaderField("content-language", ""), // 27
            new HeaderField("content-length", ""), // 28
            new HeaderField("content-location", ""), // 29
            new HeaderField("content-range", ""), // 30
            new HeaderField("content-type", ""), // 31
            new HeaderField("cookie", ""), // 32
            new HeaderField("date", ""), // 33
            new HeaderField("etag", ""), // 34
            new HeaderField("expect", ""), // 35
            new HeaderField("expires", ""), // 36
            new HeaderField("from", ""), // 37
            new HeaderField("host", ""), // 38
            new HeaderField("if-match", ""), // 39
            new HeaderField("if-modified-since", ""), // 40
            new HeaderField("if-none-match", ""), // 41
            new HeaderField("if-range", ""), // 42
            new HeaderField("if-unmodified-since", ""), // 43
            new HeaderField("last-modified", ""), // 44
            new HeaderField("link", ""), // 45
            new HeaderField("location", ""), // 46
            new HeaderField("max-forwards", ""), // 47
            new HeaderField("proxy-authenticate", ""), // 48
            new HeaderField("proxy-authorization", ""), // 49
            new HeaderField("range", ""), // 50
            new HeaderField("referer", ""), // 51
            new HeaderField("refresh", ""), // 52
            new HeaderField("retry-after", ""), // 53
            new HeaderField("server", ""), // 54
            new HeaderField("set-cookie", ""), // 55
            new HeaderField("strict-transport-security", ""), // 56
            new HeaderField("transfer-encoding", ""), // 57
            new HeaderField("user-agent", ""), // 58
            new HeaderField("vary", ""), // 59
            new HeaderField("via", ""), // 60
            new HeaderField("www-authenticate", "") // 61
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
            if (e.name().equals(name) && e.value().equals(value)) {
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
                if (ENTRIES[idx].name().equals(name)) {
                    return idx;
                }
            }
        }
        return -1;
    }
}
