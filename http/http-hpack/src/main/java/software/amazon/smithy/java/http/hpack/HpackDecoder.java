/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.http.api.HeaderNames;

/**
 * HPACK decoder for HTTP/2 header decompression (RFC 7541).
 *
 * <p>Thread safety: This class is not thread-safe. Each HTTP/2 connection should have
 * its own decoder instance to maintain dynamic table state.
 */
public final class HpackDecoder {

    private static final int DEFAULT_MAX_TABLE_SIZE = 4096;
    private static final int DEFAULT_MAX_HEADER_LIST_SIZE = 8192;

    private final DynamicTable dynamicTable;
    private final int maxHeaderListSize;
    private int maxTableSize;

    /** Current position during decoding, reset at start of each decode call. */
    private int decodePos;
    /** End of current decode region. */
    private int limit;

    /**
     * Create a decoder with default limits (4096 byte table, 8192 byte header list).
     */
    public HpackDecoder() {
        this(DEFAULT_MAX_TABLE_SIZE, DEFAULT_MAX_HEADER_LIST_SIZE);
    }

    /**
     * Create a decoder with the given maximum dynamic table size.
     *
     * @param maxTableSize maximum dynamic table size in bytes
     */
    public HpackDecoder(int maxTableSize) {
        this(maxTableSize, DEFAULT_MAX_HEADER_LIST_SIZE);
    }

    /**
     * Create a decoder with the given limits.
     *
     * @param maxTableSize maximum dynamic table size in bytes
     * @param maxHeaderListSize maximum size of decoded header list
     */
    public HpackDecoder(int maxTableSize, int maxHeaderListSize) {
        this.dynamicTable = new DynamicTable(maxTableSize);
        this.maxTableSize = maxTableSize;
        this.maxHeaderListSize = maxHeaderListSize;
    }

    /**
     * Set the maximum dynamic table size.
     *
     * @param maxSize new maximum size in bytes
     */
    public void setMaxTableSize(int maxSize) {
        this.maxTableSize = maxSize;
        dynamicTable.setMaxSize(maxSize);
    }

    /**
     * Decode a header block.
     *
     * @param data the HPACK-encoded header block
     * @return flat list of headers: [name0, value0, name1, value1, ...]
     * @throws IOException if decoding fails
     */
    public List<String> decode(byte[] data) throws IOException {
        return decode(data, 0, data.length);
    }

    /**
     * Decode a header block.
     *
     * @param data buffer containing HPACK-encoded header block
     * @param offset start offset in buffer
     * @param length number of bytes to decode
     * @return flat list of headers: [name0, value0, name1, value1, ...]
     * @throws IOException if decoding fails
     */
    public List<String> decode(byte[] data, int offset, int length) throws IOException {
        if (length == 0) {
            return List.of();
        }

        // ~12 headers * 2 = 24
        List<String> headers = new ArrayList<>(24);
        decodePos = offset;
        limit = offset + length;
        int totalSize = 0;
        boolean headerFieldSeen = false;

        while (decodePos < limit) {
            int b = data[decodePos] & 0xFF;

            String name, value;
            if ((b & 0x80) != 0) {
                // Indexed representation: 1xxxxxxx
                int index = decodeInteger(data, 7);
                if (index <= 0) {
                    throw new IOException("Invalid HPACK index: " + index);
                } else if (index <= StaticTable.SIZE) {
                    name = StaticTable.getName(index);
                    value = StaticTable.getValue(index);
                } else {
                    try {
                        name = dynamicTable.getName(index);
                        value = dynamicTable.getValue(index);
                    } catch (IndexOutOfBoundsException e) {
                        throw new IOException(e.getMessage(), e);
                    }
                }
                headerFieldSeen = true;
            } else if ((b & 0x40) != 0) {
                // Literal with indexing: 01xxxxxx
                int nameIndex = decodeInteger(data, 6);
                name = nameIndex > 0 ? getIndexedName(nameIndex) : decodeHeaderName(data);
                value = decodeString(data);
                dynamicTable.add(name, value);
                headerFieldSeen = true;
            } else if ((b & 0x20) != 0) {
                // Dynamic table size update: 001xxxxx
                // RFC 7541 Section 4.2: "This dynamic table size update MUST occur at the beginning of the first
                // header block following the change to the dynamic table size"
                if (headerFieldSeen) {
                    throw new IOException("Dynamic table size update MUST occur at beginning of header block");
                }
                int newSize = decodeInteger(data, 5);
                if (newSize > maxTableSize) {
                    throw new IOException(
                            "Dynamic table size update " + newSize + " exceeds configured maximum " + maxTableSize);
                }
                dynamicTable.setMaxSize(newSize);
                continue;
            } else {
                // Literal never indexed (0001xxxx) or without indexing (0000xxxx)
                int nameIndex = decodeInteger(data, 4);
                name = nameIndex > 0 ? getIndexedName(nameIndex) : decodeHeaderName(data);
                value = decodeString(data);
                headerFieldSeen = true;
            }

            // Check header list size
            totalSize += name.length() + value.length() + 32;
            if (totalSize > maxHeaderListSize) {
                throw new IOException("Header list exceeds maximum size: " + totalSize + " > " + maxHeaderListSize);
            }

            headers.add(name);
            headers.add(value);
        }

        return headers;
    }

