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
        int rem = len;
        int pos = off;
        while (rem > 0) {
            int nl = find(b, pos, pos + rem, (byte) '\n');
            if (nl == -1) {
                baos.write(b, off, len);
                return;
            } else {
                int toWrite = nl - off;
                baos.write(b, off, toWrite);
                lines.add(baos.toString(StandardCharsets.UTF_8));
                baos.reset();
                rem -= toWrite;
                pos += toWrite;
            }
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
}
