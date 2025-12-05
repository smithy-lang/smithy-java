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
    void skipsBytes() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new UnsyncBufferedInputStream(delegate, 8);

        assertEquals(2, stream.skip(2));
        assertEquals(3, stream.read());
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
    void readLineReturnsLine() throws IOException {
        var data = "Hello\r\nWorld\n".getBytes(StandardCharsets.US_ASCII);
        var delegate = new ByteArrayInputStream(data);
        var stream = new UnsyncBufferedInputStream(delegate, 64);

        byte[] buf = new byte[64];
        int len = stream.readLine(buf, 64);
        assertEquals("Hello", new String(buf, 0, len, StandardCharsets.US_ASCII));

        len = stream.readLine(buf, 64);
        assertEquals("World", new String(buf, 0, len, StandardCharsets.US_ASCII));
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
}
