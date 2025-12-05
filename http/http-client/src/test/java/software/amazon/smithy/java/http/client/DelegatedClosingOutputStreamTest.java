/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DelegatedClosingOutputStreamTest {

    @Test
    void callsOnCloseCallback() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var closeCount = new AtomicInteger(0);

        var stream = new DelegatedClosingOutputStream(delegate, closeCount::incrementAndGet);
        stream.close();

        assertEquals(1, closeCount.get());
    }

    @Test
    void callsOnCloseOnlyOnce() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var closeCount = new AtomicInteger(0);

        var stream = new DelegatedClosingOutputStream(delegate, closeCount::incrementAndGet);
        stream.close();
        stream.close();
        stream.close();

        assertEquals(1, closeCount.get());
    }

    @Test
    void writesToDelegate() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new DelegatedClosingOutputStream(delegate, () -> {});

        stream.write(new byte[] {1, 2, 3});
        stream.flush();

        assertArrayEquals(new byte[] {1, 2, 3}, delegate.toByteArray());
    }
}
