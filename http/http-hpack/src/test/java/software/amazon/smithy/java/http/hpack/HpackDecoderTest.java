/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HpackDecoderTest {

    @Test
    void decodesIndexedNameFromDynamicTable() throws IOException {
        var encoder = new HpackEncoder(4096);
        var decoder = new HpackDecoder(4096);

        // First block - add custom header to dynamic table
        var out1 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out1);
        encoder.encodeHeader(out1, "x-custom-name", "value1", false);
        decoder.decode(out1.toByteArray());

        // Second block - use indexed name from dynamic table with new value
        var out2 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out2);
        encoder.encodeHeader(out2, "x-custom-name", "value2", false);
        List<String> headers = decoder.decode(out2.toByteArray());

        assertEquals(2, headers.size()); // name, value
        assertEquals("x-custom-name", headers.get(0));
        assertEquals("value2", headers.get(1));
    }

    @Test
    void throwsOnStringLengthExceedsBuffer() {
        // Craft a malformed HPACK block: literal with indexing, new name
        // 0x40 = literal with indexing, name index 0 (new name)
        // 0x05 = string length 5 (but we only provide 2 bytes)
        byte[] malformed = {0x40, 0x05, 'a', 'b'};
        var decoder = new HpackDecoder(4096);

        assertThrows(IOException.class, () -> decoder.decode(malformed));
    }

    @Test
    void throwsOnDynamicTableSizeUpdateAfterHeader() {
        // Craft: indexed header (0x82 = :method GET), then table size update (0x20)
        byte[] malformed = {(byte) 0x82, 0x20};
        var decoder = new HpackDecoder(4096);

        IOException ex = assertThrows(IOException.class, () -> decoder.decode(malformed));
        assertTrue(ex.getMessage().contains("beginning of header block"));
    }

    @Test
    void throwsOnHeaderListExceedsMaxSize() {
        // Create decoder with small max header list size
        var decoder = new HpackDecoder(4096, 50);

        // Encode a header that exceeds the limit (name + value + 32 overhead)
        var encoder = new HpackEncoder(4096);
        var out = new ByteArrayOutputStream();
        try {
            encoder.beginHeaderBlock(out);
            encoder.encodeHeader(out, "x-long-header-name", "this-is-a-long-value", false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        IOException ex = assertThrows(IOException.class, () -> decoder.decode(out.toByteArray()));
        assertTrue(ex.getMessage().contains("exceeds maximum size"));
    }

    @Test
    void throwsOnUppercaseHeaderName() {
        // Craft: literal without indexing (0x00), name length 4, "Test" (uppercase T)
        byte[] malformed = {0x00, 0x04, 'T', 'e', 's', 't', 0x05, 'v', 'a', 'l', 'u', 'e'};
        var decoder = new HpackDecoder(4096);

        IOException ex = assertThrows(IOException.class, () -> decoder.decode(malformed));
        assertTrue(ex.getMessage().contains("uppercase"));
    }

    @Test
    void allowsTableSizeUpdateAtBeginning() throws IOException {
        // Table size update (0x20 = size 0) followed by indexed header (0x82 = :method GET)
        byte[] valid = {0x20, (byte) 0x82};
        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(valid);

        assertEquals(2, headers.size());
        assertEquals(":method", headers.get(0));
        assertEquals("GET", headers.get(1));
    }

    @Test
    void decodesLiteralNeverIndexed() throws IOException {
        // 0x10 = literal never indexed, name index 0
        byte[] data = {0x10, 0x04, 't', 'e', 's', 't', 0x05, 'v', 'a', 'l', 'u', 'e'};
        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(data);

        assertEquals(2, headers.size());
        assertEquals("test", headers.get(0));
        assertEquals("value", headers.get(1));
    }

    @Test
    void decodesLiteralWithoutIndexing() throws IOException {
        // 0x00 = literal without indexing, name index 0
        byte[] data = {0x00, 0x04, 't', 'e', 's', 't', 0x05, 'v', 'a', 'l', 'u', 'e'};
        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(data);

        assertEquals(2, headers.size());
        assertEquals("test", headers.get(0));
        assertEquals("value", headers.get(1));
    }

    @Test
    void throwsOnInvalidIndex() {
        // 0x80 = indexed with index 0 (invalid)
        byte[] malformed = {(byte) 0x80};
        var decoder = new HpackDecoder(4096);

        assertThrows(IOException.class, () -> decoder.decode(malformed));
    }

    @Test
    void throwsOnIncompleteInteger() {
        // 0xff = indexed with index >= 127, needs continuation byte
        byte[] malformed = {(byte) 0xff};
        var decoder = new HpackDecoder(4096);

        assertThrows(IOException.class, () -> decoder.decode(malformed));
    }

    @Test
    void throwsOnIntegerOverflow() {
        // Craft an integer that would overflow (too many continuation bytes)
        byte[] malformed = {
                (byte) 0xff, // indexed, index >= 127
                (byte) 0xff,
                (byte) 0xff,
                (byte) 0xff,
                (byte) 0xff,
                (byte) 0x0f
        };

        var decoder = new HpackDecoder(4096);

        assertThrows(IOException.class, () -> decoder.decode(malformed));
    }
}
