/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.hpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HpackEncoderTest {

    @Test
    void encodesStaticIndexedHeader() throws IOException {
        var encoder = new HpackEncoder(4096);
        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        encoder.encodeHeader(out, ":method", "GET", false);

        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(out.toByteArray());

        assertEquals(2, headers.size());
        assertEquals(":method", headers.get(0));
        assertEquals("GET", headers.get(1));
    }

    @Test
    void encodesLiteralWithIndexing() throws IOException {
        var encoder = new HpackEncoder(4096);
        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        encoder.encodeHeader(out, "x-custom", "value", false);

        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(out.toByteArray());

        assertEquals(2, headers.size());
        assertEquals("x-custom", headers.get(0));
        assertEquals("value", headers.get(1));
    }

    @Test
    void encodesMultipleHeaders() throws IOException {
        var encoder = new HpackEncoder(4096);
        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        encoder.encodeHeader(out, ":method", "GET", false);
        encoder.encodeHeader(out, ":path", "/", false);
        encoder.encodeHeader(out, ":scheme", "https", false);

        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(out.toByteArray());

        assertEquals(6, headers.size()); // 3 headers * 2
        assertEquals(":method", headers.get(0));
        assertEquals("GET", headers.get(1));
        assertEquals(":path", headers.get(2));
        assertEquals("/", headers.get(3));
        assertEquals(":scheme", headers.get(4));
        assertEquals("https", headers.get(5));
    }

    @Test
    void reusesDynamicTableEntry() throws IOException {
        var encoder = new HpackEncoder(4096);
        var decoder = new HpackDecoder(4096);

        // First block - adds to dynamic table
        var out1 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out1);
        encoder.encodeHeader(out1, "x-custom", "value", false);
        decoder.decode(out1.toByteArray());
        int firstSize = out1.size();

        // Second block - should use indexed from dynamic table
        var out2 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out2);
        encoder.encodeHeader(out2, "x-custom", "value", false);
        List<String> headers = decoder.decode(out2.toByteArray());
        int secondSize = out2.size();

        assertEquals(2, headers.size());
        assertEquals("x-custom", headers.get(0));
        assertEquals("value", headers.get(1));
        // Second encoding should be smaller (indexed reference)
        assertTrue(secondSize < firstSize);
    }

    @Test
    void encodesSensitiveHeaderNeverIndexed() throws IOException {
        var encoder = new HpackEncoder(4096);
        var decoder = new HpackDecoder(4096);

        // First block - sensitive header
        var out1 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out1);
        encoder.encodeHeader(out1, "x-secret", "password", true);
        decoder.decode(out1.toByteArray());

        // Second block - same header should NOT be indexed
        var out2 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out2);
        encoder.encodeHeader(out2, "x-secret", "password", true);
        List<String> headers = decoder.decode(out2.toByteArray());

        assertEquals(2, headers.size());
        assertEquals("x-secret", headers.get(0));
        // Size should be same (not indexed)
        assertEquals(out1.size(), out2.size());
    }

    @Test
    void authorizationHeaderNeverIndexed() throws IOException {
        var encoder = new HpackEncoder(4096);
        var decoder = new HpackDecoder(4096);

        // First block
        var out1 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out1);
        encoder.encodeHeader(out1, "authorization", "Bearer token", false);
        decoder.decode(out1.toByteArray());

        // Second block - should NOT be indexed even though sensitive=false
        var out2 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out2);
        encoder.encodeHeader(out2, "authorization", "Bearer token", false);
        List<String> headers = decoder.decode(out2.toByteArray());

        assertEquals(2, headers.size());
        assertEquals("authorization", headers.get(0));
        // Size should be same (not indexed)
        assertEquals(out1.size(), out2.size());
    }

    @Test
    void encodesWithoutHuffman() throws IOException {
        var encoder = new HpackEncoder(4096, false);
        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        encoder.encodeHeader(out, "x-test", "hello", false);

        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(out.toByteArray());

        assertEquals(2, headers.size());
        assertEquals("x-test", headers.get(0));
        assertEquals("hello", headers.get(1));
    }

    @Test
    void emitsTableSizeUpdate() throws IOException {
        var encoder = new HpackEncoder(4096);
        var decoder = new HpackDecoder(4096);

        // Change table size
        encoder.setMaxTableSize(2048);

        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        encoder.encodeHeader(out, ":method", "GET", false);

        // Decoder should handle the table size update
        List<String> headers = decoder.decode(out.toByteArray());

        assertEquals(2, headers.size());
        assertEquals(":method", headers.get(0));
    }

    @Test
    void tableSizeUpdateOnlyEmittedOnce() throws IOException {
        var encoder = new HpackEncoder(4096);
        var decoder = new HpackDecoder(4096);

        encoder.setMaxTableSize(2048);

        // First block - should emit table size update
        var out1 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out1);
        encoder.encodeHeader(out1, ":method", "GET", false);
        decoder.decode(out1.toByteArray());
        int firstSize = out1.size();

        // Second block - should NOT emit table size update again
        var out2 = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out2);
        encoder.encodeHeader(out2, ":method", "GET", false);
        decoder.decode(out2.toByteArray());
        int secondSize = out2.size();

        // Second should be smaller (no table size update prefix)
        assertTrue(secondSize < firstSize);
    }

    @Test
    void encodesLargeInteger() throws IOException {
        var encoder = new HpackEncoder(4096);
        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        // Use a long value that requires multi-byte integer encoding
        String longValue = "x".repeat(200);
        encoder.encodeHeader(out, "x-long", longValue, false);

        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(out.toByteArray());

        assertEquals(2, headers.size());
        assertEquals("x-long", headers.get(0));
        assertEquals(longValue, headers.get(1));
    }

    @Test
    void encodesStaticNameWithNewValue() throws IOException {
        var encoder = new HpackEncoder(4096);
        var out = new ByteArrayOutputStream();
        encoder.beginHeaderBlock(out);
        // :path is in static table, but /custom is not
        encoder.encodeHeader(out, ":path", "/custom/path", false);

        var decoder = new HpackDecoder(4096);
        List<String> headers = decoder.decode(out.toByteArray());

        assertEquals(2, headers.size());
        assertEquals(":path", headers.get(0));
        assertEquals("/custom/path", headers.get(1));
    }
}
