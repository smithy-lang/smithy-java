/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.http.api.HeaderNames;

/**
 * HPACK decoder for HTTP/2 header decompression (RFC 7541).
 *
 * <p>This decoder decompresses HTTP headers from HPACK format back to name-value pairs.
 *
 * <p>Thread safety: This class is NOT thread-safe. Each HTTP/2 connection should have its own decoder instance.
 */
public final class HpackDecoder {

    private final DynamicTable dynamicTable;
    private final int maxHeaderListSize;

    /** Current position during decoding, reset at start of each decodeBlock call. */
    private int decodePos;

    /**
     * Create a decoder with the given maximum dynamic table size.
     *
     * @param maxTableSize maximum dynamic table size in bytes
     */
    public HpackDecoder(int maxTableSize) {
        this(maxTableSize, 8192);
    }

    /**
     * Create a decoder with the given limits.
     *
     * @param maxTableSize maximum dynamic table size in bytes
     * @param maxHeaderListSize maximum size of decoded header list
     */
    public HpackDecoder(int maxTableSize, int maxHeaderListSize) {
        this.dynamicTable = new DynamicTable(maxTableSize);
        this.maxHeaderListSize = maxHeaderListSize;
    }

    /**
     * Set the maximum dynamic table size.
     *
     * @param maxSize new maximum size in bytes
     */
    public void setMaxTableSize(int maxSize) {
        dynamicTable.setMaxSize(maxSize);
    }

    /**
     * Decode a header block.
     *
     * @param data the HPACK-encoded header block
     * @return list of decoded header fields
     * @throws IOException if decoding fails
     */
    public List<HeaderField> decode(byte[] data) throws IOException {
        return decode(data, 0, data.length);
    }

    /**
     * Decode a header block.
     *
     * @param data buffer containing HPACK-encoded header block
     * @param offset start offset in buffer
     * @param length number of bytes to decode
     * @return list of decoded header fields
     * @throws IOException if decoding fails
     */
    public List<HeaderField> decode(byte[] data, int offset, int length) throws IOException {
        return decodeBlock(data, offset, length);
    }

    /**
     * Get a header field from the indexed tables.
     */
    private HeaderField getIndexedField(int index) throws IOException {
        if (index <= 0) {
            throw new IOException("Invalid HPACK index: " + index);
        }

        if (index <= StaticTable.SIZE) {
            return StaticTable.get(index);
        } else {
            DynamicTable.HeaderField field = dynamicTable.get(index);
            return new HeaderField(field.name(), field.value());
        }
    }

    /**
     * Get a header name from the indexed tables.
     */
    private String getIndexedName(int index) throws IOException {
        if (index <= 0) {
            throw new IOException("Invalid HPACK name index: " + index);
        }

        if (index <= StaticTable.SIZE) {
            return StaticTable.get(index).name();
        } else {
            return dynamicTable.get(index).name();
        }
    }

    /**
     * Decode an integer with the given prefix size.
     * Updates decodePos and returns the decoded value.
     */
    private int decodeInteger(byte[] data, int prefixBits) throws IOException {
        if (decodePos >= data.length) {
            throw new IOException("Incomplete HPACK integer: no data at position " + decodePos);
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
            if (decodePos >= data.length) {
                throw new IOException("Incomplete HPACK integer");
            }
            b = data[decodePos] & 0xFF;
            decodePos++;
            value += (b & 0x7F) << shift;
            shift += 7;

            if (shift > 28) {
                throw new IOException("HPACK integer overflow");
            }
        } while ((b & 0x80) != 0);

        return value;
    }

    /**
     * Decode a string.
     * Updates decodePos and returns the decoded string.
     */
    private String decodeString(byte[] data) throws IOException {
        if (decodePos >= data.length) {
            throw new IOException("Incomplete HPACK string: no data at position " + decodePos);
        }

        boolean huffman = (data[decodePos] & 0x80) != 0;
        int length = decodeInteger(data, 7);
        if (decodePos + length > data.length) {
            throw new IOException("HPACK string length exceeds buffer");
        }

        String str;
        if (huffman) {
            str = Huffman.decode(data, decodePos, length);
        } else {
            str = new String(data, decodePos, length, StandardCharsets.ISO_8859_1);
        }
        decodePos += length;

        return str;
    }

