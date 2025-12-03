/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * HPACK encoder for HTTP/2 header compression (RFC 7541).
 *
 * <p>Thread safety: This class is NOT thread-safe. Each HTTP/2 connection should have its own encoder instance.
 */
public final class HpackEncoder {

    // Headers that should never be indexed (sensitive data)
    private static final Set<String> NEVER_INDEX_HEADERS = Set.of(
            "authorization",
            "cookie",
            "proxy-authorization",
            "set-cookie");

    private final DynamicTable dynamicTable;
    private final boolean useHuffman;

    // Track pending table size update to emit at start of next header block (RFC 7541 Section 4.2)
    // -1 means no update pending
    private int pendingTableSizeUpdate = -1;

    /**
     * Create an encoder with the given maximum dynamic table size.
     *
     * @param maxTableSize maximum dynamic table size in bytes
     */
    public HpackEncoder(int maxTableSize) {
        this(maxTableSize, true);
    }

    /**
     * Create an encoder with the given maximum dynamic table size.
     *
     * @param maxTableSize maximum dynamic table size in bytes
     * @param useHuffman whether to use Huffman encoding for strings
     */
    public HpackEncoder(int maxTableSize, boolean useHuffman) {
        this.dynamicTable = new DynamicTable(maxTableSize);
        this.useHuffman = useHuffman;
    }

    /**
     * Set the maximum dynamic table size.
     *
     * <p>This should be called when receiving a SETTINGS frame with
     * SETTINGS_HEADER_TABLE_SIZE. Per RFC 7541 Section 4.2, the encoder
     * MUST signal the change to the decoder at the start of the next header block
     * (only if the size actually changed).
     *
     * @param maxSize new maximum size in bytes
     */
    public void setMaxTableSize(int maxSize) {
        int currentMaxSize = dynamicTable.maxSize();
        // Only emit table size update if the size actually changed
        if (maxSize != currentMaxSize) {
            // Apply immediately to dynamic table (evicts entries if needed)
            dynamicTable.setMaxSize(maxSize);
            // Mark that we need to emit a table size update in the next header block
            pendingTableSizeUpdate = maxSize;
        }
    }

    /**
     * Emit any pending dynamic table size update.
     *
     * <p>Per RFC 7541 Section 4.2, when SETTINGS_HEADER_TABLE_SIZE is received,
     * the encoder MUST signal the change at the start of the next header block
     * by emitting a dynamic table size update instruction.
     *
     * <p>This method MUST be called once at the start of each header block
     * (before encoding any headers).
     *
     * @param out output stream to write the update to
     * @throws IOException if writing fails
     */
    public void beginHeaderBlock(OutputStream out) throws IOException {
        if (pendingTableSizeUpdate >= 0) {
            // Emit dynamic table size update (RFC 7541 Section 6.3)
            // Format: 001xxxxx (5-bit prefix for max size)
            encodeInteger(out, pendingTableSizeUpdate, 5, 0x20);
            pendingTableSizeUpdate = -1;
        }
    }

    /**
     * Encode a single header field.
     *
     * @param out output stream to write encoded bytes
     * @param name header name (lowercase)
     * @param value header value
     * @param sensitive whether this header contains sensitive data
     * @throws IOException if encoding fails
     */
    public void encodeHeader(OutputStream out, String name, String value, boolean sensitive)
            throws IOException {

        // Sensitive headers should never be indexed
        if (sensitive || NEVER_INDEX_HEADERS.contains(name)) {
            encodeLiteralNeverIndexed(out, name, value);
            return;
        }

        // Try to find full match in static table
        int staticIndex = StaticTable.findFullMatch(name, value);
        if (staticIndex > 0) {
            encodeIndexed(out, staticIndex);
            return;
        }

        // Try to find full match in dynamic table
        int dynamicIndex = dynamicTable.findFullMatch(name, value);
        if (dynamicIndex > 0) {
            encodeIndexed(out, dynamicIndex);
            return;
        }

        // Try to find name match for literal with indexing
        int nameIndex = StaticTable.findNameMatch(name);
        if (nameIndex < 0) {
            nameIndex = dynamicTable.findNameMatch(name);
        }

        // Encode as literal with indexing (adds to dynamic table)
        encodeLiteralWithIndexing(out, nameIndex, name, value);

        // Add to dynamic table
        dynamicTable.add(name, value);
    }

    /**
     * Encode a header using indexed representation.
     * Format: 1xxxxxxx (7-bit prefix)
     */
    private void encodeIndexed(OutputStream out, int index) throws IOException {
        encodeInteger(out, index, 7, 0x80);
    }

    /**
     * Encode a header as literal with indexing.
     * Format: 01xxxxxx (6-bit prefix for index)
     */
    private void encodeLiteralWithIndexing(
            OutputStream out,
            int nameIndex,
            String name,
            String value
    ) throws IOException {
        if (nameIndex > 0) {
            // Indexed name
            encodeInteger(out, nameIndex, 6, 0x40);
        } else {
            // New name
            out.write(0x40);
            encodeString(out, name);
        }
        encodeString(out, value);
    }

    /**
     * Encode a header as literal never indexed.
     * Format: 0001xxxx (4-bit prefix for index)
     */
    private void encodeLiteralNeverIndexed(OutputStream out, int nameIndex, String name, String value)
            throws IOException {
        if (nameIndex > 0) {
            encodeInteger(out, nameIndex, 4, 0x10);
        } else {
            out.write(0x10);
            encodeString(out, name);
        }
        encodeString(out, value);
    }

    private void encodeLiteralNeverIndexed(OutputStream out, String name, String value) throws IOException {
        int nameIndex = StaticTable.findNameMatch(name);
        if (nameIndex < 0) {
            nameIndex = dynamicTable.findNameMatch(name);
        }
        encodeLiteralNeverIndexed(out, Math.max(nameIndex, 0), name, value);
    }

    /**
     * Encode an integer with the given prefix size.
     * RFC 7541 Section 5.1
     */
    private void encodeInteger(OutputStream out, int value, int prefixBits, int prefix) throws IOException {
        int maxPrefix = (1 << prefixBits) - 1;

        if (value < maxPrefix) {
            out.write(prefix | value);
        } else {
            out.write(prefix | maxPrefix);
            value -= maxPrefix;
            while (value >= 128) {
                out.write((value & 0x7F) | 0x80);
                value >>= 7;
            }
            out.write(value);
        }
    }

    /**
     * Encode a string, using Huffman encoding if it saves space.
     * RFC 7541 Section 5.2
     */
    private void encodeString(OutputStream out, String str) throws IOException {
        // Convert to bytes once and reuse for both length calculation and encoding
        byte[] raw = str.getBytes(StandardCharsets.ISO_8859_1);

        if (useHuffman) {
            int huffmanLen = Huffman.encodedLength(raw);
            if (huffmanLen < raw.length) {
                // Use Huffman encoding
                byte[] huffman = Huffman.encode(raw);
                encodeInteger(out, huffman.length, 7, 0x80); // H=1
                out.write(huffman);
                return;
            }
        }

        // Use raw encoding
        encodeInteger(out, raw.length, 7, 0x00); // H=0
        out.write(raw);
    }
}
