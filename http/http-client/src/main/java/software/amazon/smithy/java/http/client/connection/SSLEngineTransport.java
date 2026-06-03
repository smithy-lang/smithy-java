/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.channel.epoll.EpollAccess;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * TLS transport using {@link SSLEngine} for ByteBuffer-based encryption/decryption.
 *
 * <p>Provides both stream-based and channel-based I/O. The channel API avoids
 * intermediate byte[] copies by operating directly on ByteBuffers through the
 * SSLEngine, avoiding some intermediate byte[] copies.
 *
 * <p>Thread safety: reads and writes can happen concurrently from different threads.
 * The SSLEngine is protected by a lock for unwrap/wrap, but socket I/O happens
 * outside the lock.
 */
final class SSLEngineTransport implements ConnectionTransport {

    private final InputStream socketIn;
    private final OutputStream socketOut;
    private final SSLEngine engine;
    // Frees any provider-native engine resources (a no-op for the JDK engine, a ref-count release for
    // a native engine such as BoringSSL/tcnative). Invoked exactly once on close, AFTER the socket is
    // closed, on every close path including errors.
    private final Runnable engineReleaser;
    private final ReentrantLock engineLock = new ReentrantLock();
    private final Socket socket;
    private final SocketChannel socketChannel;
    // Experimental persistent-registration epoll socket backend. Non-null only when the epoll
    // transport is enabled (Linux + Epoll.isAvailable()); when set, socket/socketIn/socketOut/
    // socketChannel/readTimer are all null and the byte-level read/write/flush/timeout/close routes
    // through this channel instead of the JDK NIO SocketChannel. The TLS wrap/unwrap and all buffer
    // management above this seam are identical on both backends.
    private final EpollChannel epollChannel;
    // Read deadline (ms) for the epoll path, mirroring SO_TIMEOUT on the NIO path. Mutated by
    // setReadTimeout; 0 means no deadline. Unused on the NIO path (which uses socket.setSoTimeout).
    private int epollReadTimeoutMs;
    // Shared watchdog enforcing the read deadline on the blocking-channel path. Null => fall back to
    // an untimed blocking read (deadline still bounded by the request-level timeout above the stack).
    private final Timer readTimer;
    private final ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
    private final byte[] singleByteRead = new byte[1];
    private final byte[] singleByteWrite = new byte[1];

    // Network-side buffers (ciphertext). netIn is always in "write" mode (position = end of data).
    private ByteBuffer netIn;
    private ByteBuffer netOut;

    // Application-side read buffer (plaintext from unwrap). Always in "read" mode (position = next byte).
    private ByteBuffer appIn;
    private final boolean appBufferDirect;

    private volatile boolean closed;
    private boolean eof;

    private static final int DEFAULT_BUFFER_SIZE = 16 * 1024;

    SSLEngineTransport(Socket socket, SSLEngine engine) throws IOException {
        this(socket, engine, () -> {}, null, DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);
    }

    SSLEngineTransport(Socket socket, SSLEngine engine, Runnable engineReleaser) throws IOException {
        this(socket, engine, engineReleaser, null, DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);
    }

    SSLEngineTransport(Socket socket, SSLEngine engine, Runnable engineReleaser, Timer readTimer)
            throws IOException {
        this(socket, engine, engineReleaser, readTimer, DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);
    }

    /**
     * @param readBufferSize target capacity (bytes) for the ciphertext-read ({@code netIn}) and
     *     plaintext-unwrap ({@code appIn}) buffers. Sized up to at least one TLS record. A larger
     *     value lets one {@code socketChannel.read} pull many records that {@link #readAndUnwrap}
     *     then drains in a single locked pass (one {@code compact} per socket read, not per record),
     *     collapsing read syscalls, watchdog arms, epoll registrations, and VT park/unpark cycles
     *     proportionally to the records-per-read ratio.
     * @param writeBufferSize target capacity (bytes) for the ciphertext-write ({@code netOut})
     *     buffer. Sized up to at least one TLS record. A larger value lets {@link #write} accumulate
     *     several wrapped records before one {@code writeNetOut}, collapsing write syscalls (and the
     *     attendant VT park/unpark) for bulk uploads.
     */
    SSLEngineTransport(
            Socket socket,
            SSLEngine engine,
            Runnable engineReleaser,
            Timer readTimer,
            int readBufferSize,
            int writeBufferSize
    )
            throws IOException {
        this.socket = socket;
        this.socketIn = socket.getInputStream();
        this.socketOut = socket.getOutputStream();
        this.socketChannel = socket.getChannel();
        this.epollChannel = null;
        this.engine = engine;
        this.engineReleaser = engineReleaser != null ? engineReleaser : () -> {};
        this.readTimer = readTimer;

        // Direct buffers when we own a SocketChannel so a native engine works straight off-heap.
        boolean direct = socketChannel != null;
        this.appBufferDirect = direct;
        allocateBuffers(direct, readBufferSize, writeBufferSize);
    }

