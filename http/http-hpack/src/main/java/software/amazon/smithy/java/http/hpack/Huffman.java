/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import software.amazon.smithy.java.http.api.HeaderNames;

/**
 * HPACK Huffman encoding/decoding from RFC 7541 Appendix B.
 *
 * <p>This implementation uses table-driven encoding and a finite state machine
 * for decoding, optimized for the HTTP/2 header compression use case.
 */
final class Huffman {

    private Huffman() {}

    /**
     * Huffman codes for each byte value (0-255), from RFC 7541 Appendix B.
     */
    private static final int[] CODES = {
            0x1ff8,
            0x7fffd8,
            0xfffffe2,
            0xfffffe3,
            0xfffffe4,
            0xfffffe5,
            0xfffffe6,
            0xfffffe7,
            0xfffffe8,
            0xffffea,
            0x3ffffffc,
            0xfffffe9,
            0xfffffea,
            0x3ffffffd,
            0xfffffeb,
            0xfffffec,
            0xfffffed,
            0xfffffee,
            0xfffffef,
            0xffffff0,
            0xffffff1,
            0xffffff2,
            0x3ffffffe,
            0xffffff3,
            0xffffff4,
            0xffffff5,
            0xffffff6,
            0xffffff7,
            0xffffff8,
            0xffffff9,
            0xffffffa,
            0xffffffb,
            0x14,
            0x3f8,
            0x3f9,
            0xffa,
            0x1ff9,
            0x15,
            0xf8,
            0x7fa,
            0x3fa,
            0x3fb,
            0xf9,
            0x7fb,
            0xfa,
            0x16,
            0x17,
            0x18,
            0x0,
            0x1,
            0x2,
            0x19,
            0x1a,
            0x1b,
            0x1c,
            0x1d,
            0x1e,
            0x1f,
            0x5c,
            0xfb,
            0x7ffc,
            0x20,
            0xffb,
            0x3fc,
            0x1ffa,
            0x21,
            0x5d,
            0x5e,
            0x5f,
            0x60,
            0x61,
            0x62,
            0x63,
            0x64,
            0x65,
            0x66,
            0x67,
            0x68,
            0x69,
            0x6a,
            0x6b,
            0x6c,
            0x6d,
            0x6e,
            0x6f,
            0x70,
            0x71,
            0x72,
            0xfc,
            0x73,
            0xfd,
            0x1ffb,
            0x7fff0,
            0x1ffc,
            0x3ffc,
            0x22,
            0x7ffd,
            0x3,
            0x23,
            0x4,
            0x24,
            0x5,
            0x25,
            0x26,
            0x27,
            0x6,
            0x74,
            0x75,
            0x28,
            0x29,
            0x2a,
            0x7,
            0x2b,
            0x76,
            0x2c,
            0x8,
            0x9,
            0x2d,
            0x77,
            0x78,
            0x79,
            0x7a,
            0x7b,
            0x7ffe,
            0x7fc,
            0x3ffd,
            0x1ffd,
            0xffffffc,
            0xfffe6,
            0x3fffd2,
            0xfffe7,
            0xfffe8,
            0x3fffd3,
            0x3fffd4,
            0x3fffd5,
            0x3fffd6,
            0x3fffd7,
            0x3fffd8,
            0x3fffd9,
            0x3fffda,
            0x3fffdb,
            0x3fffdc,
            0x3fffdd,
            0x3fffde,
            0x3fffdf,
            0x3fffe0,
            0x3fffe1,
            0x3fffe2,
            0x3fffe3,
            0x3fffe4,
            0x3fffe5,
            0x3fffe6,
            0x3fffe7,
            0x3fffe8,
            0x3fffe9,
            0x3fffea,
            0x3fffeb,
            0xffffec,
            0x3fffec,
            0x3fffed,
            0x3fffee,
            0x3fffef,
            0x3ffff0,
            0x3ffff1,
            0x3ffff2,
            0x3ffff3,
            0x3ffff4,
            0x3ffff5,
            0x3ffff6,
            0x3ffff7,
            0x3ffff8,
            0x3ffff9,
            0x3ffffa,
            0x3ffffb,
            0xfffffb,
            0xfffffc,
            0xfffffd,
            0xfffffe,
            0xffffff,
            0x1ffffec,
            0x1ffffed,
            0x1ffffee,
            0x1ffffef,
            0x1fffff0,
            0x1fffff1,
            0x1fffff2,
            0x1fffff3,
            0x1fffff4,
            0x1fffff5,
            0x1fffff6,
            0x1fffff7,
            0x1fffff8,
            0x1fffff9,
            0x1fffffa,
            0x1fffffb,
            0x1fffffc,
            0x1fffffd,
            0x1fffffe,
            0x1ffffff,
            0x3fffffc,
            0x3fffffd,
            0x3fffffe,
            0x3ffffff,
            0x7fffffc,
            0x7fffffd,
            0x7fffffe,
            0x7ffffff,
            0xffffffc,
            0xffffffd,
            0xffffffe,
            0xfffffff,
            0x10000000,
            0x10000001,
            0x10000002,
            0x10000003,
            0x10000004,
            0x10000005,
            0x10000006,
            0x10000007,
            0x10000008,
            0x10000009,
            0x1000000a,
            0x1000000b,
            0x1000000c,
            0x1000000d,
            0x1000000e,
            0x1000000f,
            0x10000010,
            0x10000011,
            0x10000012,
            0x10000013,
            0x10000014,
            0x10000015,
            0x10000016,
            0x10000017,
            0x10000018,
            0x10000019,
            0x1000001a,
            0x1000001b,
            0x1000001c,
            0x1000001d,
            0x1000001e,
            0x1000001f,
            0x10000020,
            0x10000021,
            0x10000022,
            0x10000023,
            0x10000024,
            0x10000025,
            0x10000026,
            0x10000027,
            0x10000028,
            0x10000029,
            0x1000002a,
            0x1000002b,
            0x1000002c
    };

