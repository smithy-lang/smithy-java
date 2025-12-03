/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.InputStream;

/**
 * Input stream for reading response body from DATA frames.
 */
final class H2DataInputStream extends InputStream {
    private final H2Exchange exchange;
    private StreamEvent.DataChunk currentChunk;
    private int chunkPos;
    private boolean closed = false;
    private boolean eof = false;

    H2DataInputStream(H2Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public int read() throws IOException {
        if (closed || eof) {
            return -1;
        } else if ((currentChunk == null || chunkPos >= currentChunk.length()) && !loadNextChunk()) {
            return -1;
        }

        return currentChunk.data()[currentChunk.offset() + chunkPos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed || eof) {
            return -1;
        } else if (len == 0) {
            return 0;
        }

        int totalRead = 0;
        while (len > 0) {
            if (currentChunk == null || chunkPos >= currentChunk.length()) {
                if (!loadNextChunk()) {
                    return totalRead > 0 ? totalRead : -1;
                }
            }

            int available = currentChunk.length() - chunkPos;
            int toCopy = Math.min(available, len);
            System.arraycopy(currentChunk.data(), currentChunk.offset() + chunkPos, b, off, toCopy);
            chunkPos += toCopy;
            off += toCopy;
            len -= toCopy;
            totalRead += toCopy;
        }

        return totalRead;
    }

    private boolean loadNextChunk() throws IOException {
        currentChunk = exchange.readDataChunk();
        chunkPos = 0;
        if (currentChunk.isEnd()) {
            eof = true;
            return false;
        }
        return true;
    }

    @Override
    public int available() {
        if (currentChunk != null && !currentChunk.isEnd()) {
            return currentChunk.length() - chunkPos;
        }
        return 0;
    }

    @Override
    public void close() {
        closed = true;
    }
}
