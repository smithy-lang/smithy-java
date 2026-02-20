/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UnsyncBufferedInputStreamTest {

    @Test
    void readsSingleBytes() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(1, stream.read());
        assertEquals(2, stream.read());
        assertEquals(3, stream.read());
        assertEquals(-1, stream.read());
    }

    @Test
    void readsIntoArray() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        byte[] buf = new byte[3];
        assertEquals(3, stream.read(buf));
        assertArrayEquals(new byte[] {1, 2, 3}, buf);
    }

    @Test
    void readArrayDelegatesToReadWithOffsetAndLength() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        byte[] buf = new byte[5];
        assertEquals(3, stream.read(buf));
        assertArrayEquals(new byte[] {1, 2, 3, 0, 0}, buf);
    }

    @Test
    void readWithOffsetAndLength() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        byte[] buf = new byte[10];
        assertEquals(3, stream.read(buf, 2, 3));
        assertArrayEquals(new byte[] {0, 0, 1, 2, 3, 0, 0, 0, 0, 0}, buf);
    }

    @Test
    void readReturnsZeroWhenLenIsZero() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(0, stream.read(new byte[10], 0, 0));
    }

    @Test
    void readThrowsOnNegativeOffset() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[10], -1, 5));
    }

    @Test
    void readThrowsOnNegativeLength() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[10], 0, -1));
    }

    @Test
    void readThrowsWhenLengthExceedsArrayBounds() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[10], 5, 10));
    }

    @Test
    void readBypassesBufferForLargeRequests() throws IOException {
        var data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        byte[] buf = new byte[100];
        assertEquals(100, stream.read(buf));
        assertArrayEquals(data, buf);
    }

    @Test
    void readDrainsBufferThenRefills() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        var stream = new UnsyncBufferedInputStream(delegate, 4);

        // First read fills buffer with [1,2,3,4], returns 3
        byte[] buf = new byte[3];
        assertEquals(3, stream.read(buf));
        assertArrayEquals(new byte[] {1, 2, 3}, buf);

        // Second read drains remaining [4], refills with [5,6,7,8], returns 3
        assertEquals(3, stream.read(buf));
        assertArrayEquals(new byte[] {4, 5, 6}, buf);
    }

    @Test
    void readReturnsMinusOneOnEmptyStream() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[10]));
    }

    @Test
    void readReturnsPartialDataThenMinusOne() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        byte[] buf = new byte[10];
        assertEquals(2, stream.read(buf));
        assertEquals(-1, stream.read(buf));
    }

    @Test
    void readThrowsWhenClosed() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, () -> stream.read(new byte[10], 0, 5));
    }

    @Test
    void skipsBytes() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(2, stream.skip(2));
        assertEquals(3, stream.read());
    }

    @Test
    void skipReturnsZeroForNonPositive() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(0, stream.skip(0));
        assertEquals(0, stream.skip(-5));
    }

    @Test
    void skipThrowsWhenClosed() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, () -> stream.skip(1));
    }

    @Test
    void skipDrainsBufferThenSkipsUnderlying() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        var stream = new UnsyncBufferedInputStream(delegate, 4);

        // Fill buffer first
        stream.read();

        // Skip more than buffer has (3 in buffer + some from underlying)
        assertEquals(6, stream.skip(6));
        assertEquals(8, stream.read());
    }

    @Test
    void availableReturnsBufferedPlusUnderlying() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Before any read, buffer is empty
        assertEquals(5, stream.available());

        // After read, buffer has data
        stream.read();
        assertEquals(4, stream.available());
    }

    @Test
    void availableThrowsWhenClosed() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, stream::available);
    }

    @Test
    void closeIsIdempotent() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        stream.close();
        stream.close(); // Should not throw
    }

    @Test
    void transfersToOutputStream() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        var out = new ByteArrayOutputStream();

        assertEquals(5, stream.transferTo(out));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, out.toByteArray());
    }

    @Test
    void transferToDrainsBufferFirst() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Read one byte to fill buffer
        assertEquals(1, stream.read());

        var out = new ByteArrayOutputStream();
        assertEquals(4, stream.transferTo(out));
        assertArrayEquals(new byte[] {2, 3, 4, 5}, out.toByteArray());
    }

    @Test
    void transferToThrowsWhenClosed() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, () -> stream.transferTo(new ByteArrayOutputStream()));
    }

    @Test
    void readLineReturnsLine() throws IOException {
        var data = "Hello\r\nWorld\n".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        byte[] buf = new byte[64];
        int len = stream.readLine(buf, 64);
        assertEquals(5, len);
        assertEquals("Hello", new String(buf, 0, len, StandardCharsets.US_ASCII));

        len = stream.readLine(buf, 64);
        assertEquals(5, len);
        assertEquals("World", new String(buf, 0, len, StandardCharsets.US_ASCII));
    }

    @Test
    void readLineHandlesCrOnly() throws IOException {
        var data = "Hello\rWorld".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        byte[] buf = new byte[64];
        int len = stream.readLine(buf, 64);
        assertEquals(5, len);
        assertEquals("Hello", new String(buf, 0, len, StandardCharsets.US_ASCII));

        len = stream.readLine(buf, 64);
        assertEquals(5, len);
        assertEquals("World", new String(buf, 0, len, StandardCharsets.US_ASCII));
    }

    @Test
    void readLineReturnsMinusOneOnEmptyStream() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {});
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        assertEquals(-1, stream.readLine(new byte[64], 64));
    }

    @Test
    void readLineReturnsDataWithoutTerminatorAtEof() throws IOException {
        var data = "Hello".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        byte[] buf = new byte[64];
        int len = stream.readLine(buf, 64);
        assertEquals("Hello", new String(buf, 0, len, StandardCharsets.US_ASCII));
    }

    @Test
    void readLineThrowsWhenExceedsMaxLength() throws IOException {
        var data = "HelloWorld\r\n".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        assertThrows(IOException.class, () -> stream.readLine(new byte[64], 5));
    }

    @Test
    void readLineThrowsWhenExceedsBufferSize() throws IOException {
        var data = "HelloWorld\r\n".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        assertThrows(IOException.class, () -> stream.readLine(new byte[5], 64));
    }

    @Test
    void readLineThrowsWhenClosed() throws IOException {
        var delegate = new ByteArrayInputStream("Hello\r\n".getBytes(StandardCharsets.US_ASCII));
        var stream = new UnsyncBufferedInputStream(delegate, 64);
        stream.close();

        assertThrows(IOException.class, () -> stream.readLine(new byte[64], 64));
    }

    @Test
    void readLineHandlesEmptyLine() throws IOException {
        var data = "\r\nHello\r\n".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        byte[] buf = new byte[64];
        int len = stream.readLine(buf, 64);
        assertEquals(0, len);

        len = stream.readLine(buf, 64);
        assertEquals("Hello", new String(buf, 0, len, StandardCharsets.US_ASCII));
    }

    @Test
    void readLineSpansMultipleBufferFills() throws IOException {
        var data = "HelloWorld\r\n".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 4); // Small buffer

        byte[] buf = new byte[64];
        int len = stream.readLine(buf, 64);
        assertEquals("HelloWorld", new String(buf, 0, len, StandardCharsets.US_ASCII));
    }

    @Test
    void throwsAfterClose() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, stream::read);
    }

    @Test
    void throwsOnInvalidBufferSize() {
        var delegate = new ByteArrayInputStream(new byte[] {});
        assertThrows(IllegalArgumentException.class,
                () -> new UnsyncBufferedInputStream(delegate, 0));
    }

    @Test
    void throwsOnNegativeBufferSize() {
        var delegate = new ByteArrayInputStream(new byte[] {});
        assertThrows(IllegalArgumentException.class,
                () -> new UnsyncBufferedInputStream(delegate, -1));
    }

    // ==================== Direct Buffer Access Tests ====================

    @Test
    void bufferReturnsInternalBuffer() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Trigger a read to fill the buffer
        stream.read();

        byte[] buf = stream.buffer();
        assertEquals(8, buf.length); // Buffer size we specified
    }

    @Test
    void positionAndLimitTrackBufferState() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Initially empty
        assertEquals(0, stream.position());
        assertEquals(0, stream.limit());
        assertEquals(0, stream.buffered());

        // After read, buffer is filled
        stream.read();
        assertEquals(1, stream.position()); // Advanced by one read
        assertEquals(5, stream.limit()); // All 5 bytes loaded
        assertEquals(4, stream.buffered()); // 4 remaining
    }

    @Test
    void consumeAdvancesPosition() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Fill buffer
        stream.read();
        int initialPos = stream.position();

        stream.consume(2);
        assertEquals(initialPos + 2, stream.position());
        assertEquals(2, stream.buffered());
    }

    @Test
    void consumeThrowsOnOverflow() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Fill buffer
        stream.read();

        assertThrows(IndexOutOfBoundsException.class, () -> stream.consume(10));
    }

    @Test
    void consumeThrowsOnNegative() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        stream.read();

        assertThrows(IndexOutOfBoundsException.class, () -> stream.consume(-1));
    }

    @Test
    void ensureReturnsTrueWhenDataAvailable() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(true, stream.ensure(5));
        // ensure() reads at least 5 bytes, may read more (up to buffer size)
        assertEquals(true, stream.buffered() >= 5);
    }

    @Test
    void ensureCompactsAndFillsBuffer() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Fill buffer and consume some
        stream.ensure(8); // Fill entire buffer [1,2,3,4,5,6,7,8]
        stream.consume(6); // Now position=6, only 2 bytes left [7,8]

        // Ensure more than available - should compact and fill
        assertEquals(true, stream.ensure(4));
        // After compacting, position should be 0
        assertEquals(0, stream.position());
        assertEquals(true, stream.buffered() >= 4);

        // Verify data integrity - after consuming bytes 1-6, next byte should be 7
        byte[] buf = stream.buffer();
        assertEquals(7, buf[0]); // First unread byte after consuming 1-6
    }

    @Test
    void ensureReturnsFalseOnEof() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Try to ensure more bytes than available (but within buffer size)
        assertEquals(false, stream.ensure(5));
        // But we should have whatever was available
        assertEquals(3, stream.buffered());
    }

    @Test
    void ensureThrowsWhenRequestExceedsBufferSize() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 4);

        assertThrows(IllegalArgumentException.class, () -> stream.ensure(10));
    }

    @Test
    void ensureReturnsTrueForZeroOrNegative() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(true, stream.ensure(0));
        assertEquals(true, stream.ensure(-5));
    }

    @Test
    void ensureThrowsWhenClosed() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, () -> stream.ensure(1));
    }

    @Test
    void readDirectBypassesBuffer() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Buffer is empty initially, so readDirect should work
        byte[] buf = new byte[3];
        int n = stream.readDirect(buf, 0, 3);
        assertEquals(3, n);
        assertArrayEquals(new byte[] {1, 2, 3}, buf);
    }

    @Test
    void readDirectThrowsIfBufferNotEmpty() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        // Fill the buffer by reading one byte
        stream.read();

        // Now buffer has data, readDirect should throw
        assertThrows(IllegalStateException.class, () -> stream.readDirect(new byte[3], 0, 3));
    }

    @Test
    void readDirectWorksAfterDrainingBuffer() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        var stream = new UnsyncBufferedInputStream(delegate, 4);

        // Fill buffer [1,2,3,4] and consume all
        stream.ensure(4);
        stream.consume(4);

        // Buffer is now empty, readDirect should work
        byte[] buf = new byte[4];
        int n = stream.readDirect(buf, 0, 4);
        assertEquals(4, n);
        assertArrayEquals(new byte[] {5, 6, 7, 8}, buf);
    }

    @Test
    void readDirectThrowsWhenClosed() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new UnsyncBufferedInputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, () -> stream.readDirect(new byte[3], 0, 3));
    }

    @Test
    void directBufferAccessForZeroCopyParsing() throws IOException {
        // Simulate zero-copy frame header parsing like H2FrameCodec does
        byte[] frameData = {
                0,
                0,
                10, // length = 10
                0, // type = DATA
                1, // flags = END_STREAM
                0,
                0,
                0,
                1, // stream ID = 1
                'H',
                'e',
                'l',
                'l',
                'o',
                ' ',
                'W',
                'o',
                'r',
                'l' // payload
        };
        var delegate = new ByteArrayInputStream(frameData);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        // Ensure 9-byte header is available
        assertEquals(true, stream.ensure(9));

        // Parse header directly from buffer (zero-copy)
        byte[] buf = stream.buffer();
        int p = stream.position();

        int length = ((buf[p] & 0xFF) << 16)
                | ((buf[p + 1] & 0xFF) << 8)
                | (buf[p + 2] & 0xFF);
        int type = buf[p + 3] & 0xFF;
        int flags = buf[p + 4] & 0xFF;
        int streamId = ((buf[p + 5] & 0x7F) << 24)
                | ((buf[p + 6] & 0xFF) << 16)
                | ((buf[p + 7] & 0xFF) << 8)
                | (buf[p + 8] & 0xFF);

        stream.consume(9);

        assertEquals(10, length);
        assertEquals(0, type);
        assertEquals(1, flags);
        assertEquals(1, streamId);

        // Now read the payload
        byte[] payload = new byte[length];
        assertEquals(length, stream.read(payload));
        assertEquals("Hello Worl", new String(payload, StandardCharsets.US_ASCII));
    }
}
