/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DelegatedClosingInputStreamTest {

    @Test
    void callsCloseCallbackWithDelegate() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var closeCount = new AtomicInteger(0);
        var passedDelegate = new AtomicReference<InputStream>();

        var stream = new DelegatedClosingInputStream(delegate, in -> {
            passedDelegate.set(in);
            closeCount.incrementAndGet();
        });
        stream.close();

        assertEquals(1, closeCount.get());
        assertSame(delegate, passedDelegate.get());
    }

    @Test
    void callsCloseCallbackOnlyOnce() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var closeCount = new AtomicInteger(0);

        var stream = new DelegatedClosingInputStream(delegate, in -> closeCount.incrementAndGet());
        stream.close();
        stream.close();
        stream.close();

        assertEquals(1, closeCount.get());
    }

    @Test
    void readsFromDelegate() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new DelegatedClosingInputStream(delegate, in -> {});

        assertEquals(1, stream.read());
        assertEquals(2, stream.read());
        assertEquals(3, stream.read());
        assertEquals(-1, stream.read());
    }
}
