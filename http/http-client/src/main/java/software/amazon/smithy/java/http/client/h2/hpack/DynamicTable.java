/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * HPACK dynamic table implementation from RFC 7541 Section 2.3.2.
 *
 * <p>The dynamic table is a FIFO queue of header field entries. New entries
 * are added at the front (lowest index), and entries are evicted from the
 * back (highest index) when the table size exceeds the maximum.
 *
 * <p>Dynamic table indices start at 62 (after the 61 static table entries).
 * Index 62 is the most recently added entry.
 *
 * <p>This implementation uses linear scans for lookups. The typical dynamic table
 * size is small (< 128 entries with default 4KB limit) so linear scans have good
 * cache locality and avoid the overhead of maintaining index maps that shift on
 * every add operation.
 *
 * <p>Header names must be lowercase as required by HTTP/2 (RFC 7540 Section 8.1.2).
 * Name matching uses case-sensitive String.equals().
 */
final class DynamicTable {

    /**
     * Each entry has 32 bytes of overhead.
     */
    private static final int ENTRY_OVERHEAD = 32;

    private final Deque<HeaderField> entries = new ArrayDeque<>();
    private int currentSize = 0;
    private int maxSize;

    /**
     * Header field entry with cached size to avoid recomputation during eviction.
     */
    record HeaderField(String name, String value, int size) {}

    /**
     * Create a dynamic table with the given maximum size.
     *
     * @param maxSize maximum table size in bytes
     */
    DynamicTable(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Get the current number of entries in the table.
     *
     * @return entry count
     */
    int length() {
        return entries.size();
    }

    /**
     * Get the current size of the table in bytes.
     *
     * @return current size
     */
    int size() {
        return currentSize;
    }

    /**
     * Get the maximum size of the table in bytes.
     *
     * @return maximum size
     */
    int maxSize() {
        return maxSize;
    }

    /**
     * Set a new maximum table size.
     *
     * <p>If the new size is smaller than current size, entries are evicted.
     *
     * @param newMaxSize new maximum size in bytes
     */
    void setMaxSize(int newMaxSize) {
        if (newMaxSize == maxSize) {
            return;
        }
        this.maxSize = newMaxSize;
        evictToSize(newMaxSize);
    }

    /**
     * Add a new entry to the dynamic table.
     *
     * <p>The entry is added at index 62 (first dynamic index).
     * Existing entries shift to higher indices.
     *
     * @param name header name
     * @param value header value
     */
    void add(String name, String value) {
        int entrySize = entrySize(name, value);

        // If entry does not fit even in empty table, don't add it, but still evict to make room as per spec
        if (entrySize > maxSize) {
            clear();
            return;
        }

        // Evict entries until there's room
        evictToSize(maxSize - entrySize);

        // Add new entry at front with cached size
        entries.addFirst(new HeaderField(name, value, entrySize));
        currentSize += entrySize;
    }

    /**
     * Get header field at the given index.
     *
     * @param index dynamic table index (62 + offset)
     * @return header field
     * @throws IndexOutOfBoundsException if index is out of range
     */
    HeaderField get(int index) {
        int offset = index - StaticTable.SIZE - 1; // Convert to 0-based offset
        if (offset < 0 || offset >= entries.size()) {
            throw new IndexOutOfBoundsException("Dynamic table index out of range: "
                    + index + " (table has " + entries.size() + " entries)");
        }

        // Linear scan to find entry at offset
        int i = 0;
        for (HeaderField field : entries) {
            if (i++ == offset) {
                return field;
            }
        }

        // Should never reach here given bounds check above
        throw new AssertionError("Unreachable: offset in range but entry not found");
    }

    /**
     * Find the index of a full match (name + value) in the dynamic table.
     *
     * @param name header name
     * @param value header value
     * @return dynamic table index (62+) if found, -1 otherwise
     */
    int findFullMatch(String name, String value) {
        int index = StaticTable.SIZE + 1;
        for (HeaderField field : entries) {
            if (field.name().equals(name) && field.value().equals(value)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /**
     * Find the index of a name-only match in the dynamic table.
     *
     * @param name header name
     * @return dynamic table index (62+) if found, -1 otherwise
     */
    int findNameMatch(String name) {
        int index = StaticTable.SIZE + 1;
        for (HeaderField field : entries) {
            if (field.name().equals(name)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /**
     * Clear all entries from the table.
     */
    void clear() {
        entries.clear();
        currentSize = 0;
    }

    /**
     * Calculate the size of an entry per RFC 7541 Section 4.1.
     * Size = length(name) in octets + length(value) in octets + 32
     *
     * <p>HTTP/2 header names are lowercase ASCII, and values are effectively ASCII/Latin-1,
     * so we count chars as bytes directly (avoids getBytes allocation per insertion).
     */
    static int entrySize(String name, String value) {
        return name.length() + value.length() + ENTRY_OVERHEAD;
    }

    private void evictToSize(int targetSize) {
        while (currentSize > targetSize && !entries.isEmpty()) {
            HeaderField evicted = entries.removeLast();
            currentSize -= evicted.size();
        }
    }
}