    /**
     * Decode a header name string and intern strings. Updates decodePos and returns the interned name.
     *
     * <p>NOTE: This method does NOT validate for uppercase. The caller must validate literal names using
     * {@link #validateHeaderName(String)} on the raw decoded string BEFORE this method is called,
     * to comply with RFC 9113 Section 8.2.
     *
     * @param data HPACK data buffer
     * @param validate if true, validate for uppercase before interning
     * @return interned header name
     * @throws IOException if validation fails or decoding fails
     */
    private String decodeHeaderName(byte[] data, boolean validate) throws IOException {
        if (decodePos >= data.length) {
            throw new IOException("Incomplete HPACK string: no data at position " + decodePos);
        }

        boolean huffman = (data[decodePos] & 0x80) != 0;
        int length = decodeInteger(data, 7);

        if (decodePos + length > data.length) {
            throw new IOException("HPACK string length exceeds buffer");
        }

        String name;
        if (huffman) {
            // Huffman-encoded: decode then validate and normalize
            name = Huffman.decode(data, decodePos, length);
            if (validate) {
                validateHeaderName(name);
            }
            name = HeaderNames.canonicalize(name);
        } else {
            // Raw bytes: validate before interning if needed
            if (validate) {
                for (int i = 0; i < length; i++) {
                    byte b = data[decodePos + i];
                    if (b >= 'A' && b <= 'Z') {
                        throw new IOException("Header field name contains uppercase character");
                    }
                }
            }
            // Normalize directly (zero-copy for known headers)
            name = HeaderNames.canonicalize(data, decodePos, length);
        }
        decodePos += length;

        return name;
    }

    /**
     * Decode a literal header field. Updates decodePos and returns the decoded header field.
     *
     * <p>Validates literal header names per RFC 9113 Section 8.2. Indexed names are already validated
     * (static table is RFC-defined, dynamic table entries were validated when added).
     *
     * <p>Literal names are interned via {@link HeaderNames} for efficient pointer comparisons.
     */
    private HeaderField decodeLiteralField(byte[] data, int prefixBits) throws IOException {
        int nameIndex = decodeInteger(data, prefixBits);

        String name;
        if (nameIndex > 0) {
            // Indexed name, so already validated (static or dynamic table)
            name = getIndexedName(nameIndex);
        } else {
            // Literal name: decode with validation (must not contain uppercase per RFC 9113)
            name = decodeHeaderName(data, true);
        }

        return new HeaderField(name, decodeString(data));
    }

    /**
     * Decode a header block.
     *
     * @param data buffer containing HPACK-encoded header block
     * @param offset start offset in buffer
     * @param length number of bytes to decode
     * @return list of decoded header fields
     * @throws IOException if decoding fails
     */
    public List<HeaderField> decodeBlock(byte[] data, int offset, int length) throws IOException {
        // Initial capacity of 12 avoids resizing for typical HTTP responses (5-15 headers)
        List<HeaderField> headers = new ArrayList<>(12);
        decodePos = offset;
        int end = offset + length;
        int totalSize = 0;
        boolean headerFieldSeen = false;

        while (decodePos < end) {
            int b = data[decodePos] & 0xFF;

            HeaderField field;
            if ((b & 0x80) != 0) {
                // Indexed representation: 1xxxxxxx
                // No validation needed - static table is RFC-defined lowercase,
                // dynamic table entries were validated when added
                int index = decodeInteger(data, 7);
                field = getIndexedField(index);
                headerFieldSeen = true;
            } else if ((b & 0x40) != 0) {
                // Literal with indexing: 01xxxxxx
                // decodeLiteralField validates literal names
                field = decodeLiteralField(data, 6);
                dynamicTable.add(field.name(), field.value());
                headerFieldSeen = true;
            } else if ((b & 0x20) != 0) {
                // Dynamic table size update: 001xxxxx
                // RFC 7541 Section 4.2: MUST occur at the beginning of header block
                if (headerFieldSeen) {
                    throw new IOException("Dynamic table size update MUST occur at beginning of header block");
                }
                int newSize = decodeInteger(data, 5);
                dynamicTable.setMaxSize(newSize);
                continue;
            } else {
                // Literal never indexed (0001xxxx) or without indexing (0000xxxx)
                // decodeLiteralField validates literal names
                field = decodeLiteralField(data, 4);
                headerFieldSeen = true;
            }

            // Check header list size per RFC 7541 Section 4.1.
            // Since HPACK-decoded strings are ISO-8859-1, each char is one byte,
            // so we can use length() directly instead of allocating byte arrays.
            totalSize += field.name().length() + field.value().length() + 32;
            if (totalSize > maxHeaderListSize) {
                throw new IOException("Header list exceeds maximum size: " + totalSize + " > " + maxHeaderListSize);
            }

            headers.add(field);
        }

        return headers;
    }

    /**
     * Validate header field name per RFC 9113 Section 8.2.
     *
     * @param name header field name
     * @throws IOException if name contains invalid characters
     */
    private void validateHeaderName(String name) throws IOException {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            // RFC 9113 Section 8.2: Field names MUST NOT contain uppercase characters
            if (c >= 'A' && c <= 'Z') {
                throw new IOException("Header field name contains uppercase character: '" + name);
            }
        }
    }
}