    /**
     * Construct a transport whose ciphertext I/O is backed by the experimental persistent-registration
     * {@link EpollChannel} instead of a JDK {@link SocketChannel}. The TLS state machine, buffer
     * management, and every method above the byte-level socket seam are identical to the NIO path; only
     * {@code readIntoNetIn}/{@code writeNetOut}/{@code flushSocket}/{@code setReadTimeout}/{@code close}
     * route through the epoll channel. Buffers are always direct (the raw-address recv/send path
     * requires it).
     *
     * @param epollChannel a connected epoll channel (TLS not yet started)
     * @param engine the SSL engine driving TLS
     * @param engineReleaser native-engine release callback, invoked once on close
     * @param readTimeoutMs initial read deadline in milliseconds (0 = none); mirrors SO_TIMEOUT
     * @param readBufferSize ciphertext-read / plaintext-unwrap buffer target capacity
     * @param writeBufferSize ciphertext-write buffer target capacity
     */
    SSLEngineTransport(
            EpollChannel epollChannel,
            SSLEngine engine,
            Runnable engineReleaser,
            int readTimeoutMs,
            int readBufferSize,
            int writeBufferSize
    )
            throws IOException {
        this.socket = null;
        this.socketIn = null;
        this.socketOut = null;
        this.socketChannel = null;
        this.epollChannel = epollChannel;
        this.epollReadTimeoutMs = readTimeoutMs;
        this.engine = engine;
        this.engineReleaser = engineReleaser != null ? engineReleaser : () -> {};
        this.readTimer = null;

        // The raw-address recv/send path operates on the buffer's native memory address, so buffers
        // must be direct.
        this.appBufferDirect = true;
        allocateBuffers(true, readBufferSize, writeBufferSize);
    }

