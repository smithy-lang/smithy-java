/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class UnsyncBufferedOutputStreamTest {

    @Test
    void writesSingleBytes() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new UnsyncBufferedOutputStream(delegate, 8);

        stream.write(1);
        stream.write(2);
        stream.write(3);
        stream.flush();

        assertArrayEquals(new byte[] {1, 2, 3}, delegate.toByteArray());
    }

    @Test
    void writesArray() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new UnsyncBufferedOutputStream(delegate, 8);

        stream.write(new byte[] {1, 2, 3, 4, 5});
        stream.flush();

        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, delegate.toByteArray());
    }

    @Test
    void writesAsciiString() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new UnsyncBufferedOutputStream(delegate, 8);

        stream.writeAscii("Hello");
        stream.flush();

        assertEquals("Hello", delegate.toString());
    }

    @Test
    void flushesOnClose() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new UnsyncBufferedOutputStream(delegate, 8);

        stream.write(new byte[] {1, 2, 3});
        stream.close();

        assertArrayEquals(new byte[] {1, 2, 3}, delegate.toByteArray());
    }

    @Test
    void throwsAfterClose() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new UnsyncBufferedOutputStream(delegate, 8);
        stream.close();

        assertThrows(IOException.class, () -> stream.write(1));
    }

    @Test
    void throwsOnInvalidBufferSize() {
        var delegate = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class,
                () -> new UnsyncBufferedOutputStream(delegate, 0));
    }

    @Test
    void largeWriteBypassesBuffer() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new UnsyncBufferedOutputStream(delegate, 4);

        stream.write(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        stream.flush();

        assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}, delegate.toByteArray());
    }
}