    /**
     * Get a header name from the indexed tables.
     *
     * @param index table index (1-61 for static, 62+ for dynamic)
     * @return header name
     * @throws IOException if index is invalid
     */
    private String getIndexedName(int index) throws IOException {
        if (index <= 0) {
            throw new IOException("Invalid HPACK name index: " + index);
        }
        try {
            return index <= StaticTable.SIZE ? StaticTable.getName(index) : dynamicTable.getName(index);
        } catch (IndexOutOfBoundsException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Decode an integer with the given prefix size. Updates decodePos and returns the decoded value.
     *
     * @param data buffer containing encoded integer
     * @param prefixBits number of prefix bits (1-8)
     * @return decoded integer value
     * @throws IOException if integer is incomplete or overflows
     */
    private int decodeInteger(byte[] data, int prefixBits) throws IOException {
        if (decodePos >= limit) {
            throw new IOException("Incomplete HPACK integer");
        }

        int maxPrefix = (1 << prefixBits) - 1;
        int value = data[decodePos] & maxPrefix;
        decodePos++;

        if (value < maxPrefix) {
            return value;
        }

        int shift = 0;
        int b;
        do {
            if (decodePos >= limit) {
                throw new IOException("Incomplete HPACK integer");
            }
            b = data[decodePos++] & 0xFF;
            if (shift >= 28) {
                throw new IOException("HPACK integer overflow");
            }
            value += (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        return value;
    }

    /**
     * Decode a string literal.
     * Updates decodePos and returns the decoded string.
     *
     * @param data buffer containing encoded string
     * @return decoded string
     * @throws IOException if string is incomplete or invalid
     */
    private String decodeString(byte[] data) throws IOException {
        if (decodePos >= limit) {
            throw new IOException("Incomplete HPACK string");
        }

        boolean isHuffmanEncoded = (data[decodePos] & 0x80) != 0;
        int length = decodeStringLength(data);
        int start = decodePos;
        decodePos += length;
        return isHuffmanEncoded
                ? Huffman.decode(data, start, length)
                : new String(data, start, length, StandardCharsets.ISO_8859_1);
    }

    private int decodeStringLength(byte[] data) throws IOException {
        int length = decodeInteger(data, 7);
        if (decodePos + length > limit) {
            throw new IOException("HPACK string length exceeds buffer");
        }
        return length;
    }

    /**
     * Decode a header name string with validation and interning.
     *
     * <p>Validates that literal names do not contain uppercase characters, then interns via {@link HeaderNames}.
     *
     * @param data buffer containing encoded name
     * @return interned header name
     * @throws IOException if validation fails or decoding fails
     */
    private String decodeHeaderName(byte[] data) throws IOException {
        if (decodePos >= limit) {
            throw new IOException("Incomplete HPACK string");
        }

        boolean isHuffmanEncoded = (data[decodePos] & 0x80) != 0;
        int length = decodeStringLength(data);
        int start = decodePos;
        decodePos += length;

        if (isHuffmanEncoded) {
            return Huffman.decodeHeaderName(data, start, length);
        }

        for (int i = 0; i < length; i++) {
            byte b = data[start + i];
            if (b >= 'A' && b <= 'Z') {
                throw new IOException("Header name contains uppercase");
            }
        }

        return HeaderNames.canonicalize(data, start, length);
    }
}
