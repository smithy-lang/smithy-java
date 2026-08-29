/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

final class TestInputStream extends InputStream {
    private byte[] onDeck;
    private int pos;
    private final BlockingQueue<byte[]> bytes = new LinkedBlockingQueue<>();

    void write(String s) {
        bytes.add(s.getBytes(StandardCharsets.UTF_8));
    }

    void write(byte[] bytes) {
        this.bytes.add(bytes);
    }

    @Override
    public int read() {
        load(true);
        return onDeck[pos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        int rem = len;
        int read = 0;
        boolean first = true;
        while (rem > 0) {
            if (load(first) || onDeck == null) {
                break;
            }
            first = false;
            int toRead = Math.min(onDeck.length - pos, rem);
            System.arraycopy(onDeck, pos, b, off, toRead);
            pos += toRead;
            off += toRead;
            rem -= toRead;
            read += toRead;
        }
        return read;
    }

    private boolean load(boolean first) {
        try {
            if (onDeck == null || pos == onDeck.length) {
                onDeck = first ? bytes.take() : bytes.poll();
                pos = 0;
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
