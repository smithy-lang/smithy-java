/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Minimal API surface tests for Huffman. Comprehensive coverage is in HpackTestSuiteTest.
 */
class HuffmanTest {

    @Test
    void roundTrip() throws IOException {
        String input = "hello";
        byte[] inputBytes = input.getBytes(StandardCharsets.ISO_8859_1);

        var out = new ByteArrayOutputStream();
        Huffman.encode(inputBytes, 0, inputBytes.length, out);
        byte[] encoded = out.toByteArray();

        String decoded = Huffman.decode(encoded, 0, encoded.length);
        assertEquals(input, decoded);
    }

    @Test
    void encodedLengthMatchesActual() throws IOException {
        byte[] input = "www.example.com".getBytes(StandardCharsets.ISO_8859_1);
        int predicted = Huffman.encodedLength(input, 0, input.length);

        var out = new ByteArrayOutputStream();
        Huffman.encode(input, 0, input.length, out);

        assertEquals(predicted, out.size());
    }

    @Test
    void emptyInput() throws IOException {
        byte[] empty = new byte[0];

        var out = new ByteArrayOutputStream();
        Huffman.encode(empty, 0, 0, out);
        assertEquals(0, out.size());

        assertEquals("", Huffman.decode(empty, 0, 0));
        assertEquals(0, Huffman.encodedLength(empty, 0, 0));
    }

    @Test
    void incompleteEncodingThrows() {
        // Single byte that starts a multi-byte sequence but doesn't complete it.
        // '0' is 5 bits, needs padding but 0x00 has wrong padding.
        byte[] incomplete = {(byte) 0x00};

        assertThrows(IOException.class, () -> Huffman.decode(incomplete, 0, incomplete.length));
    }
}
