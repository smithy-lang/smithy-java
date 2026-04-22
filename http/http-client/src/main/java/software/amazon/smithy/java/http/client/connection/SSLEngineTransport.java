/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * TLS transport using {@link SSLEngine} for zero-copy encryption/decryption.
 *
 * <p>Provides both stream-based and channel-based I/O. The channel API avoids
 * intermediate byte[] copies by operating directly on ByteBuffers through the
 * SSLEngine, achieving near-zero-copy TLS.
 *
 * <p>Thread safety: reads and writes can happen concurrently from different threads.
 * The SSLEngine is protected by a lock for unwrap/wrap, but socket I/O happens
 * outside the lock.
 */
final class SSLEngineTransport implements AutoCloseable {

    private final InputStream socketIn;
    private final OutputStream socketOut;
    private final SSLEngine engine;
    private final ReentrantLock engineLock = new ReentrantLock();
    private final Socket socket;
    private final SocketChannel socketChannel;

    // Network-side buffers (ciphertext). netIn is always in "write" mode (position = end of data).
    private ByteBuffer netIn;
    private ByteBuffer netOut;

    // Application-side read buffer (plaintext from unwrap). Always in "read" mode (position = next byte).
    private ByteBuffer appIn;

    private volatile boolean closed;
    private boolean eof;

    SSLEngineTransport(Socket socket, SSLEngine engine) throws IOException {
        this.socket = socket;
        this.socketIn = socket.getInputStream();
        this.socketOut = socket.getOutputStream();
        this.socketChannel = socket.getChannel();
        this.engine = engine;

        SSLSession session = engine.getSession();
        int packetSize = session.getPacketBufferSize();
        int appSize = session.getApplicationBufferSize();

        boolean direct = socketChannel != null;
        this.netIn = direct ? ByteBuffer.allocateDirect(packetSize) : ByteBuffer.allocate(packetSize);
        this.netOut = direct ? ByteBuffer.allocateDirect(packetSize) : ByteBuffer.allocate(packetSize);
        this.appIn = ByteBuffer.allocate(appSize);
        this.appIn.flip(); // start empty (read mode, nothing to read)
    }

    /**
     * Perform the TLS handshake. Must be called before any read/write.
     */
    void handshake() throws IOException {
        engine.beginHandshake();
        HandshakeStatus hs = engine.getHandshakeStatus();

        while (hs != HandshakeStatus.FINISHED && hs != HandshakeStatus.NOT_HANDSHAKING) {
            switch (hs) {
                case NEED_WRAP -> hs = handshakeWrap();
                case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> hs = handshakeUnwrap(hs);
                case NEED_TASK -> hs = runDelegatedTasks();
                default -> throw new SSLException("Unexpected handshake status: " + hs);
            }
        }
    }

    private HandshakeStatus handshakeWrap() throws IOException {
        ByteBuffer empty = ByteBuffer.allocate(0);
        netOut.clear();
        SSLEngineResult result = engine.wrap(empty, netOut);
        if (result.getStatus() == Status.BUFFER_OVERFLOW) {
            netOut = allocateNetBuffer(engine.getSession().getPacketBufferSize());
            return result.getHandshakeStatus();
        }
        if (result.getStatus() == Status.CLOSED) {
            throw new SSLException("Engine closed during handshake wrap");
        }
        netOut.flip();
        if (netOut.hasRemaining()) {
            writeNetOut();
            flushSocket();
        }
        return result.getHandshakeStatus();
    }

    private HandshakeStatus handshakeUnwrap(HandshakeStatus current) throws IOException {
        if (current == HandshakeStatus.NEED_UNWRAP && netIn.position() == 0) {
            if (!readIntoNetIn()) {
                throw new EOFException("Connection closed during handshake");
            }
        }

        while (true) {
            netIn.flip();
            appIn.clear();
            SSLEngineResult result;
            engineLock.lock();
            try {
                result = engine.unwrap(netIn, appIn);
            } finally {
                engineLock.unlock();
            }
            netIn.compact();
            appIn.flip();

            Status status = result.getStatus();
            if (status == Status.BUFFER_OVERFLOW) {
                appIn = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                appIn.flip();
                continue;
            }
            if (status == Status.BUFFER_UNDERFLOW) {
                netIn = ensureCapacity(netIn, engine.getSession().getPacketBufferSize());
                if (!readIntoNetIn()) {
                    throw new EOFException("Connection closed during handshake (BUFFER_UNDERFLOW)");
                }
                continue;
            }
            if (status == Status.CLOSED) {
                throw new SSLException("Engine closed during handshake unwrap");
            }
            return result.getHandshakeStatus();
        }
    }

