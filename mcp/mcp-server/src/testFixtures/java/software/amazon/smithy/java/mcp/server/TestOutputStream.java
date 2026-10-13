/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

final class TestOutputStream extends OutputStream {
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    @Override
    public void write(int b) {
        baos.write(b);
        if (b == '\n') {
            lines.add(baos.toString(StandardCharsets.UTF_8));
            baos.reset();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) {
        int end = off + len;
        int pos = off;
        while (pos < end) {
            int nl = find(b, pos, end, (byte) '\n');
            if (nl == -1) {
                baos.write(b, pos, end - pos);
                return;
            }
            baos.write(b, pos, nl - pos);
            lines.add(baos.toString(StandardCharsets.UTF_8));
            baos.reset();
            pos = nl + 1;
        }
    }

    private static int find(byte[] arr, int start, int end, byte b) {
        if (start >= end || end > arr.length) {
            throw new IllegalArgumentException();
        }
        for (int i = start; i < end; i++) {
            if (arr[i] == b) {
                return i;
            }
        }
        return -1;
    }

    String read() {
        try {
            return lines.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    boolean hasOutput() {
        return !lines.isEmpty();
    }

    void assertNoOutput() {
        assertNoOutput(50);
    }

    void assertNoOutput(long waitMillis) {
        // Wait briefly to allow any potential response to be written
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Verify no output was produced
        if (hasOutput()) {
            throw new AssertionError("Expected no output but got : " + read());
        }
    }
}