    /**
     * Huffman code lengths (in bits) for each byte value (0-255), from RFC 7541 Appendix B.
     */
    private static final byte[] LENGTHS = {
            13,
            23,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            24,
            30,
            28,
            28,
            30,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            30,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            6,
            10,
            10,
            12,
            13,
            6,
            8,
            11,
            10,
            10,
            8,
            11,
            8,
            6,
            6,
            6,
            5,
            5,
            5,
            6,
            6,
            6,
            6,
            6,
            6,
            6,
            7,
            8,
            15,
            6,
            12,
            10,
            13,
            6,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            7,
            8,
            7,
            8,
            13,
            19,
            13,
            14,
            6,
            15,
            5,
            6,
            5,
            6,
            5,
            6,
            6,
            6,
            5,
            7,
            7,
            6,
            6,
            6,
            5,
            6,
            7,
            6,
            5,
            5,
            6,
            7,
            7,
            7,
            7,
            7,
            15,
            11,
            14,
            13,
            28,
            20,
            22,
            20,
            20,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            24,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            22,
            24,
            24,
            24,
            24,
            24,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            25,
            26,
            26,
            26,
            26,
            27,
            27,
            27,
            27,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28,
            28
    };

    // Decode flags
    private static final int FLAG_EMIT = 0x01; // Emit a decoded byte
    private static final int FLAG_ACCEPTED = 0x02; // Valid end state
    private static final int FLAG_FAIL = 0x04; // Invalid sequence

    /**
     * Huffman decoding table using finite state machine.
     * Each entry is {next_state, flags, emitted_byte}.
     * The table is indexed by (state << 4) | nibble.
     *
     * <p>This table was generated from the Huffman tree in RFC 7541.
     * States 0-511 represent positions in the decoding tree (511 nodes max).
     */
    private static final int[][] DECODE_TABLE = buildDecodeTable();

    /**
     * Encode bytes using Huffman coding directly to an output stream.
     *
     * <p>This avoids allocating an intermediate byte[] buffer.
     *
     * @param data the bytes to encode
     * @param offset start offset in data
     * @param length number of bytes to encode
     * @param out the output stream to write to
     * @throws IOException if writing fails
     */
    static void encode(byte[] data, int offset, int length, OutputStream out) throws IOException {
        long current = 0;
        int bits = 0;

        int end = offset + length;
        for (int i = offset; i < end; i++) {
            int index = data[i] & 0xFF;
            int code = CODES[index];
            int codeLength = LENGTHS[index];

            current <<= codeLength;
            current |= code;
            bits += codeLength;

            while (bits >= 8) {
                bits -= 8;
                out.write((int) (current >> bits));
            }
        }

        // Pad with EOS (all 1s) to byte boundary
        if (bits > 0) {
            out.write((int) ((current << (8 - bits)) | (0xFF >> bits)));
        }
    }

