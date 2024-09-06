/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

// Copied from jackson data-bind.
final class ByteBufferBackedInputStream extends InputStream {

    private final ByteBuffer _b;

    public ByteBufferBackedInputStream(ByteBuffer buf) {
        _b = buf;
    }

    @Override
    public int available() {
        return _b.remaining();
    }

    @Override
    public int read() throws IOException {
        return _b.hasRemaining() ? (_b.get() & 0xFF) : -1;
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        if (!_b.hasRemaining()) {
            return -1;
        }
        len = Math.min(len, _b.remaining());
        _b.get(bytes, off, len);
        return len;
    }
}
