/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * ByteBuffer-based frame writer that replaces UnsyncBufferedOutputStream for H2.
 *
 * <p>Accumulates frame data into a ByteBuffer and flushes to a
 * {@link WritableByteChannel} (backed by SSLEngine or socket).
 * Multiple frames are coalesced into a single channel write for
 * fewer syscalls.
 *
 * <p>Thread safety: single writer thread only (H2Muxer writer thread).
 */
final class ChannelFrameWriter {

    private final WritableByteChannel channel;
    private final ByteBuffer buf;

    ChannelFrameWriter(WritableByteChannel channel, int bufferSize) {
        this.channel = channel;
        this.buf = ByteBuffer.allocate(bufferSize);
    }

    /**
     * Write bytes from a byte array. Flushes if buffer is full.
     */
    void write(byte[] src) throws IOException {
        write(src, 0, src.length);
    }

    /**
     * Write bytes from a byte array with offset/length.
     */
    void write(byte[] src, int offset, int length) throws IOException {
        if (length >= buf.capacity()) {
            // Large write: flush buffer, then write directly
            flushBuffer();
            ByteBuffer wrapped = ByteBuffer.wrap(src, offset, length);
            while (wrapped.hasRemaining()) {
                channel.write(wrapped);
            }
            return;
        }

        if (length > buf.remaining()) {
            flushBuffer();
        }
        buf.put(src, offset, length);
    }

    /**
     * Write from a ByteBuffer. Zero-copy for DATA frame payloads.
     */
    void write(ByteBuffer src) throws IOException {
        int length = src.remaining();
        if (length >= buf.capacity()) {
            flushBuffer();
            while (src.hasRemaining()) {
                channel.write(src);
            }
            return;
        }

        if (length > buf.remaining()) {
            flushBuffer();
        }
        buf.put(src);
    }

    /**
     * Write an ASCII string directly without allocation.
     */
    @SuppressWarnings("deprecation")
    void writeAscii(String s) throws IOException {
        int len = s.length();
        if (len == 0)
            return;

        if (len > buf.remaining()) {
            flushBuffer();
        }
        if (len > buf.capacity()) {
            // Rare: very long string, write in chunks
            byte[] tmp = new byte[len];
            s.getBytes(0, len, tmp, 0);
            write(tmp, 0, len);
            return;
        }
        // Write directly into buffer's backing array
        s.getBytes(0, len, buf.array(), buf.arrayOffset() + buf.position());
        buf.position(buf.position() + len);
    }

    /**
     * Flush buffered data to the channel.
     */
    void flush() throws IOException {
        flushBuffer();
    }

    private void flushBuffer() throws IOException {
        if (buf.position() > 0) {
            buf.flip();
            while (buf.hasRemaining()) {
                channel.write(buf);
            }
            buf.clear();
        }
    }
}