    /**
     * Calculate the encoded length of a byte array region without actually encoding it.
     *
     * @param data the bytes to measure
     * @param offset start offset in data
     * @param length number of bytes to measure
     * @return length in bytes when Huffman-encoded
     */
    static int encodedLength(byte[] data, int offset, int length) {
        int bits = 0;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            bits += LENGTHS[data[i] & 0xFF];
        }
        return (bits + 7) / 8;
    }

    /**
     * Decode Huffman-encoded bytes to a string.
     *
     * @param data the buffer containing Huffman-encoded bytes
     * @param offset start offset in buffer
     * @param length number of bytes to decode
     * @return decoded string
     * @throws IOException if decoding fails (invalid Huffman sequence)
     */
    static String decode(byte[] data, int offset, int length) throws IOException {
        // Safe: shortest HPACK Huffman code is 5 bits, so max expansion is 8/5 = 1.6x < 2x
        byte[] buf = new byte[length * 2];
        int pos = decodeBytes(data, offset, length, false, buf);
        return new String(buf, 0, pos, StandardCharsets.ISO_8859_1);
    }

    /**
     * Decode a Huffman-encoded header name, validating no uppercase and canonicalizing.
     */
    static String decodeHeaderName(byte[] data, int offset, int length) throws IOException {
        // Safe: shortest HPACK Huffman code is 5 bits, so max expansion is 8/5 = 1.6x < 2x
        byte[] buf = new byte[length * 2];
        int pos = decodeBytes(data, offset, length, true, buf);
        return HeaderNames.canonicalize(buf, 0, pos);
    }

    /**
     * Decode Huffman-encoded bytes into the provided buffer.
     *
     * @param buf output buffer, must be at least {@code length * 2} bytes
     * @return number of decoded bytes written to buf
     */
    private static int decodeBytes(byte[] data, int offset, int length, boolean validateName, byte[] buf)
            throws IOException {
        assert buf.length >= length * 2 : "buffer too small for Huffman decode";
        int pos = 0;
        int state = 0;
        boolean accepted = true;

        for (int i = offset; i < offset + length; i++) {
            int b = data[i] & 0xFF;

            // Process high nibble
            int index = (state << 4) | (b >> 4);
            state = DECODE_TABLE[index][0];
            int flags = DECODE_TABLE[index][1];

            pos = processNibble(validateName, buf, pos, index, flags);

            // Process low nibble
            index = (state << 4) | (b & 0x0F);
            state = DECODE_TABLE[index][0];
            flags = DECODE_TABLE[index][1];

            pos = processNibble(validateName, buf, pos, index, flags);
            accepted = (flags & FLAG_ACCEPTED) != 0;
        }

        if (!accepted) {
            throw new IOException("Invalid Huffman encoding: incomplete sequence");
        }

        return pos;
    }

    private static int processNibble(boolean validateName, byte[] buf, int pos, int index, int flags)
            throws IOException {
        if ((flags & FLAG_FAIL) != 0) {
            throw new IOException("Invalid Huffman encoding");
        }

        if ((flags & FLAG_EMIT) != 0) {
            byte emitted = (byte) DECODE_TABLE[index][2];
            if (validateName && emitted >= 'A' && emitted <= 'Z') {
                throw new IOException("Header name contains uppercase");
            }
            buf[pos++] = emitted;
        }

        return pos;
    }

    private static int[][] buildDecodeTable() {
        // State machine with up to 512 states (Huffman tree has 511 nodes: 256 leaves + 255 internal)
        int[][] table = new int[512 * 16][3];

        // Initialize all entries to fail state
        for (int[] row : table) {
            row[1] = FLAG_FAIL;
        }

        // Tree as parallel arrays: left[i] and right[i] are children of state i (-1 = none)
        // symbol[i] is decoded symbol at state i (-1 = non-terminal)
        int[] left = new int[512];
        int[] right = new int[512];
        int[] symbol = new int[512];
        Arrays.fill(left, -1);
        Arrays.fill(right, -1);
        Arrays.fill(symbol, -1);
        int numStates = 1; // state 0 is root

        // Build tree
        for (int sym = 0; sym < 256; sym++) {
            int code = CODES[sym];
            int len = LENGTHS[sym];
            int state = 0;
            for (int i = len - 1; i >= 0; i--) {
                int bit = (code >> i) & 1;
                int[] children = (bit == 0) ? left : right;
                if (children[state] == -1) {
                    children[state] = numStates++;
                }
                state = children[state];
            }
            symbol[state] = sym;
        }

        // Build state transition table for each nibble (4 bits at a time)
        for (int startState = 0; startState < numStates; startState++) {
            for (int nibble = 0; nibble < 16; nibble++) {
                int cur = startState;
                int emitted = -1;
                boolean failed = false;

                // Process 4 bits
                for (int i = 3; i >= 0; i--) {
                    int bit = (nibble >> i) & 1;
                    cur = (bit == 0) ? left[cur] : right[cur];
                    if (cur == -1) {
                        failed = true;
                        break;
                    }
                    if (symbol[cur] != -1) {
                        emitted = symbol[cur];
                        cur = 0; // reset to root
                    }
                }

                if (failed) {
                    continue; // already initialized to FAIL
                }

                int idx = (startState << 4) | nibble;
                table[idx][0] = cur;
                table[idx][1] = (emitted >= 0 ? FLAG_EMIT : 0)
                        | (canBeEosPadded(cur, symbol, right) ? FLAG_ACCEPTED : 0);
                table[idx][2] = Math.max(emitted, 0);
            }
        }

        return table;
    }

    /**
     * Check if a state can be valid EOS padding per RFC 7541.
     * A state is accepted if following all 1-bits (right children) for up to 7 bits
     * never reaches a terminal symbol.
     */
    private static boolean canBeEosPadded(int state, int[] symbol, int[] right) {
        // root is always accepted
        if (state == 0) {
            return true;
        }

        for (int i = 0; i < 7; i++) {
            if (state == -1) {
                return false;
            } else if (symbol[state] != -1) {
                return false; // would decode a symbol - invalid padding
            }
            state = right[state]; // follow 1-bit (EOS is all 1s)
        }

        return true;
    }
}