    // Allocate netIn/netOut/appIn sized to at least one TLS record. netIn holds buffered ciphertext;
    // appIn holds the plaintext drained from it. Per TLS record plaintext < ciphertext (record framing
    // + AEAD tag overhead), so sizing appIn >= netIn guarantees one drain pass empties every whole
    // record netIn can hold without an appIn overflow mid-pass — keeping the trailing compact to at
    // most one partial record. netOut must hold at least one whole packet; larger lets write() coalesce
    // several records.
    private void allocateBuffers(boolean direct, int readBufferSize, int writeBufferSize) {
        SSLSession session = engine.getSession();
        int packetSize = session.getPacketBufferSize();
        int appSize = session.getApplicationBufferSize();

        int netCap = Math.max(readBufferSize, packetSize);
        int appCap = Math.max(readBufferSize, appSize);
        int netOutCap = Math.max(writeBufferSize, packetSize);

        this.netIn = direct ? ByteBuffer.allocateDirect(netCap) : ByteBuffer.allocate(netCap);
        this.netOut = direct ? ByteBuffer.allocateDirect(netOutCap) : ByteBuffer.allocate(netOutCap);
        this.appIn = allocateAppBuffer(appCap);
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
        netOut.clear();
        emptyBuffer.clear();
        SSLEngineResult result = engine.wrap(emptyBuffer, netOut);
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
                appIn = allocateAppBuffer(engine.getSession().getApplicationBufferSize());
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
        if (epollChannel != null) {
            // Raw-address read straight into netIn's off-heap region: recvAddress on the buffer's
            // native memory address advances nothing itself, so we bump netIn's position by the
            // count. The address is recomputed per call because netIn may have been reallocated by
            // ensureCapacity (the recompute is a cheap Unsafe field read — still cheaper than the
            // per-op GetDirectBufferAddress the ByteBuffer overload would pay). The read deadline
            // mirrors SO_TIMEOUT on the NIO path.
            long base = EpollAccess.memoryAddress(netIn);
            int pos = netIn.position();
            int limit = netIn.limit();
            n = epollChannel.readAddress(base, pos, limit, epollReadTimeoutMs);
            if (n > 0) {
                netIn.position(pos + n);
            }
        } else if (socketChannel != null) {
            int timeoutMs = socket.getSoTimeout();
            if (timeoutMs > 0 && readTimer != null) {
                n = readWithTimeout(timeoutMs);
            } else {
                // No deadline (or no shared timer): a plain blocking channel read parks the calling
                // virtual thread cleanly. The request-level timeout above the stack still bounds it.
                n = socketChannel.read(netIn);
            }
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

    /**
     * Blocking-channel read with a watchdog-enforced deadline. A blocking {@link SocketChannel#read}
     * ignores {@code SO_TIMEOUT}, so instead of opening an epoll {@code Selector} per read (which cost
     * an {@code epoll_create1}/{@code eventfd}/{@code close} cycle and a blocking-mode flip every call)
     * the read parks the virtual thread and a single shared {@link #readTimer} closes the channel if
     * the deadline passes — waking the parked read with an {@code AsynchronousCloseException} that is
     * surfaced as a {@link SocketTimeoutException}. The channel stays in blocking mode throughout.
     */
    private int readWithTimeout(int timeoutMs) throws IOException {
        Timeout watchdog = readTimer.newTimeout(t -> closeChannelQuietly(), timeoutMs, TimeUnit.MILLISECONDS);
        try {
            return socketChannel.read(netIn);
        } catch (ClosedChannelException e) {
            // The watchdog (or a concurrent close) closed the channel out from under the read
            // (AsynchronousCloseException is the watchdog case; both extend ClosedChannelException).
            if (watchdog.isExpired()) {
                throw new SocketTimeoutException("Read timed out after " + timeoutMs + "ms");
            }
            throw e;
        } finally {
            watchdog.cancel();
        }
    }

    private void closeChannelQuietly() {
        try {
            socketChannel.close();
        } catch (IOException ignored) {
            // best effort — the parked read will unblock with AsynchronousCloseException
        }
    }

    private void writeNetOut() throws IOException {
        if (!netOut.hasRemaining()) {
            return;
        }
        if (epollChannel != null) {
            // writeAddress drains the whole [pos, limit) region (looping internally on partial
            // sends / back-pressure), so advance netOut to its limit in one step afterward.
            int pos = netOut.position();
            int limit = netOut.limit();
            epollChannel.writeAddress(EpollAccess.memoryAddress(netOut), pos, limit);
            netOut.position(limit);
        } else if (socketChannel != null) {
            while (netOut.hasRemaining()) {
                socketChannel.write(netOut);
            }
        } else {
            socketOut.write(netOut.array(), netOut.arrayOffset() + netOut.position(), netOut.remaining());
            netOut.position(netOut.limit());
        }
    }

    private void flushSocket() throws IOException {
        // Only the stream (non-channel) backend buffers writes; both the NIO SocketChannel and the
        // epoll channel write straight through, so flush is a no-op there.
        if (socketChannel == null && epollChannel == null) {
            socketOut.flush();
        }
    }

    private ByteBuffer allocateNetBuffer(int size) {
        return appBufferDirect ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }

    // Allocate the plaintext unwrap-destination buffer, direct when we own a SocketChannel so a native
    // engine decrypts straight into it (no temp-direct staging copy). Every appIn (re)allocation must
    // route through here, or a BUFFER_OVERFLOW resize would silently revert appIn to heap.
    private ByteBuffer allocateAppBuffer(int size) {
        return appBufferDirect ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }

    @Override
    public void setReadTimeout(int timeoutMs) throws IOException {
        if (epollChannel != null) {
            // Mirrors SO_TIMEOUT: the next readIntoNetIn parks with this deadline (0 == infinite).
            this.epollReadTimeoutMs = timeoutMs;
        } else {
            socket.setSoTimeout(timeoutMs);
        }
    }

    @Override
    public int getReadTimeout() throws IOException {
        return epollChannel != null ? epollReadTimeoutMs : socket.getSoTimeout();
    }

    @Override
    public boolean hasBufferedData() {
        return appIn.hasRemaining();
    }

    @Override
    public SSLSession sslSession() {
        return engine.getSession();
    }

    @Override
    public String negotiatedProtocol() {
        String proto = engine.getApplicationProtocol();
        return (proto != null && !proto.isEmpty()) ? proto : null;
    }

    @Override
    public boolean isOpen() {
        return !closed;
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
            Status status;
            while (true) {
                SSLEngineResult result;
                engineLock.lock();
                try {
                    result = engine.unwrap(netIn, appIn);
                } finally {
                    engineLock.unlock();
                }
                status = result.getStatus();
                if (status == Status.OK) {
                    handlePostResult(result);
                    // No forward progress (defensive against a pathological 0/0 OK) or netIn drained
                    // of whole records — stop the drain and serve what we have.
                    if ((result.bytesConsumed() == 0 && result.bytesProduced() == 0) || !netIn.hasRemaining()) {
                        break;
                    }
                    // Another whole record may be buffered; keep draining into appIn.
                    continue;
                }
                // UNDERFLOW (partial trailing record), OVERFLOW (appIn full), or CLOSED.
                break;
            }
            netIn.compact();
            appIn.flip();

            // Serve whatever plaintext we drained this pass.
            if (appIn.hasRemaining()) {
                int toCopy = Math.min(appIn.remaining(), len);
                appIn.get(b, off, toCopy);
                return toCopy;
            }

            // No plaintext produced — act on why the drain stopped.
            switch (status) {
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
                    appIn = allocateAppBuffer(engine.getSession().getApplicationBufferSize());
                    appIn.flip();
                }
                case CLOSED -> {
                    return -1;
                }
                default -> {
                    // OK but produced 0 bytes (e.g. a post-handshake message consumed no app data);
                    // loop to read/unwrap again.
                }
            }
        }
    }

