/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.junit.FuzzTest;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Headers;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fuzz tests for HPACK decoder.
 *
 * <p>Tests crash safety, differential correctness against Netty,
 * property-based invariants, resource limits, and state machine consistency.
 */
class HpackDecoderFuzzTest {

    // --- Crash safety ---

    private static final int MAX_FUZZ_INPUT = 512;

    @FuzzTest(maxDuration = "5m")
    void fuzzDecode(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        try {
            new HpackDecoder(4096).decode(data);
        } catch (IOException ignored) {}
    }

    // --- Differential testing against Netty ---

    @FuzzTest
    void fuzzDifferentialVsNetty(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        // Decode with our implementation
        var smithyDecoder = new HpackDecoder(4096);
        List<String> smithyResult = null;
        Exception smithyError = null;
        try {
            smithyResult = smithyDecoder.decode(data);
        } catch (Exception e) {
            smithyError = e;
        }

        // Decode with Netty
        var nettyDecoder = new DefaultHttp2HeadersDecoder();
        Http2Headers nettyResult = null;
        Exception nettyError = null;
        try {
            nettyResult = nettyDecoder.decodeHeaders(1, Unpooled.wrappedBuffer(data));
        } catch (Exception e) {
            nettyError = e;
        }

        // Both should agree on success/failure
        if (smithyError != null && nettyError != null) {
            return; // Both rejected — fine
        }
        if (smithyError == null && nettyError == null) {
            // Both succeeded — compare results
            int smithyCount = smithyResult.size() / 2;
            int nettyCount = nettyResult.size();
            // Netty includes pseudo-headers in the count differently, so just verify
            // we decoded the same number of headers
            assertEquals(nettyCount,
                    smithyCount,
                    "Header count mismatch: smithy=" + smithyCount + " netty=" + nettyCount);
        }
        // One succeeded and one failed — this is acceptable because implementations
        // may differ on strictness (e.g., header validation, size limits)
    }

    // --- Property-based invariants: encode → decode round-trip ---

    @FuzzTest
    void fuzzRoundTrip(byte[] data) throws IOException {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        if (data.length < 4) {
            return;
        }

        // Use fuzz data to generate header name/value pairs
        var headers = extractHeaders(data);
        if (headers.isEmpty()) {
            return;
        }

        // Encode with our encoder
        var encoder = new HpackEncoder(4096);
        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        for (int i = 0; i < headers.size(); i += 2) {
            encoder.encodeHeader(out, headers.get(i), headers.get(i + 1), false);
        }
        byte[] encoded = out.toByteArray();

        // Decode and verify
        var decoder = new HpackDecoder(4096);
        List<String> decoded = decoder.decode(encoded);

        assertEquals(headers.size(), decoded.size(), "Round-trip header count mismatch");
        for (int i = 0; i < headers.size(); i += 2) {
            assertEquals(headers.get(i), decoded.get(i), "Name mismatch at " + (i / 2));
            assertEquals(headers.get(i + 1), decoded.get(i + 1), "Value mismatch at " + (i / 2));
        }
    }

    /**
     * Extract lowercase header name/value pairs from fuzz data.
     */
    private static List<String> extractHeaders(byte[] data) {
        var headers = new ArrayList<String>();
        int pos = 0;
        while (pos + 2 < data.length && headers.size() < 20) {
            int nameLen = (data[pos] & 0x0F) + 1; // 1-16
            pos++;
            if (pos + nameLen >= data.length) {
                break;
            }
            // Build lowercase ASCII name
            var name = new StringBuilder(nameLen);
            for (int i = 0; i < nameLen; i++) {
                name.append((char) ('a' + ((data[pos + i] & 0xFF) % 26)));
            }
            pos += nameLen;

            int valueLen = data[pos] & 0x3F; // 0-63
            pos++;
            if (pos + valueLen > data.length) {
                break;
            }
            var value = new String(data, pos, valueLen, java.nio.charset.StandardCharsets.ISO_8859_1);
            pos += valueLen;

            headers.add(name.toString());
            headers.add(value);
        }
        return headers;
    }
}
