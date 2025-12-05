/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class BoundedInputStreamTest {

    @Test
    void readsExactlyBoundedBytes() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new BoundedInputStream(delegate, 3);

        assertEquals(1, stream.read());
        assertEquals(2, stream.read());
        assertEquals(3, stream.read());
        assertEquals(-1, stream.read());
    }

    @Test
    void readArrayRespectsBound() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new BoundedInputStream(delegate, 3);

        byte[] buf = new byte[10];
        int n = stream.read(buf, 0, 10);

        assertEquals(3, n);
        assertArrayEquals(new byte[] {1, 2, 3}, java.util.Arrays.copyOf(buf, n));
    }

    @Test
    void availableRespectsBound() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new BoundedInputStream(delegate, 3);

        assertEquals(3, stream.available());
    }

    @Test
    void skipRespectsBound() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new BoundedInputStream(delegate, 3);

        assertEquals(3, stream.skip(10));
        assertEquals(-1, stream.read());
    }

    @Test
    void closeDrainsRemainingBytes() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var stream = new BoundedInputStream(delegate, 3);

        stream.read(); // read 1 byte
        stream.close();

        // Delegate should have been drained to byte 4
        assertEquals(4, delegate.read());
    }

    @Test
    void throwsOnPrematureEof() {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2});
        var stream = new BoundedInputStream(delegate, 5);

        assertThrows(IOException.class, () -> {
            while (stream.read() != -1) {
                // drain
            }
        });
    }
}
