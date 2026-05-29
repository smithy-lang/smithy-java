/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

final class ChannelDataStream implements DataStream {

    private static final int BUFFER_SIZE = 16 * 1024;

    private final ReadableByteChannel channel;
    private final String contentType;
    private final long contentLength;
    private boolean consumed;
    private boolean closed;
    private ByteBuffer buffer;

    ChannelDataStream(ReadableByteChannel channel, String contentType, long contentLength) {
        this.channel = channel;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public InputStream asInputStream() {
        return Channels.newInputStream(asChannel());
    }

    @Override
    public ReadableByteChannel asChannel() {
        markConsumed();
        return channel;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        try (ReadableByteChannel src = asChannel()) {
            ByteBuffer copyBuffer = copyBuffer();
            byte[] bytes = copyBuffer.array();
            while (src.read(copyBuffer) >= 0) {
                out.write(bytes, 0, copyBuffer.position());
                copyBuffer.clear();
            }
        }
    }

    @Override
    public void writeTo(WritableByteChannel dst) throws IOException {
        try (ReadableByteChannel src = asChannel()) {
            copy(src, dst);
        }
    }

    @Override
    public void discard() throws IOException {
        if (consumed || closed) {
            return;
        }

        consumed = true;
        closed = true;
        try (var src = channel) {
            drain(src, contentLength, copyBuffer());
        }
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public boolean isReplayable() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return !consumed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                channel.close();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("Failed to close data stream", e);
            }
        }
    }

    private void markConsumed() {
        if (consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }
        consumed = true;
    }

    private ByteBuffer copyBuffer() {
        if (buffer == null) {
            buffer = ByteBuffer.allocate(BUFFER_SIZE);
        } else {
            buffer.clear();
        }
        return buffer;
    }

    private void copy(ReadableByteChannel src, WritableByteChannel dst) throws IOException {
        if (src instanceof FileChannel fileChannel) {
            long position = fileChannel.position();
            long size = fileChannel.size();
            while (position < size) {
                long transferred = fileChannel.transferTo(position, size - position, dst);
                if (transferred <= 0) {
                    fileChannel.position(position);
                    copyBuffered(fileChannel, dst, copyBuffer());
                    return;
                }
                position += transferred;
            }
            return;
        }

        copyBuffered(src, dst, copyBuffer());
    }

    private static void copyBuffered(ReadableByteChannel src, WritableByteChannel dst, ByteBuffer buffer)
            throws IOException {
        while (src.read(buffer) >= 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                dst.write(buffer);
            }
            buffer.clear();
        }
    }

    private static void drain(ReadableByteChannel src, long contentLength, ByteBuffer buffer) throws IOException {
        if (contentLength < 0) {
            while (src.read(buffer) >= 0) {
                buffer.clear();
            }
            return;
        }

        long remaining = contentLength;
        while (remaining > 0) {
            buffer.clear();
            if (remaining < buffer.capacity()) {
                buffer.limit((int) remaining);
            }
            int read = src.read(buffer);
            if (read < 0) {
                break;
            }
            remaining -= read;
        }
    }
}
