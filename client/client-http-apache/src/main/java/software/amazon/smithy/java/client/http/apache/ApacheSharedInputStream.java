/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.io.IOException;
import java.io.InputStream;

final class ApacheSharedInputStream extends InputStream {
    private final ApacheSharedInputBuffer buffer;
    private final ApacheStreamingResponseConsumer owner;
    private boolean eof;

    ApacheSharedInputStream(ApacheSharedInputBuffer buffer, ApacheStreamingResponseConsumer owner) {
        this.buffer = buffer;
        this.owner = owner;
    }

    @Override
    public int available() throws IOException {
        IOException failure = owner.failureAsIOException();
        if (failure != null) {
            throw failure;
        }
        return buffer != null ? buffer.availableBytes() : 0;
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        IOException failure = owner.failureAsIOException();
        if (failure != null) {
            throw failure;
        }
        if (buffer == null) {
            eof = true;
            owner.completeTransport();
            return -1;
        }
        int b = buffer.read();
        if (b == -1) {
            eof = true;
            owner.completeTransport();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (eof) {
            return -1;
        }
        IOException failure = owner.failureAsIOException();
        if (failure != null) {
            throw failure;
        }
        if (buffer == null) {
            eof = true;
            owner.completeTransport();
            return -1;
        }
        int bytesRead = buffer.read(b, off, len);
        if (bytesRead == -1) {
            eof = true;
            owner.completeTransport();
        }
        return bytesRead;
    }

    @Override
    public void close() {
        eof = true;
        if (buffer != null) {
            buffer.abort();
        }
        owner.completeTransport();
    }
}
