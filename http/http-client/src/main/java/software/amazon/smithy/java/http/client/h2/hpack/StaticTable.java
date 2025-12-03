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
     * Static table entries. Index 0 is unused (indices are 1-based).
     * Each entry is {name, value} where value may be empty string.
     */
    private static final String[][] ENTRIES = {
            null, // Index 0 unused
            {":authority", ""}, // 1
            {":method", "GET"}, // 2
            {":method", "POST"}, // 3
            {":path", "/"}, // 4
            {":path", "/index.html"}, // 5
            {":scheme", "http"}, // 6
            {":scheme", "https"}, // 7
            {":status", "200"}, // 8
            {":status", "204"}, // 9
            {":status", "206"}, // 10
            {":status", "304"}, // 11
            {":status", "400"}, // 12
            {":status", "404"}, // 13
            {":status", "500"}, // 14
            {"accept-charset", ""}, // 15
            {"accept-encoding", "gzip, deflate"}, // 16
            {"accept-language", ""}, // 17
            {"accept-ranges", ""}, // 18
            {"accept", ""}, // 19
            {"access-control-allow-origin", ""}, // 20
            {"age", ""}, // 21
            {"allow", ""}, // 22
            {"authorization", ""}, // 23
            {"cache-control", ""}, // 24
            {"content-disposition", ""}, // 25
            {"content-encoding", ""}, // 26
            {"content-language", ""}, // 27
            {"content-length", ""}, // 28
            {"content-location", ""}, // 29
            {"content-range", ""}, // 30
            {"content-type", ""}, // 31
            {"cookie", ""}, // 32
            {"date", ""}, // 33
            {"etag", ""}, // 34
            {"expect", ""}, // 35
            {"expires", ""}, // 36
            {"from", ""}, // 37
            {"host", ""}, // 38
            {"if-match", ""}, // 39
            {"if-modified-since", ""}, // 40
            {"if-none-match", ""}, // 41
            {"if-range", ""}, // 42
            {"if-unmodified-since", ""}, // 43
            {"last-modified", ""}, // 44
            {"link", ""}, // 45
            {"location", ""}, // 46
            {"max-forwards", ""}, // 47
            {"proxy-authenticate", ""}, // 48
            {"proxy-authorization", ""}, // 49
            {"range", ""}, // 50
            {"referer", ""}, // 51
            {"refresh", ""}, // 52
            {"retry-after", ""}, // 53
            {"server", ""}, // 54
            {"set-cookie", ""}, // 55
            {"strict-transport-security", ""}, // 56
            {"transfer-encoding", ""}, // 57
            {"user-agent", ""}, // 58
            {"vary", ""}, // 59
            {"via", ""}, // 60
            {"www-authenticate", ""} // 61
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
            int len = ENTRIES[i][0].length();
            if (len > maxLen) {
                maxLen = len;
            }
        }
        MAX_NAME_LEN = maxLen;

        // First pass: count entries per length
        int[] counts = new int[MAX_NAME_LEN + 1];
        for (int i = 1; i <= SIZE; i++) {
            counts[ENTRIES[i][0].length()]++;
        }

        // Allocate buckets (empty bucket for lengths with no entries)
        int[][] buckets = new int[MAX_NAME_LEN + 1][];
        for (int len = 0; len <= MAX_NAME_LEN; len++) {
            buckets[len] = counts[len] > 0 ? new int[counts[len]] : EMPTY_BUCKET;
        }

        // Second pass: fill buckets
        int[] pos = new int[MAX_NAME_LEN + 1];
        for (int i = 1; i <= SIZE; i++) {
            int len = ENTRIES[i][0].length();
            buckets[len][pos[len]++] = i;
        }

        NAME_BUCKETS_BY_LEN = buckets;
    }

    /**
     * Get the header name at the given index.
     *
     * @param index 1-based index into static table
     * @return header name
     * @throws IndexOutOfBoundsException if index is out of range
     */
    static String getName(int index) {
        if (index < 1 || index > SIZE) {
            throw new IndexOutOfBoundsException("Static table index out of range: " + index);
        }
        return ENTRIES[index][0];
    }

    /**
     * Get the header value at the given index.
     *
     * @param index 1-based index into static table
     * @return header value (may be empty string)
     * @throws IndexOutOfBoundsException if index is out of range
     */
    static String getValue(int index) {
        if (index < 1 || index > SIZE) {
            throw new IndexOutOfBoundsException("Static table index out of range: " + index);
        }
        return ENTRIES[index][1];
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
            String[] e = ENTRIES[idx];
            if (e[0].equals(name) && e[1].equals(value)) {
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
                if (ENTRIES[idx][0].equals(name)) {
                    return idx;
                }
            }
        }
        return -1;
    }
}
