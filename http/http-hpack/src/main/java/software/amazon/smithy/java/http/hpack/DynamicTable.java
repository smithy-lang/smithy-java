/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import java.util.ArrayList;

/**
 * HPACK dynamic table implementation from RFC 7541 Section 2.3.2.
 *
 * <p>The dynamic table is a FIFO queue of header field entries. New entries
 * are added at index 62 (lowest dynamic index), and older entries are evicted
 * first when the table size exceeds the maximum.
 *
 * <p>Dynamic table indices start at 62 (after the 61 static table entries).
 * Index 62 is the most recently added entry.
 *
 * <p>Entries are stored as interleaved name/value pairs. We use an ArrayList
 * with reverse indexing: new entries append to the end (O(1)), and index 62
 * maps to the last pair. Eviction removes from the front.
 *
 * <p>Header names must be lowercase as required by HTTP/2 (RFC 7540 Section 8.1.2).
 */
final class DynamicTable {

    /**
     * Each entry has 32 bytes of overhead per RFC 7541 Section 4.1.
     */
    private static final int ENTRY_OVERHEAD = 32;

    // Interleaved storage: [name0, value0, name1, value1, ...]
    // Newest entries at the END (reverse of logical HPACK order)
    private final ArrayList<String> entries = new ArrayList<>();
    private int numEntries = 0;
    private int currentSize = 0;
    private int maxSize;

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
        return numEntries;
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

        // RFC 7541 Section 4.4: "an attempt to add an entry larger than the maximum size
        // causes the table to be emptied of all existing entries and results in an empty table"
        if (entrySize > maxSize) {
            clear();
            return;
        }

        // Evict entries until there's room
        evictToSize(maxSize - entrySize);

        // Append to end (newest = highest array index, but lowest HPACK index)
        entries.add(name);
        entries.add(value);
        currentSize += entrySize;
        numEntries++;
    }

    /**
     * Get header name at the given index.
     *
     * @param index dynamic table index (62 + offset)
     * @return header name
     * @throws IndexOutOfBoundsException if index is out of range
     */
    String getName(int index) {
        return entries.get(toArrayIndex(index));
    }

    /**
     * Get header value at the given index.
     *
     * @param index dynamic table index (62 + offset)
     * @return header value
     * @throws IndexOutOfBoundsException if index is out of range
     */
    String getValue(int index) {
        return entries.get(toArrayIndex(index) + 1);
    }

    /**
     * Convert HPACK index to array index.
     * HPACK index 62 = newest entry = last pair in array.
     */
    private int toArrayIndex(int hpackIndex) {
        int offset = hpackIndex - StaticTable.SIZE - 1;
        if (offset < 0 || offset >= numEntries) {
            throw new IndexOutOfBoundsException("Dynamic table index out of range: "
                    + hpackIndex + " (table has " + numEntries + " entries)");
        }
        // Reverse: offset 0 -> last pair, offset 1 -> second-to-last, etc.
        return entries.size() - 2 - (offset * 2);
    }

    /**
     * Convert an array index to an HPACK dynamic table index.
     */
    private int toHpackIndex(int arrayIndex) {
        return StaticTable.SIZE + 1 + (entries.size() - 2 - arrayIndex) / 2;
    }

    /**
     * Find the index of a full match (name + value) in the dynamic table.
     *
     * @param name header name
     * @param value header value
     * @return dynamic table index (62+) if found, -1 otherwise
     */
    int findFullMatch(String name, String value) {
        // Search from newest (end) to oldest (start)
        for (int i = entries.size() - 2; i >= 0; i -= 2) {
            if (entries.get(i).equals(name) && entries.get(i + 1).equals(value)) {
                return toHpackIndex(i);
            }
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
        for (int i = entries.size() - 2; i >= 0; i -= 2) {
            if (entries.get(i).equals(name)) {
                return toHpackIndex(i);
            }
        }
        return -1;
    }

    /**
     * Clear all entries from the table.
     */
    void clear() {
        entries.clear();
        currentSize = 0;
        numEntries = 0;
    }

    /**
     * Calculate the size of an entry per RFC 7541 Section 4.1.
     * Size = length(name) + length(value) + 32
     *
     * @param name header name
     * @param value header value
     * @return entry size in bytes
     */
    static int entrySize(String name, String value) {
        return name.length() + value.length() + ENTRY_OVERHEAD;
    }

    private void evictToSize(int targetSize) {
        int removeCount = 0;
        int removedSize = 0;

        // Efficiently resize in one-shot.
        while (currentSize - removedSize > targetSize && removeCount < entries.size()) {
            removedSize += entrySize(entries.get(removeCount), entries.get(removeCount + 1));
            removeCount += 2;
        }

        if (removeCount > 0) {
            entries.subList(0, removeCount).clear();
            currentSize -= removedSize;
            numEntries -= removeCount / 2;
        }
    }
}
