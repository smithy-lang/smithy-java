/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;

/**
 * Fuzz tests for HTTP/1.1 chunked transfer encoding.
 */
class ChunkedEncodingFuzzTest {

    private static final int MAX_FUZZ_INPUT = 512;

    // --- Crash safety: random bytes as chunked input ---

    @FuzzTest
    void fuzzChunkedInputDecode(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        var bis = new UnsyncBufferedInputStream(new ByteArrayInputStream(data), 1024);
        var chunked = new ChunkedInputStream(bis);
        try {
            chunked.transferTo(OutputStream.nullOutputStream());
        } catch (IOException ignored) {}
    }

    // --- Round-trip: write chunked → read chunked → verify identity ---

    @FuzzTest
    void fuzzChunkedRoundTrip(byte[] data) throws IOException {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }

        // Write through ChunkedOutputStream
        var wireBuffer = new ByteArrayOutputStream();
        var bos = new UnsyncBufferedOutputStream(wireBuffer, 1024);
        var chunkedOut = new ChunkedOutputStream(bos, 64);
        chunkedOut.write(data);
        chunkedOut.close();

        // Read back through ChunkedInputStream
        byte[] wire = wireBuffer.toByteArray();
        var bis = new UnsyncBufferedInputStream(new ByteArrayInputStream(wire), 1024);
        var chunkedIn = new ChunkedInputStream(bis);
        var result = new ByteArrayOutputStream();
        chunkedIn.transferTo(result);
        byte[] decoded = result.toByteArray();

        assertArrayEquals(data, decoded, "Chunked round-trip mismatch");
    }

}
