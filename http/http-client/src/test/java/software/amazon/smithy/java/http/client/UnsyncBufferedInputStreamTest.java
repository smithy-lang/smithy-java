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
}
