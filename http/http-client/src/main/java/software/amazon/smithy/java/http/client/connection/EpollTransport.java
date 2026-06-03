/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.channel.epoll.EpollAccess;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import javax.net.ssl.SSLSession;

final class EpollTransport implements ConnectionTransport {

    private static final int SCRATCH_SIZE = 16 * 1024;

    private final EpollChannel channel;
    private final EpollReadableChannel readableChannel = new EpollReadableChannel();
    private final EpollWritableChannel writableChannel = new EpollWritableChannel();
    private final InputStream inputStream = Channels.newInputStream(readableChannel);
    private final OutputStream outputStream = Channels.newOutputStream(writableChannel);
    private volatile int readTimeoutMs;

    EpollTransport(EpollChannel channel, int readTimeoutMs) {
        this.channel = channel;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public InputStream inputStream() {
        return inputStream;
    }

    @Override
    public OutputStream outputStream() {
        return outputStream;
    }

    @Override
    public ReadableByteChannel readableChannel() {
        return readableChannel;
    }

    @Override
    public WritableByteChannel writableChannel() {
        return writableChannel;
    }

    @Override
    public SSLSession sslSession() {
        return null;
    }

    @Override
    public String negotiatedProtocol() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void setReadTimeout(int timeoutMs) {
        readTimeoutMs = timeoutMs;
    }

    @Override
    public int getReadTimeout() {
        return readTimeoutMs;
    }

    @Override
    public void close() {
        channel.close();
    }

    private final class EpollReadableChannel implements ReadableByteChannel {
        private ByteBuffer scratch;

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (!isOpen()) {
                return -1;
            }
            if (!dst.hasRemaining()) {
                return 0;
            }
            if (dst.isDirect()) {
                int pos = dst.position();
                int n = channel.readAddress(EpollAccess.memoryAddress(dst), pos, dst.limit(), readTimeoutMs);
                if (n > 0) {
                    dst.position(pos + n);
                }
                return n;
            }

            int want = dst.remaining();
            ByteBuffer direct = scratchBuffer(want);
            // Cap the read at the destination's remaining bytes so a partially-reused scratch buffer
            // (sized from an earlier larger read) can't overflow dst.
            int cap = Math.min(want, direct.capacity());
            int n = channel.readAddress(EpollAccess.memoryAddress(direct), 0, cap, readTimeoutMs);
            if (n > 0) {
                direct.limit(n);
                dst.put(direct);
            }
            return n;
        }

        @Override
        public boolean isOpen() {
            return EpollTransport.this.isOpen();
        }

        @Override
        public void close() {
            EpollTransport.this.close();
        }

        private ByteBuffer scratchBuffer(int remaining) {
            int size = Math.min(remaining, SCRATCH_SIZE);
            ByteBuffer result = scratch;
            if (result == null || result.capacity() < size) {
                result = ByteBuffer.allocateDirect(size);
                scratch = result;
            }
            result.clear();
            return result;
        }
    }

    private final class EpollWritableChannel implements GatheringByteChannel {
        private ByteBuffer scratch;

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (!isOpen()) {
                throw new IOException("channel closed");
            }
            if (!src.hasRemaining()) {
                return 0;
            }

            int len = src.remaining();
            if (src.isDirect()) {
                int pos = src.position();
                channel.writeAddress(EpollAccess.memoryAddress(src), pos, src.limit());
                src.position(src.limit());
                return len;
            }

            ByteBuffer direct = scratchBuffer(len);
            int n = Math.min(len, direct.capacity());
            int limit = src.limit();
            src.limit(src.position() + n);
            direct.put(src);
            src.limit(limit);
            direct.flip();
            channel.writeAddress(EpollAccess.memoryAddress(direct), 0, n);
            return n;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            if (!isOpen()) {
                throw new IOException("channel closed");
            }
            return channel.writev(srcs, offset, length);
        }

        @Override
        public long write(ByteBuffer[] srcs) throws IOException {
            return write(srcs, 0, srcs.length);
        }

        @Override
        public boolean isOpen() {
            return EpollTransport.this.isOpen();
        }

        @Override
        public void close() {
            EpollTransport.this.close();
        }

        private ByteBuffer scratchBuffer(int remaining) {
            int size = Math.min(remaining, SCRATCH_SIZE);
            ByteBuffer result = scratch;
            if (result == null || result.capacity() < size) {
                result = ByteBuffer.allocateDirect(size);
                scratch = result;
            }
            result.clear();
            return result;
        }
    }
}
