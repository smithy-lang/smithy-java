/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DynamicTableTest {

    @Test
    void addsEntry() {
        var table = new DynamicTable(4096);

        table.add("name", "value");

        assertEquals(1, table.length());
        assertEquals("name", table.get(62).name());
        assertEquals("value", table.get(62).value());
    }

    @Test
    void calculatesEntrySize() {
        // name(4) + value(5) + 32 overhead = 41
        int size = DynamicTable.entrySize("name", "value");

        assertEquals(41, size);
    }

    @Test
    void tracksTotalSize() {
        var table = new DynamicTable(4096);

        table.add("name", "value"); // 4 + 5 + 32 = 41

        assertEquals(41, table.size());
    }

    @Test
    void evictsOldestWhenFull() {
        // Table size 100, each entry ~41 bytes, so fits 2 entries
        var table = new DynamicTable(100);

        table.add("first", "value"); // 5 + 5 + 32 = 42
        table.add("second", "value"); // 6 + 5 + 32 = 43
        // Total = 85, fits
        assertEquals(2, table.length());

        table.add("third", "value"); // 5 + 5 + 32 = 42
        // Would be 127, exceeds 100, so evict oldest

        assertEquals(2, table.length());
        assertEquals("third", table.get(62).name());
        assertEquals("second", table.get(63).name());
    }

    @Test
    void clearsWhenEntryTooLarge() {
        var table = new DynamicTable(50);
        table.add("a", "b"); // 1 + 1 + 32 = 34

        // Entry larger than max table size
        table.add("verylongname", "verylongvalue"); // 12 + 13 + 32 = 57 > 50

        assertEquals(0, table.length());
        assertEquals(0, table.size());
    }

    @Test
    void setMaxSizeEvicts() {
        var table = new DynamicTable(4096);
        table.add("name1", "value1"); // 5 + 6 + 32 = 43
        table.add("name2", "value2"); // 5 + 6 + 32 = 43
        assertEquals(2, table.length());

        table.setMaxSize(50); // Only room for 1 entry

        assertEquals(1, table.length());
        assertEquals("name2", table.get(62).name());
    }

    @Test
    void setMaxSizeToZeroClears() {
        var table = new DynamicTable(4096);
        table.add("name", "value");

        table.setMaxSize(0);

        assertEquals(0, table.length());
    }

    @Test
    void getThrowsOnInvalidIndex() {
        var table = new DynamicTable(4096);
        table.add("name", "value");

        assertThrows(IndexOutOfBoundsException.class, () -> table.get(61)); // Below dynamic range
        assertThrows(IndexOutOfBoundsException.class, () -> table.get(63)); // Only 1 entry at 62
    }

    @Test
    void findFullMatchReturnsIndex() {
        var table = new DynamicTable(4096);
        table.add("first", "value1");
        table.add("second", "value2");

        int index = table.findFullMatch("first", "value1");

        assertEquals(63, index); // second entry (first is at 62)
    }

    @Test
    void findFullMatchReturnsNegativeWhenNotFound() {
        var table = new DynamicTable(4096);
        table.add("name", "value");

        assertEquals(-1, table.findFullMatch("name", "other"));
        assertEquals(-1, table.findFullMatch("other", "value"));
    }

    @Test
    void findNameMatchReturnsIndex() {
        var table = new DynamicTable(4096);
        table.add("first", "value1");
        table.add("second", "value2");

        int index = table.findNameMatch("first");

        assertEquals(63, index);
    }

    @Test
    void findNameMatchReturnsNegativeWhenNotFound() {
        var table = new DynamicTable(4096);
        table.add("name", "value");

        assertEquals(-1, table.findNameMatch("other"));
    }

    @Test
    void clearRemovesAllEntries() {
        var table = new DynamicTable(4096);
        table.add("name1", "value1");
        table.add("name2", "value2");

        table.clear();

        assertEquals(0, table.length());
        assertEquals(0, table.size());
    }

    @Test
    void maxSizeReturnsConfiguredMax() {
        var table = new DynamicTable(1234);

        assertEquals(1234, table.maxSize());
    }

    @Test
    void indicesShiftOnAdd() {
        var table = new DynamicTable(4096);
        table.add("first", "v");
        assertEquals(62, table.findFullMatch("first", "v"));

        table.add("second", "v");
        assertEquals(62, table.findFullMatch("second", "v"));
        assertEquals(63, table.findFullMatch("first", "v")); // shifted
    }
}
