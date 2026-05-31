/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A single HTTP/1.1 connection that performs blocking socket I/O on the calling (virtual) thread,
 * using a Netty {@link EmbeddedChannel} as a pure protocol engine (TLS + HTTP codec) with no event
 * loop.
 *
 * <h2>Why this shape</h2>
 * The transport exposes only a synchronous API and expects callers to use virtual threads. An
 * event-loop model therefore pays for a second thread pool and a carrier&lt;-&gt;event-loop handoff
 * on every request for nothing. Here the calling VT does the blocking {@code read}/{@code write}
 * itself and merely pumps bytes through Netty's codecs synchronously:
 * <ul>
 *   <li>Outbound: write an {@code HttpObject}/{@code ByteBuf} to the channel, drain the resulting
 *       (encrypted) bytes from {@link EmbeddedChannel#outboundMessages()}, and write them to the
 *       socket.</li>
 *   <li>Inbound: read ciphertext from the socket, feed it via {@link EmbeddedChannel#writeInbound},
 *       and drain decoded {@code HttpObject}s from {@link EmbeddedChannel#inboundMessages()}.</li>
 * </ul>
 *
 * <p>{@code EmbeddedChannel} runs the whole pipeline inline on the calling thread, so this needs no
 * synchronization beyond the connection being used by one thread at a time (the H1 contract).
 *
 * <h2>Buffer ownership</h2>
 * {@code readInbound()}/{@code readOutbound()} transfer ref-count ownership to the caller, so every
 * drained {@link ByteBuf} (or {@code HttpContent}) is released once consumed.
 */
public final class VtH1Connection implements AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(VtH1Connection.class);

    // Size of the chunk read from the socket per syscall when pumping ciphertext inbound. Larger =
    // fewer read syscalls and fewer VT park/unpark cycles per response (each blocking read that has
    // to wait is a park+unpark). 256 KiB drains a full benchmark response in ~1-2 reads vs ~8 at 32K.
    private static final int SOCKET_READ_CHUNK = 256 * 1024;

    private final Socket socket;
    private final InputStream socketIn;
    private final SocketChannel socketChannel;
    private final EmbeddedChannel channel;
    private final boolean tls;
    private final boolean openSsl;
    private final Route route;
    private final Timer readTimer;
    private final int readTimeoutMs;

    private final byte[] readBuffer = new byte[SOCKET_READ_CHUNK];
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Connection liveness/keep-alive bookkeeping.
    private boolean keepAlive = true;
    private boolean fromReuse;
    private long lastUsedNanos;

    private VtH1Connection(
            Socket socket,
            EmbeddedChannel channel,
            boolean tls,
            boolean openSsl,
            Route route,
            Timer readTimer,
            int readTimeoutMs
    ) throws IOException {
        this.socket = socket;
        this.socketIn = socket.getInputStream();
        this.socketChannel = socket.getChannel();
        this.channel = channel;
        this.tls = tls;
        this.openSsl = openSsl;
        this.route = route;
        this.readTimer = readTimer;
        this.readTimeoutMs = readTimeoutMs;
        this.lastUsedNanos = System.nanoTime();
    }

    /**
     * Open a new connection to the route, performing the TLS handshake if needed.
     *
     * @param route target route
     * @param tlsContext TLS context (null for cleartext)
     * @param connectTimeoutMs TCP connect timeout
     * @param readTimeoutMs socket read timeout (also bounds the TLS handshake)
     * @param readTimer shared watchdog enforcing the read deadline for direct channel reads
     */
    public static VtH1Connection open(
            Route route,
            VtTlsContext tlsContext,
            int connectTimeoutMs,
            int readTimeoutMs,
            Timer readTimer
    ) throws IOException {
        boolean tls = route.isTls();
        // SocketChannel-backed socket: inbound bytes are read directly into a direct ByteBuf via
        // SocketChannel.read (no JDK socket-adaptor heap bounce). A blocking channel read ignores
        // SO_TIMEOUT, so the read deadline is enforced by the shared readTimer watchdog (see
        // readDirect). SO_TIMEOUT is still set for the InputStream paths (handshake, validateForReuse).
        Socket socket = SocketChannel.open().socket();
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.connect(new InetSocketAddress(route.host(), route.port()), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);

            EmbeddedChannel channel = new EmbeddedChannel();
            boolean openSsl = false;
            if (tls) {
                SslHandler ssl = tlsContext.newHandler(channel.alloc(), route.host(), route.port());
                channel.pipeline().addLast(ssl);
                openSsl = tlsContext.isOpenSsl();
            }
            channel.pipeline().addLast(new HttpClientCodec());

            var conn = new VtH1Connection(socket, channel, tls, openSsl, route, readTimer, readTimeoutMs);
            if (tls) {
                conn.handshake();
            }
            return conn;
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
            throw e;
        }
    }

    Route route() {
        return route;
    }

    boolean isKeepAlive() {
        return keepAlive;
    }

    void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    boolean isFromReuse() {
        return fromReuse;
    }

    void setFromReuse(boolean fromReuse) {
        this.fromReuse = fromReuse;
    }

    long lastUsedNanos() {
        return lastUsedNanos;
    }

    void markUsedNow() {
        this.lastUsedNanos = System.nanoTime();
    }

    EmbeddedChannel channel() {
        return channel;
    }

    /**
     * Whether this connection's TLS is the tcnative/OpenSSL engine ({@code wantsDirectBuffer=true}).
     * When true, staging the request body into a pooled <em>direct</em> {@link ByteBuf} lets
     * {@code SslHandler.wrap} encrypt it in place instead of copying heap plaintext into a direct
     * scratch buffer per 16&nbsp;KiB record. False for cleartext or the JDK engine (which is
     * already copy-free on heap input, so staging would only add a copy).
     */
    boolean usesOpenSslTls() {
        return openSsl;
    }

    boolean isOpen() {
        return !closed.get() && socket.isConnected() && !socket.isClosed() && channel.isOpen();
    }

    void setSoTimeout(int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
    }

    // ---- TLS handshake pump ----

    private void handshake() throws IOException {
        SslHandler ssl = channel.pipeline().get(SslHandler.class);
        // Firing channelActive starts the client handshake (wrapNonAppData produces the ClientHello
        // into the outbound queue). Then we shuttle ciphertext both ways until the handshake future
        // completes.
        channel.pipeline().fireChannelActive();
        Future<Channel> handshakeFuture = ssl.handshakeFuture();

        flushOutboundToSocket();
        while (!handshakeFuture.isDone()) {
            if (!pumpInboundOnce()) {
                throw new IOException("Connection closed during TLS handshake to " + route);
            }
            flushOutboundToSocket();
        }
        if (!handshakeFuture.isSuccess()) {
            Throwable cause = handshakeFuture.cause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("TLS handshake failed to " + route, cause);
        }
    }

    // ---- Outbound: drain encoded/encrypted bytes from the channel to the socket ----

    /**
     * Write an outbound message (an {@code HttpObject} or a {@code ByteBuf}) through the pipeline.
     * Does not flush to the socket; call {@link #flushOutboundToSocket()} after the final write of a
     * logical unit.
     */
    void write(Object msg) {
        channel.write(msg);
    }

    /**
     * Flush the pipeline and drain all pending outbound bytes to the socket. For TLS, the bytes
     * drained here are ciphertext produced by {@link SslHandler}.
     */
    void flushOutboundToSocket() throws IOException {
        channel.flush();
        ByteBuf out;
        while ((out = (ByteBuf) channel.readOutbound()) != null) {
            try {
                int len = out.readableBytes();
                if (len > 0) {
                    writeFully(out);
                }
            } finally {
                ReferenceCountUtil.release(out);
            }
        }
    }

    /**
     * Write all readable bytes of {@code buf} to the socket. Uses the {@link java.nio.channels.SocketChannel}
     * directly with the buffer's NIO view: for the direct (off-heap) ciphertext buffers tcnative/SslHandler
     * produce, {@code nioBuffer()} is a zero-copy view, so this avoids the temp-{@code byte[]} copy that
     * {@code ByteBuf.readBytes(OutputStream)} performs to bridge an off-heap buffer to an
     * {@code OutputStream} (previously ~1.8% CPU in {@code ByteBuffer.getArray} on the upload path).
     */
    private void writeFully(ByteBuf buf) throws IOException {
        int len = buf.readableBytes();
        int idx = buf.readerIndex();
        if (buf.nioBufferCount() == 1) {
            ByteBuffer nio = buf.nioBuffer(idx, len);
            while (nio.hasRemaining()) {
                socketChannel.write(nio);
            }
        } else {
            ByteBuffer[] nios = buf.nioBuffers(idx, len);
            long remaining = len;
            while (remaining > 0) {
                remaining -= socketChannel.write(nios);
            }
        }
    }

    // ---- Inbound: read ciphertext from the socket and feed the pipeline ----

    /**
     * Read one chunk from the socket and feed it inbound. Returns false on EOF (server closed).
     *
     * <p>Decoded HTTP objects (if any) land in {@link EmbeddedChannel#inboundMessages()} and are
     * retrieved by {@link #readInbound()}.
     */
    boolean pumpInboundOnce() throws IOException {
        return openSsl ? pumpInboundDirect() : pumpInboundHeap();
    }

    /**
     * Hot path (tcnative TLS): read ciphertext STRAIGHT into a pooled direct {@link ByteBuf} via
     * {@link SocketChannel#read(java.nio.ByteBuffer)} — no JDK socket-adaptor heap bounce, no second
     * copy, and the direct buffer feeds {@code SslHandler}/{@code SSLEngine.unwrap} in place. The
     * pipeline takes ownership of the buffer ({@code writeInbound}), so it cannot be a reused scratch.
     *
     * <p>A blocking {@code SocketChannel.read} ignores {@code SO_TIMEOUT}; the shared {@link #readTimer}
     * arms a one-shot watchdog that closes the socket if the read outlasts the deadline, converting a
     * stalled server into an {@link IOException} instead of an indefinite VT park.
     */
    private boolean pumpInboundDirect() throws IOException {
        ByteBuf buf = channel.alloc().directBuffer(SOCKET_READ_CHUNK);
        int n;
        try {
            ByteBuffer nio = buf.internalNioBuffer(buf.writerIndex(), buf.writableBytes());
            n = readWithDeadline(nio);
            if (n > 0) {
                buf.writerIndex(buf.writerIndex() + n);
            }
        } catch (IOException | RuntimeException e) {
            buf.release();
            throw e;
        }
        if (n < 0) {
            buf.release();
            return false;
        }
        if (n == 0) {
            buf.release();
            return true;
        }
        channel.writeInbound(buf);
        return true;
    }

    /** Cleartext / JDK-engine path: timeout-honoring InputStream read into a heap buffer. */
    private boolean pumpInboundHeap() throws IOException {
        int n = socketIn.read(readBuffer);
        if (n < 0) {
            return false;
        }
        if (n == 0) {
            return true;
        }
        ByteBuf buf = channel.alloc().heapBuffer(n);
        try {
            buf.writeBytes(readBuffer, 0, n);
        } catch (RuntimeException e) {
            buf.release();
            throw e;
        }
        channel.writeInbound(buf);
        return true;
    }

    /**
     * Blocking {@link SocketChannel} read with a watchdog-enforced deadline. The read parks the
     * virtual thread (no carrier pin); if it has not returned within {@code readTimeoutMs} the
     * watchdog closes the socket, which makes the parked read throw and unparks the VT.
     */
    private int readWithDeadline(ByteBuffer dst) throws IOException {
        if (readTimer == null || readTimeoutMs <= 0) {
            return socketChannel.read(dst);
        }
        Timeout watchdog = readTimer.newTimeout(t -> {
            // Only fires if the read is still outstanding past the deadline. Closing the channel
            // wakes the parked read with an AsynchronousCloseException; the connection is discarded.
            try {
                socketChannel.close();
            } catch (IOException ignored) {
                // best effort
            }
        }, readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        try {
            return socketChannel.read(dst);
        } catch (AsynchronousCloseException e) {
            throw new SocketTimeoutException("Read timed out after " + readTimeoutMs + "ms to " + route);
        } finally {
            watchdog.cancel();
        }
    }

    /**
     * Retrieve the next decoded inbound HTTP object, pumping the socket as needed. Returns null only
     * if the connection reached EOF before another object could be decoded.
     *
     * <p>Ownership of the returned object transfers to the caller (release {@code HttpContent}).
     */
    Object readInbound() throws IOException {
        Object msg = channel.readInbound();
        while (msg == null) {
            if (!pumpInboundOnce()) {
                // EOF: surface any final decoded object the codec emitted on close, else null.
                channel.finish();
                return channel.readInbound();
            }
            msg = channel.readInbound();
        }
        return msg;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            // Release any buffered inbound/outbound messages, then the channel and socket.
            channel.releaseInbound();
            channel.releaseOutbound();
            channel.close();
        } catch (RuntimeException e) {
            LOGGER.debug("Error closing embedded channel for {}: {}", route, e.getMessage());
        }
        try {
            socket.close();
        } catch (IOException e) {
            LOGGER.debug("Error closing socket for {}: {}", route, e.getMessage());
        }
    }

    /**
     * Cheap liveness probe for pooled reuse: a reused keep-alive may have been closed server-side.
     * A definitive check requires a read; callers gate expensive validation on idle age.
     */
    boolean validateForReuse() {
        if (!isOpen()) {
            return false;
        }
        try {
            int original = socket.getSoTimeout();
            socket.setSoTimeout(1);
            try {
                int n = socketIn.read(readBuffer, 0, 1);
                if (n < 0) {
                    return false; // server closed
                }
                if (n > 0) {
                    // Unexpected data on an idle keep-alive — treat as unusable.
                    return false;
                }
                return true;
            } catch (SocketTimeoutException e) {
                return true; // no data, still alive
            } finally {
                socket.setSoTimeout(original);
            }
        } catch (IOException e) {
            return false;
        }
    }
}
