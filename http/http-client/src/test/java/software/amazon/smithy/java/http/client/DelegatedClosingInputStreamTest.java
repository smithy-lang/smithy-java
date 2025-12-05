/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DelegatedClosingInputStreamTest {

    @Test
    void callsOnCloseCallback() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var closeCount = new AtomicInteger(0);

        var stream = new DelegatedClosingInputStream(delegate, closeCount::incrementAndGet);
        stream.close();

        assertEquals(1, closeCount.get());
    }

    @Test
    void callsOnCloseOnlyOnce() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var closeCount = new AtomicInteger(0);

        var stream = new DelegatedClosingInputStream(delegate, closeCount::incrementAndGet);
        stream.close();
        stream.close();
        stream.close();

        assertEquals(1, closeCount.get());
    }

    @Test
    void readsFromDelegate() throws IOException {
        var delegate = new ByteArrayInputStream(new byte[] {1, 2, 3});
        var stream = new DelegatedClosingInputStream(delegate, () -> {});

        assertEquals(1, stream.read());
        assertEquals(2, stream.read());
        assertEquals(3, stream.read());
        assertEquals(-1, stream.read());
    }
}
