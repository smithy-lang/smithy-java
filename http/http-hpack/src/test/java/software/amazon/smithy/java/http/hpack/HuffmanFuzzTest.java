/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Fuzz tests for Huffman codec.
 */
class HuffmanFuzzTest {

    private static final int MAX_FUZZ_INPUT = 1024;

    @FuzzTest
    void fuzzDecode(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        try {
            Huffman.decode(data, 0, data.length);
        } catch (IOException ignored) {}
    }

    @FuzzTest
    void fuzzDecodeHeaderName(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        try {
            Huffman.decodeHeaderName(data, 0, data.length);
        } catch (IOException ignored) {}
    }

    @FuzzTest
    void fuzzEncode(byte[] data) throws IOException {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }

        // Verify encodedLength prediction matches actual encode
        var out = new ByteArrayOutputStream();
        Huffman.encode(data, 0, data.length, out);
        byte[] encoded = out.toByteArray();

        int predictedLen = Huffman.encodedLength(data, 0, data.length);
        assertEquals(encoded.length,
                predictedLen,
                "encodedLength prediction mismatch");
    }

}
