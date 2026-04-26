/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.function.BooleanSupplier;

/**
 * ByteBuffer-based frame reader that replaces UnsyncBufferedInputStream for H2.
 *
 * <p>Reads plaintext from a {@link ReadableByteChannel} (backed by SSLEngine or socket)
 * into a single ByteBuffer. The frame codec parses directly from this buffer,
 * eliminating intermediate copies.
 *
 * <p>The buffer is always in read mode externally. Internally, it compacts and
 * fills from the channel when more data is needed.
 *
 * <p>Thread safety: single reader thread only (H2Connection reader thread).
 * After construction, this object must be the only reader of the transport
 * channel. DATA payloads may bypass {@link #buf} via {@link #readIntoDirect},
 * but any extra decrypted/plaintext bytes remain in the transport and are
 * drained by subsequent reads through this same reader. Response body channels
 * read from queued DATA buffers, not from the transport channel.
 */
final class ChannelFrameReader {

    private final ReadableByteChannel channel;
    private final BooleanSupplier transportHasBufferedData;
    private ByteBuffer buf;

    ChannelFrameReader(ReadableByteChannel channel, int bufferSize) {
        this(channel, bufferSize, () -> false);
    }

    ChannelFrameReader(ReadableByteChannel channel, int bufferSize, BooleanSupplier transportHasBufferedData) {
        this.channel = channel;
        this.transportHasBufferedData = transportHasBufferedData;
        this.buf = ByteBuffer.allocate(bufferSize);
        this.buf.flip(); // start empty in read mode
    }

    /**
     * Ensure at least {@code n} bytes are available in the buffer.
     * Reads from the channel if needed.
     *
     * @return true if n bytes are available, false on EOF
     */
    boolean ensure(int n) throws IOException {
        while (buf.remaining() < n) {
            if (buf.hasRemaining()) {
                buf.compact(); // switch to write mode, preserving unread data
            } else {
                buf.clear(); // no unread data to preserve
            }
            int read;
            try {
                read = channel.read(buf);
            } finally {
                buf.flip(); // always restore read mode, even if the read is interrupted
            }
            if (read < 0) {
                return buf.remaining() >= n;
            }
        }
        return true;
    }

    /**
     * Get the underlying buffer for direct parsing. Buffer is in read mode.
     */
    ByteBuffer buffer() {
        return buf;
    }

    /**
     * Number of bytes available without reading from channel.
     */
    int buffered() {
        return buf.remaining();
    }

    /**
     * Returns true if plaintext is buffered in this reader or in the transport below it.
     */
    boolean hasBufferedData() {
        return buf.hasRemaining() || transportHasBufferedData.getAsBoolean();
    }

    /**
     * Read a single byte.
     */
    int readByte() throws IOException {
        if (!ensure(1)) {
            throw new IOException("Unexpected EOF");
        }
        return buf.get() & 0xFF;
    }

    /**
     * Read payload into a byte[] (for control frames like SETTINGS, GOAWAY).
     */
    void readInto(byte[] dest, int offset, int length) throws IOException {
        while (length > 0) {
            if (!buf.hasRemaining()) {
                if (!ensure(1)) {
                    throw new IOException("Unexpected EOF reading payload");
                }
            }
            int toCopy = Math.min(buf.remaining(), length);
            buf.get(dest, offset, toCopy);
            offset += toCopy;
            length -= toCopy;
        }
    }

    /**
     * Read payload directly into a ByteBuffer (for DATA frames — zero copy path).
     * The destination buffer must be in write mode.
     *
     * <p>First drains any buffered data, then reads remaining directly from channel
     * into the destination, bypassing our internal buffer entirely.
     */
    void readIntoDirect(ByteBuffer dest, int length) throws IOException {
        // Drain buffered data first
        if (buf.hasRemaining()) {
            int toDrain = Math.min(buf.remaining(), length);
            int oldLimit = buf.limit();
            buf.limit(buf.position() + toDrain);
            dest.put(buf);
            buf.limit(oldLimit);
            length -= toDrain;
        }

        // Read remainder directly from channel into dest — no intermediate buffer
        while (length > 0) {
            int oldLimit = dest.limit();
            dest.limit(dest.position() + length);
            int n = channel.read(dest);
            dest.limit(oldLimit);
            if (n < 0) {
                throw new IOException("Unexpected EOF reading payload");
            }
            length -= n;
        }
    }

    /**
     * Skip bytes (for padding).
     */
    void skip(int length) throws IOException {
        while (length > 0) {
            if (!buf.hasRemaining()) {
                if (!ensure(1)) {
                    throw new IOException("Unexpected EOF skipping bytes");
                }
            }
            int toSkip = Math.min(buf.remaining(), length);
            buf.position(buf.position() + toSkip);
            length -= toSkip;
        }
    }
}
