/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;

class ChunkedInputStreamTest {

    private ChunkedInputStream chunked(String data) {
        var bytes = data.getBytes(StandardCharsets.US_ASCII);
        return new ChunkedInputStream(new UnsyncBufferedInputStream(new ByteArrayInputStream(bytes), 256));
    }

    @Test
    void readsSingleChunk() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");

        byte[] result = stream.readAllBytes();

        assertArrayEquals("hello".getBytes(), result);
    }

    @Test
    void readsMultipleChunks() throws IOException {
        var stream = chunked("5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n");

        byte[] result = stream.readAllBytes();

        assertArrayEquals("hello world".getBytes(), result);
    }

    @Test
    void readsEmptyBody() throws IOException {
        var stream = chunked("0\r\n\r\n");

        byte[] result = stream.readAllBytes();

        assertEquals(0, result.length);
    }

    @Test
    void readsSingleByte() throws IOException {
        var stream = chunked("3\r\nabc\r\n0\r\n\r\n");

        assertEquals('a', stream.read());
        assertEquals('b', stream.read());
        assertEquals('c', stream.read());
        assertEquals(-1, stream.read());
    }

    @Test
    void readsUppercaseHex() throws IOException {
        var stream = chunked("A\r\n0123456789\r\n0\r\n\r\n");

        byte[] result = stream.readAllBytes();

        assertEquals(10, result.length);
    }

    @Test
    void readsLowercaseHex() throws IOException {
        var stream = chunked("a\r\n0123456789\r\n0\r\n\r\n");

        byte[] result = stream.readAllBytes();

        assertEquals(10, result.length);
    }

    @Test
    void ignoresChunkExtensions() throws IOException {
        var stream = chunked("5;name=value\r\nhello\r\n0\r\n\r\n");

        byte[] result = stream.readAllBytes();

        assertArrayEquals("hello".getBytes(), result);
    }

    @Test
    void parsesTrailers() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\nX-Checksum: abc123\r\n\r\n");
        stream.readAllBytes();

        var trailers = stream.getTrailers();

        assertEquals("abc123", trailers.firstValue("x-checksum"));
    }

    @Test
    void parsesMultipleTrailers() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\nX-Foo: bar\r\nX-Baz: qux\r\n\r\n");
        stream.readAllBytes();

        var trailers = stream.getTrailers();

        assertEquals("bar", trailers.firstValue("x-foo"));
        assertEquals("qux", trailers.firstValue("x-baz"));
    }

    @Test
    void trailersNullBeforeEof() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\nX-Foo: bar\r\n\r\n");

        assertNull(stream.getTrailers());
    }

    @Test
    void trailersNullWhenNonePresent() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");
        stream.readAllBytes();

        assertNull(stream.getTrailers());
    }

    @Test
    void availableReturnsChunkRemaining() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");
        stream.read(); // read 'h'

        int available = stream.available();

        assertEquals(4, available);
    }

    @Test
    void skipSkipsBytes() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");

        long skipped = stream.skip(3);

        assertEquals(3, skipped);
        assertEquals('l', stream.read());
        assertEquals('o', stream.read());
    }

    @Test
    void closeDrainsAndParsesTrailers() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\nX-Foo: bar\r\n\r\n");
        stream.close();

        var trailers = stream.getTrailers();

        assertEquals("bar", trailers.firstValue("x-foo"));
    }

    @Test
    void closeIsIdempotent() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");

        stream.close();
        stream.close();

        assertEquals(-1, stream.read());
    }

    @Test
    void throwsOnInvalidHex() {
        var stream = chunked("xyz\r\nhello\r\n0\r\n\r\n");

        assertThrows(IOException.class, stream::read);
    }

    @Test
    void throwsOnMissingCrlf() {
        var stream = chunked("5\r\nhelloX");

        assertThrows(IOException.class, stream::readAllBytes);
    }

    @Test
    void throwsOnUnexpectedEofInSingleByteRead() {
        var stream = chunked("5\r\nhi");

        assertThrows(IOException.class, () -> {
            stream.read();
            stream.read();
            stream.read(); // expects 5 bytes but only 2 available
        });
    }

    @Test
    void throwsOnUnexpectedEofInBulkRead() {
        var stream = chunked("5\r\nhi");

        assertThrows(IOException.class, () -> {
            // First read returns 2 bytes, second read hits EOF
            byte[] buf = new byte[10];
            stream.read(buf, 0, 10);
            stream.read(buf, 0, 10);
        });
    }

    @Test
    void readReturnsNegativeOneAfterClose() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");
        stream.close();

        assertEquals(-1, stream.read());
    }

    @Test
    void readReturnsNegativeOneAfterEof() throws IOException {
        var stream = chunked("0\r\n\r\n");
        stream.readAllBytes();

        assertEquals(-1, stream.read());
    }

    @Test
    void bulkReadReturnsNegativeOneAfterEof() throws IOException {
        var stream = chunked("0\r\n\r\n");
        stream.readAllBytes();

        assertEquals(-1, stream.read(new byte[10], 0, 10));
    }

    @Test
    void skipReturnsZeroAfterEof() throws IOException {
        var stream = chunked("0\r\n\r\n");
        stream.readAllBytes();

        assertEquals(0, stream.skip(10));
    }

    @Test
    void skipReturnsZeroWhenClosed() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");
        stream.close();

        assertEquals(0, stream.skip(10));
    }

    @Test
    void skipStopsAtEof() throws IOException {
        var stream = chunked("3\r\nabc\r\n0\r\n\r\n");

        long skipped = stream.skip(100);

        assertEquals(3, skipped);
    }

    @Test
    void availableReturnsZeroAfterEof() throws IOException {
        var stream = chunked("0\r\n\r\n");
        stream.readAllBytes();

        assertEquals(0, stream.available());
    }

    @Test
    void availableReturnsZeroWhenClosed() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");
        stream.close();

        assertEquals(0, stream.available());
    }

    @Test
    void availableReturnsZeroBeforeFirstRead() throws IOException {
        var stream = chunked("5\r\nhello\r\n0\r\n\r\n");

        assertEquals(0, stream.available());
    }

    @Test
    void throwsOnEmptyChunkSizeLine() {
        var stream = chunked("\r\nhello\r\n0\r\n\r\n");

        assertThrows(IOException.class, stream::read);
    }

    @Test
    void throwsOnMissingChunkSize() {
        var stream = chunked(";extension\r\nhello\r\n0\r\n\r\n");

        assertThrows(IOException.class, stream::read);
    }

    @Test
    void throwsOnInvalidCrlfAfterChunk() {
        var stream = chunked("5\r\nhello\n\n0\r\n\r\n");

        assertThrows(IOException.class, stream::readAllBytes);
    }

    @Test
    void throwsOnTruncatedCrlfAfterChunk() {
        var stream = chunked("5\r\nhello\r");

        assertThrows(IOException.class, stream::readAllBytes);
    }

    @Test
    void throwsOnChunkSizeExceedsMax() {
        // 0x100000000 = 4GB, way over the 1MB default max
        var stream = chunked("100000000\r\n");

        assertThrows(IOException.class, stream::read);
    }

    @Test
    void throwsOnChunkSizeOverflow() {
        // 17 hex digits would overflow a long (max is 16 hex digits = 64 bits)
        var stream = chunked("FFFFFFFFFFFFFFFFF\r\n");

        assertThrows(IOException.class, stream::read);
    }

    @Test
    void throwsOnInvalidTrailerLine() {
        // Trailer line without colon is invalid
        var stream = chunked("5\r\nhello\r\n0\r\ninvalidtrailer\r\n\r\n");

        assertThrows(IOException.class, stream::readAllBytes);
    }
}