    private HandshakeStatus runDelegatedTasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
        return engine.getHandshakeStatus();
    }

    /**
     * Read from socket directly into netIn's backing array. No intermediate copy.
     *
     * @return true if data was read, false on EOF
     */
    private boolean readIntoNetIn() throws IOException {
        int space = netIn.remaining();
        if (space == 0) {
            netIn = ensureCapacity(netIn, netIn.capacity() * 2);
        }
        int n;
        if (socketChannel != null) {
            n = socketChannel.read(netIn);
        } else {
            n = socketIn.read(netIn.array(), netIn.arrayOffset() + netIn.position(), netIn.remaining());
            if (n > 0) {
                netIn.position(netIn.position() + n);
            }
        }
        if (n <= 0) {
            eof = true;
            return false;
        }
        return true;
    }

    private void writeNetOut() throws IOException {
        if (!netOut.hasRemaining()) {
            return;
        }
        if (socketChannel != null) {
            while (netOut.hasRemaining()) {
                socketChannel.write(netOut);
            }
        } else {
            socketOut.write(netOut.array(), netOut.arrayOffset() + netOut.position(), netOut.remaining());
            netOut.position(netOut.limit());
        }
    }

    private void flushSocket() throws IOException {
        if (socketChannel == null) {
            socketOut.flush();
        }
    }

    private ByteBuffer allocateNetBuffer(int size) {
        return socketChannel != null ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }

    boolean isClosed() {
        return closed;
    }

    void setReadTimeout(int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
    }

    int getReadTimeout() throws IOException {
        return socket.getSoTimeout();
    }

    boolean hasBufferedData() {
        return appIn.hasRemaining();
    }

    SSLSession getSession() {
        return engine.getSession();
    }

    String getApplicationProtocol() {
        return engine.getApplicationProtocol();
    }

    // ==================== Stream-based I/O (InputStream/OutputStream) ====================

    /**
     * Read decrypted data into the given byte array.
     *
     * @return bytes read, or -1 on EOF
     */
    int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            return -1;
        }
        if (len == 0) {
            return 0;
        }

        // Fast path: data already decrypted in appIn
        if (appIn.hasRemaining()) {
            int toCopy = Math.min(appIn.remaining(), len);
            appIn.get(b, off, toCopy);
            return toCopy;
        }

        return readAndUnwrap(b, off, len);
    }

    private int readAndUnwrap(byte[] b, int off, int len) throws IOException {
        while (true) {
            if (eof && netIn.position() == 0) {
                return -1;
            }

            if (netIn.position() == 0) {
                if (!readIntoNetIn()) {
                    return -1;
                }
            }

            netIn.flip();
            appIn.clear();
            SSLEngineResult result;
            engineLock.lock();
            try {
                result = engine.unwrap(netIn, appIn);
            } finally {
                engineLock.unlock();
            }
            netIn.compact();
            appIn.flip();

            switch (result.getStatus()) {
                case OK -> {
                    handlePostResult(result);
                    if (appIn.hasRemaining()) {
                        int toCopy = Math.min(appIn.remaining(), len);
                        appIn.get(b, off, toCopy);
                        return toCopy;
                    }
                }
                case BUFFER_UNDERFLOW -> {
                    netIn = ensureCapacity(netIn, engine.getSession().getPacketBufferSize());
                    if (!readIntoNetIn()) {
                        if (netIn.position() == 0) {
                            return -1;
                        }
                        throw new EOFException("Connection closed with partial TLS record");
                    }
                }
                case BUFFER_OVERFLOW -> {
                    appIn = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                    appIn.flip();
                }
                case CLOSED -> {
                    return -1;
                }
            }
        }
    }

    // ==================== Channel-based I/O (zero-copy ByteBuffer path) ====================

    /**
     * Read decrypted data directly into a ByteBuffer. This is the zero-copy read path.
     *
     * <p>Unwraps TLS data directly into the destination buffer when possible,
     * avoiding the intermediate appIn buffer entirely. Falls back to appIn for
     * cases where the destination buffer is too small for SSLEngine.
     *
     * @param dst destination buffer
     * @return bytes read, or -1 on EOF
     */
    int readChannel(ByteBuffer dst) throws IOException {
        if (closed) {
            return -1;
        }
        if (!dst.hasRemaining()) {
            return 0;
        }

        // Fast path: drain any leftover plaintext from appIn
        if (appIn.hasRemaining()) {
            return drainAppIn(dst);
        }

        return readAndUnwrapChannel(dst);
    }

    private int readAndUnwrapChannel(ByteBuffer dst) throws IOException {
        while (true) {
            if (eof && netIn.position() == 0) {
                return -1;
            }

            if (netIn.position() == 0) {
                if (!readIntoNetIn()) {
                    return -1;
                }
            }

            netIn.flip();

            // Try to unwrap directly into dst if it's large enough for SSLEngine
            int appBufSize = engine.getSession().getApplicationBufferSize();
            boolean directUnwrap = dst.remaining() >= appBufSize;

            SSLEngineResult result;
            if (directUnwrap) {
                // Zero-copy path: unwrap directly into caller's buffer
                engineLock.lock();
                try {
                    result = engine.unwrap(netIn, dst);
                } finally {
                    engineLock.unlock();
                }
                netIn.compact();

                switch (result.getStatus()) {
                    case OK -> {
                        handlePostResult(result);
                        if (result.bytesProduced() > 0) {
                            return result.bytesProduced();
                        }
                        // No data produced (e.g., post-handshake message), loop
                    }
                    case BUFFER_UNDERFLOW -> {
                        netIn = ensureCapacity(netIn, engine.getSession().getPacketBufferSize());
                        if (!readIntoNetIn()) {
                            if (netIn.position() == 0) {
                                return -1;
                            }
                            throw new EOFException("Connection closed with partial TLS record");
                        }
                    }
                    case BUFFER_OVERFLOW -> {
                        // dst too small despite our check — fall through to appIn path
                        directUnwrap = false;
                    }
                    case CLOSED -> {
                        return -1;
                    }
                }
                if (directUnwrap) {
                    continue;
                }
                // Fall through to appIn path on BUFFER_OVERFLOW
                netIn.flip(); // re-flip for the appIn path below
            }

            // Fallback: unwrap into appIn, then copy to dst
            appIn.clear();
            engineLock.lock();
            try {
                result = engine.unwrap(netIn, appIn);
            } finally {
                engineLock.unlock();
            }
            netIn.compact();
            appIn.flip();

            switch (result.getStatus()) {
                case OK -> {
                    handlePostResult(result);
                    if (appIn.hasRemaining()) {
                        return drainAppIn(dst);
                    }
                }
                case BUFFER_UNDERFLOW -> {
                    netIn = ensureCapacity(netIn, engine.getSession().getPacketBufferSize());
                    if (!readIntoNetIn()) {
                        if (netIn.position() == 0) {
                            return -1;
                        }
                        throw new EOFException("Connection closed with partial TLS record");
                    }
                }
                case BUFFER_OVERFLOW -> {
                    appIn = ByteBuffer.allocate(appBufSize);
                    appIn.flip();
                }
                case CLOSED -> {
                    return -1;
                }
            }
        }
    }

    private int drainAppIn(ByteBuffer dst) {
        int toCopy = Math.min(appIn.remaining(), dst.remaining());
        int oldLimit = appIn.limit();
        appIn.limit(appIn.position() + toCopy);
        dst.put(appIn);
        appIn.limit(oldLimit);
        return toCopy;
    }

    /**
     * Encrypt and write data from the given ByteBuffer. Zero-copy write path.
     *
     * @param src source buffer with plaintext data
     * @return bytes consumed from src
     */
    int writeChannel(ByteBuffer src) throws IOException {
        if (closed) {
            throw new IOException("Transport closed");
        }
        int totalConsumed = 0;
        while (src.hasRemaining()) {
            netOut.clear();
            SSLEngineResult result;
            engineLock.lock();
            try {
                result = engine.wrap(src, netOut);
            } finally {
                engineLock.unlock();
            }
            totalConsumed += result.bytesConsumed();

            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                netOut = allocateNetBuffer(engine.getSession().getPacketBufferSize());
                continue;
            }
            if (result.getStatus() == Status.CLOSED) {
                throw new IOException("SSLEngine closed during write");
            }

            netOut.flip();
            if (netOut.hasRemaining()) {
                writeNetOut();
            }
            handlePostResult(result);
        }
        return totalConsumed;
    }

    // ==================== Stream-based write ====================

    void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Transport closed");
        }
        ByteBuffer src = ByteBuffer.wrap(b, off, len);
        while (src.hasRemaining()) {
            netOut.clear();
            SSLEngineResult result;
            engineLock.lock();
            try {
                result = engine.wrap(src, netOut);
            } finally {
                engineLock.unlock();
            }

            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                netOut = allocateNetBuffer(engine.getSession().getPacketBufferSize());
                continue;
            }
            if (result.getStatus() == Status.CLOSED) {
                throw new IOException("SSLEngine closed during write");
            }

            netOut.flip();
            if (netOut.hasRemaining()) {
                writeNetOut();
            }
            handlePostResult(result);
        }
    }

    void flush() throws IOException {
        flushSocket();
    }

    private void handlePostResult(SSLEngineResult result) {
        HandshakeStatus hs = result.getHandshakeStatus();
        if (hs == HandshakeStatus.NEED_TASK) {
            Runnable task;
            while ((task = engine.getDelegatedTask()) != null) {
                task.run();
            }
        }
    }

    // ==================== Channel adapters ====================

    ReadableByteChannel readableChannel() {
        return new ReadableByteChannel() {
            @Override
            public int read(ByteBuffer dst) throws IOException {
                return readChannel(dst);
            }

            @Override
            public boolean isOpen() {
                return !closed;
            }

            @Override
            public void close() throws IOException {
                SSLEngineTransport.this.close();
            }
        };
    }

    WritableByteChannel writableChannel() {
        return new WritableByteChannel() {
            @Override
            public int write(ByteBuffer src) throws IOException {
                return writeChannel(src);
            }

            @Override
            public boolean isOpen() {
                return !closed;
            }

            @Override
            public void close() throws IOException {
                SSLEngineTransport.this.close();
            }
        };
    }

    // ==================== Stream adapters ====================

    InputStream inputStream() {
        return new TransportInputStream();
    }

    OutputStream outputStream() {
        return new TransportOutputStream();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            engineLock.lock();
            try {
                engine.closeOutbound();
                netOut.clear();
                engine.wrap(ByteBuffer.allocate(0), netOut);
                netOut.flip();
                if (netOut.hasRemaining()) {
                    writeNetOut();
                    flushSocket();
                }
            } finally {
                engineLock.unlock();
            }
        } catch (IOException ignored) {
            // Best-effort close_notify
        } finally {
            socket.close();
        }
    }

    private static ByteBuffer ensureCapacity(ByteBuffer buf, int minCapacity) {
        if (buf.capacity() >= minCapacity) {
            return buf;
        }
        ByteBuffer newBuf = buf.isDirect()
                ? ByteBuffer.allocateDirect(minCapacity)
                : ByteBuffer.allocate(minCapacity);
        buf.flip();
        newBuf.put(buf);
        return newBuf;
    }

    private final class TransportInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = SSLEngineTransport.this.read(b, 0, 1);
            return n < 0 ? -1 : b[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return SSLEngineTransport.this.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            SSLEngineTransport.this.close();
        }
    }

    private final class TransportOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            SSLEngineTransport.this.write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            SSLEngineTransport.this.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            SSLEngineTransport.this.flush();
        }

        @Override
        public void close() throws IOException {
            SSLEngineTransport.this.close();
        }
    }
}
