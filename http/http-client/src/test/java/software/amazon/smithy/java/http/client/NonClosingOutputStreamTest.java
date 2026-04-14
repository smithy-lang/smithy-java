/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NonClosingOutputStreamTest {

    @Test
    void doesNotCloseDelegate() throws IOException {
        var delegateClosed = new AtomicInteger(0);
        var delegate = new ByteArrayOutputStream() {
            @Override
            public void close() {
                delegateClosed.incrementAndGet();
            }
        };

        var stream = new NonClosingOutputStream(delegate);
        stream.write(new byte[] {1, 2, 3});
        stream.close();

        assertEquals(0, delegateClosed.get());
        assertArrayEquals(new byte[] {1, 2, 3}, delegate.toByteArray());
    }

    @Test
    void flushesOnClose() throws IOException {
        var flushCount = new AtomicInteger(0);
        var delegate = new ByteArrayOutputStream() {
            @Override
            public void flush() {
                flushCount.incrementAndGet();
            }
        };

        var stream = new NonClosingOutputStream(delegate);
        stream.close();

        assertTrue(flushCount.get() >= 1);
    }

    @Test
    void throwsAfterClose() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new NonClosingOutputStream(delegate);
        stream.close();

        assertThrows(IOException.class, () -> stream.write(1));
        assertThrows(IOException.class, () -> stream.write(new byte[] {1, 2, 3}, 0, 3));
    }
}
