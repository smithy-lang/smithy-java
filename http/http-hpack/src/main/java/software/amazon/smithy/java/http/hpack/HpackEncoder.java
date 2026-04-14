/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import software.amazon.smithy.java.io.ByteBufferOutputStream;

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

    // HPACK representation type prefixes (RFC 7541 Section 6)
    private static final int PREFIX_INDEXED = 0x80; // 1xxxxxxx
    private static final int PREFIX_LITERAL_INDEXED = 0x40; // 01xxxxxx
    private static final int PREFIX_SIZE_UPDATE = 0x20; // 001xxxxx
    private static final int PREFIX_LITERAL_NEVER = 0x10; // 0001xxxx

    private static final int DEFAULT_MAX_TABLE_SIZE = 4096;

    private final DynamicTable dynamicTable;
    private final boolean useHuffman;

    // Track pending table size updates to emit at start of next header block (RFC 7541 Section 4.2).
    // If multiple size changes occur before a header block, we must emit the minimum reached
    // and then the final size, to ensure the decoder evicts the same entries we did.
    private int pendingTableSizeUpdate = -1;
    private int minPendingTableSize = -1;

    // Reusable scratch buffer for string encoding to avoid per-string allocation.
    // Typical header values are < 256 bytes; buffer grows if needed.
    private byte[] stringBuf = new byte[256];

    /**
     * Create an encoder with default limits (4096 byte table) and Huffman encoding enabled.
     */
    public HpackEncoder() {
        this(DEFAULT_MAX_TABLE_SIZE, true);
    }

    /**
     * Create an encoder with the given maximum dynamic table size and Huffman encoding enabled.
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
     * <p>This should be called when receiving a SETTINGS frame with SETTINGS_HEADER_TABLE_SIZE.
     * Per RFC 7541 Section 4.2, the encoder MUST signal the change to the decoder at the start of the next
     * header block (only if the size actually changed).
     *
     * @param maxSize new maximum size in bytes
     */
    public void setMaxTableSize(int maxSize) {
        int currentMaxSize = dynamicTable.maxSize();

        if (maxSize != currentMaxSize) {
            dynamicTable.setMaxSize(maxSize);
            // Track the minimum size reached since last header block
            if (pendingTableSizeUpdate != -1) {
                minPendingTableSize = Math.min(minPendingTableSize, maxSize);
            } else {
                minPendingTableSize = maxSize;
            }
            pendingTableSizeUpdate = maxSize;
        }
    }

    /**
     * Emit any pending dynamic table size update.
     *
     * <p>Per RFC 7541 Section 4.2, when SETTINGS_HEADER_TABLE_SIZE is received, the encoder MUST signal the change
     * at the start of the next header block by emitting a dynamic table size update instruction.
     *
     * <p>This method MUST be called once at the start of each header block (before encoding any headers).
     *
     * @param out output stream to write the update to
     * @throws IOException if writing fails
     */
    public void beginHeaderBlock(OutputStream out) throws IOException {
        if (pendingTableSizeUpdate >= 0) {
            // RFC 7541 Section 4.2: If size was reduced then raised, emit the minimum first
            // to ensure the decoder evicts the same entries we did.
            if (minPendingTableSize < pendingTableSizeUpdate) {
                encodeInteger(out, minPendingTableSize, 5, PREFIX_SIZE_UPDATE);
            }
            encodeInteger(out, pendingTableSizeUpdate, 5, PREFIX_SIZE_UPDATE);
            pendingTableSizeUpdate = -1;
            minPendingTableSize = -1;
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
    public void encodeHeader(OutputStream out, String name, String value, boolean sensitive) throws IOException {
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
        encodeInteger(out, index, 7, PREFIX_INDEXED);
    }

    /**
     * Encode a header as literal with indexing. Format: 01xxxxxx (6-bit prefix for index)
     */
    private void encodeLiteralWithIndexing(OutputStream out, int nameIndex, String name, String value)
            throws IOException {
        if (nameIndex > 0) {
            // Indexed name
            encodeInteger(out, nameIndex, 6, PREFIX_LITERAL_INDEXED);
        } else {
            // New name
            out.write(PREFIX_LITERAL_INDEXED);
            encodeString(out, name);
        }
        encodeString(out, value);
    }

    /**
     * Encode a header as literal never indexed. Format: 0001xxxx (4-bit prefix for index)
     */
    private void encodeLiteralNeverIndexed(OutputStream out, int nameIndex, String name, String value)
            throws IOException {
        if (nameIndex > 0) {
            encodeInteger(out, nameIndex, 4, PREFIX_LITERAL_NEVER);
        } else {
            out.write(PREFIX_LITERAL_NEVER);
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
     * Encode an integer with the given prefix size. RFC 7541 Section 5.1
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
     */
    @SuppressWarnings("deprecation")
    private void encodeString(OutputStream out, String str) throws IOException {
        int len = str.length();

        if (useHuffman) {
            // Need bytes in scratch buffer to calculate Huffman length
            if (len > stringBuf.length) {
                stringBuf = new byte[len];
            }
            str.getBytes(0, len, stringBuf, 0);

            // Only use Huffman if it saves space.
            int huffmanLen = Huffman.encodedLength(stringBuf, 0, len);
            if (huffmanLen < len) {
                encodeInteger(out, huffmanLen, 7, 0x80); // H=1
                Huffman.encode(stringBuf, 0, len, out);
            } else {
                encodeInteger(out, len, 7, 0x00); // H=0
                out.write(stringBuf, 0, len);
            }
        } else {
            // Raw encoding, use optimized path for ByteBufferOutputStream if available.
            encodeInteger(out, len, 7, 0x00); // H=0
            if (out instanceof ByteBufferOutputStream bbos) {
                bbos.writeAscii(str);
            } else {
                if (len > stringBuf.length) {
                    stringBuf = new byte[len];
                }
                str.getBytes(0, len, stringBuf, 0);
                out.write(stringBuf, 0, len);
            }
        }
    }
}
