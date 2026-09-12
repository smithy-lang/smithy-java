/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

/**
 * Sparrowhawk wire-format constants and varint arithmetic.
 *
 * <p>Sparrowhawk varints are unsigned, little-endian, and prefix-encoded: the number of trailing zero bits
 * in the first byte is the number of additional bytes to read. A value {@code v} needing {@code n} bytes is
 * encoded as {@code (2v+1) << (n-1)} written little-endian; values needing more than 56 bits are encoded as
 * a zero byte followed by the raw 8-byte little-endian value.
 */
final class Sparrowhawk {

    private Sparrowhawk() {}

    // Struct type-section codes (low two bits of a section header varint).
    static final int T_LIST = 0;
    static final int T_VARINT = 1;
    static final int T_FOUR = 2;
    static final int T_EIGHT = 3;

    // Third bit of a section header: a varint follows giving the 61-field group offset minus one.
    static final int CONTINUATION = 0b100;

    // Fields per presence bitset group: a section header varint carries the type (2 bits), the
    // continuation flag (1 bit), and a 61-bit presence bitset.
    static final int FIELDS_PER_GROUP = 61;

    // List element-type tags (low bits of a list length varint). Byte lists use the low bit 0 and encode
    // (byteCount << 1); all other lists encode (elementCount << 3) | tag.
    static final int LIST_BYTES = 0;
    static final int LIST_LEN_DELIMITED = 0b001;
    static final int LIST_VARINTS = 0b011;
    static final int LIST_FOUR = 0b101;
    static final int LIST_EIGHT = 0b111;

    // Canonical encoding of an empty struct/map/byte-list: a zero-length byte list.
    static final byte EMPTY_BYTE_LIST = 0x01;

    // Marker byte of a present sparse element: the wrapper struct's "list field 0" section header as the
    // reference implementation writes it. Note this is the reference's non-varint quirk (raw byte
    // zigzag(listField(1)) == 0x10, not the varint encoding 0x11); we match it for interoperability since
    // the reference reader compares this exact byte.
    static final byte SPARSE_PRESENT_MARKER = 0x10;

    // The one-byte varint of listField(0b11): the map struct's single list section with fields {0,1}
    // (parallel key and value lists).
    static final long MAP_FIELDSET = 0b11L << 3;

    static long byteListHeader(long byteCount) {
        return byteCount << 1;
    }

    static long typedListHeader(long elementCount, int tag) {
        return (elementCount << 3) | tag;
    }

    static long zigzag(long v) {
        return (v << 1) ^ (v >> 63);
    }

    static int zigzag(int v) {
        return (v << 1) ^ (v >> 31);
    }

    static long unzigzag(long v) {
        return (v >>> 1) ^ -(v & 1);
    }

    static int unzigzag(int v) {
        return (v >>> 1) ^ -(v & 1);
    }

    /** Number of bytes the unsigned varint encoding of {@code v} occupies (1-9). */
    static int uvarintSize(long v) {
        int bits = 64 - Long.numberOfLeadingZeros(v | 1);
        // 1 + (bits-1)/7 for bits <= 56, else 9.
        return bits <= 56 ? 1 + (bits - 1) / 7 : 9;
    }

    /** Number of additional bytes to read after the first byte of a varint. */
    static int uvarintExtraBytes(int firstByte) {
        return Integer.numberOfTrailingZeros(firstByte | 0x100);
    }
}
