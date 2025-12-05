/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DelegatedClosingOutputStreamTest {

    @Test
    void callsCloseCallbackWithDelegate() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var closeCount = new AtomicInteger(0);
        var passedDelegate = new AtomicReference<OutputStream>();

        var stream = new DelegatedClosingOutputStream(delegate, out -> {
            passedDelegate.set(out);
            closeCount.incrementAndGet();
        });
        stream.close();

        assertEquals(1, closeCount.get());
        assertSame(delegate, passedDelegate.get());
    }

    @Test
    void callsCloseCallbackOnlyOnce() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var closeCount = new AtomicInteger(0);

        var stream = new DelegatedClosingOutputStream(delegate, out -> closeCount.incrementAndGet());
        stream.close();
        stream.close();
        stream.close();

        assertEquals(1, closeCount.get());
    }

    @Test
    void writesToDelegate() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new DelegatedClosingOutputStream(delegate, out -> {});

        stream.write(new byte[] {1, 2, 3});
        stream.flush();

        assertArrayEquals(new byte[] {1, 2, 3}, delegate.toByteArray());
    }
}