    // ==================== Channel-based I/O (ByteBuffer path) ====================

    /**
     * Read decrypted data directly into a ByteBuffer.
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
        } else if (!dst.hasRemaining()) {
            return 0;
        } else if (appIn.hasRemaining()) {
            // Fast path: drain any leftover plaintext from appIn
            return drainAppIn(dst);
        }

        return readAndUnwrapChannel(dst);
    }

    private int readAndUnwrapChannel(ByteBuffer dst) throws IOException {
        while (true) {
            if (eof && netIn.position() == 0) {
                return -1;
            } else if (netIn.position() == 0 && !readIntoNetIn()) {
                return -1;
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
                    appIn = allocateAppBuffer(appBufSize);
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
        int packetSize = engine.getSession().getPacketBufferSize();
        netOut.clear();
        while (src.hasRemaining()) {
            SSLEngineResult result;
            engineLock.lock();
            try {
                result = engine.wrap(src, netOut);
            } finally {
                engineLock.unlock();
            }

            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                if (netOut.position() > 0) {
                    flushAccumulatedNetOut();
                } else {
                    netOut = allocateNetBuffer(packetSize);
                }
                continue;
            }
            if (result.getStatus() == Status.CLOSED) {
                throw new IOException("SSLEngine closed during write");
            }

            handlePostResult(result);

            if (netOut.remaining() < packetSize || !src.hasRemaining()) {
                flushAccumulatedNetOut();
            }
        }
        // Flush any trailing accumulated records.
        if (netOut.position() > 0) {
            flushAccumulatedNetOut();
        }
    }

    // Flip the accumulated ciphertext in netOut, write it all to the socket, then reset to fill mode.
    private void flushAccumulatedNetOut() throws IOException {
        netOut.flip();
        if (netOut.hasRemaining()) {
            writeNetOut();
        }
        netOut.clear();
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

    @Override
    public ReadableByteChannel readableChannel() {
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

    @Override
    public WritableByteChannel writableChannel() {
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

    @Override
    public InputStream inputStream() {
        return new TransportInputStream();
    }

    @Override
    public OutputStream outputStream() {
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
                emptyBuffer.clear();
                engine.wrap(emptyBuffer, netOut);
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
            try {
                if (epollChannel != null) {
                    epollChannel.close();
                } else {
                    socket.close();
                }
            } finally {
                // Release provider-native engine resources last, on every close path. For a
                // reference-counted native engine (BoringSSL/tcnative) this frees off-heap memory;
                // for the JDK engine it is a no-op. Must run even if close_notify or socket.close()
                // threw, or a native engine leaks per connection.
                engineReleaser.run();
            }
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
            int n = SSLEngineTransport.this.read(singleByteRead, 0, 1);
            return n < 0 ? -1 : singleByteRead[0] & 0xFF;
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
            singleByteWrite[0] = (byte) b;
            SSLEngineTransport.this.write(singleByteWrite, 0, 1);
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
