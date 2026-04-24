/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

/**
 * Non-blocking TLS transport driven by an external selector loop.
 *
 * <p>Unlike {@code SSLEngineTransport} which blocks on socket I/O, this transport
 * exposes non-blocking read/write methods and lets the caller drive the
 * {@link Selector} loop. The event loop is responsible for:
 * <ul>
 *   <li>Calling {@link #onReadable()} when the socket has bytes to read</li>
 *   <li>Calling {@link #onWritable()} when the socket can accept more writes</li>
 *   <li>Calling {@link #handshakeStep()} until handshake completes</li>
 *   <li>Calling {@link #wrap(ByteBuffer)} to encrypt application data</li>
 *   <li>Calling {@link #readPlaintext(ByteBuffer)} to consume decrypted data</li>
 * </ul>
 *
 * <p>Prototype only. Skips: renegotiation, graceful close_notify, delegated tasks on
 * a thread pool (runs them inline).
 */
final class NonBlockingSSLTransport {

    private final SocketChannel channel;
    private final SSLEngine engine;
    private final SelectionKey key;

    // Network ciphertext buffers (direct).
    // netIn: write-mode (accumulating bytes from socket). netOut: write-mode (accumulating wrap output).
    private ByteBuffer netIn;
    private ByteBuffer netOut;
    // Application plaintext buffer (read-mode, contains decrypted bytes not yet consumed).
    private ByteBuffer appIn;

    private boolean handshakeComplete;
    private boolean eof;

    NonBlockingSSLTransport(SocketChannel channel, SSLEngine engine, Selector selector) throws IOException {
        this.channel = channel;
        this.engine = engine;
        channel.configureBlocking(false);
        this.key = channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);

        int packetSize = engine.getSession().getPacketBufferSize();
        int appSize = engine.getSession().getApplicationBufferSize();
        this.netIn = ByteBuffer.allocateDirect(packetSize);   // write mode
        this.netOut = ByteBuffer.allocateDirect(packetSize);  // write mode
        this.appIn = ByteBuffer.allocate(appSize);
        this.appIn.flip();                                    // read mode (empty)
    }

    static NonBlockingSSLTransport connect(String host, int port, SSLContext sslCtx, Selector selector) throws IOException {
        SocketChannel ch = SocketChannel.open();
        ch.configureBlocking(false);
        ch.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
        ch.connect(new InetSocketAddress(host, port));
        // Wait for connect to finish
        while (!ch.finishConnect()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during connect", e);
            }
        }
        SSLEngine engine = sslCtx.createSSLEngine(host, port);
        engine.setUseClientMode(true);
        var params = engine.getSSLParameters();
        params.setApplicationProtocols(new String[]{"h2"});
        engine.setSSLParameters(params);
        engine.beginHandshake();
        return new NonBlockingSSLTransport(ch, engine, selector);
    }

    SelectionKey selectionKey() {
        return key;
    }

    SocketChannel channel() {
        return channel;
    }

    boolean handshakeComplete() {
        return handshakeComplete;
    }

    boolean eof() {
        return eof;
    }

    String applicationProtocol() {
        return engine.getApplicationProtocol();
    }

    /**
     * Drive the handshake state machine as far as it will go with currently available data.
     * Call repeatedly as OP_READ/OP_WRITE events fire until {@link #handshakeComplete()} returns true.
     */
    void handshakeStep() throws IOException {
        if (handshakeComplete) {
            return;
        }
        HandshakeStatus hs = engine.getHandshakeStatus();
        while (!handshakeComplete) {
            switch (hs) {
                case NEED_WRAP -> {
                    SSLEngineResult r = wrapEmpty();
                    if (r.getStatus() == Status.BUFFER_OVERFLOW) {
                        return; // wait for socket writable to drain netOut
                    }
                    // flush immediately
                    if (!flushNetOut()) {
                        return; // socket full, retry on OP_WRITE
                    }
                    hs = r.getHandshakeStatus();
                }
                case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                    // Read more if nothing buffered
                    if (netIn.position() == 0) {
                        int n = channel.read(netIn);
                        if (n < 0) {
                            eof = true;
                            throw new SSLException("EOF during handshake");
                        }
                        if (n == 0) {
                            return; // need more data
                        }
                    }
                    netIn.flip();
                    ByteBuffer scratch = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                    SSLEngineResult r = engine.unwrap(netIn, scratch);
                    netIn.compact();
                    if (r.getStatus() == Status.BUFFER_UNDERFLOW) {
                        return; // need more data
                    }
                    if (r.getStatus() == Status.CLOSED) {
                        eof = true;
                        throw new SSLException("Engine closed during handshake");
                    }
                    hs = r.getHandshakeStatus();
                }
                case NEED_TASK -> {
                    Runnable t;
                    while ((t = engine.getDelegatedTask()) != null) {
                        t.run();
                    }
                    hs = engine.getHandshakeStatus();
                }
                case FINISHED, NOT_HANDSHAKING -> {
                    handshakeComplete = true;
                    return;
                }
            }
        }
    }

    private SSLEngineResult wrapEmpty() throws IOException {
        ByteBuffer empty = ByteBuffer.allocate(0);
        return engine.wrap(empty, netOut);
    }

    /**
     * Flush any pending wrapped bytes to the socket.
     * @return true if netOut is fully drained; false if socket would block (OP_WRITE needed)
     */
    boolean flushNetOut() throws IOException {
        if (netOut.position() == 0) {
            return true;
        }
        netOut.flip();
        while (netOut.hasRemaining()) {
            int n = channel.write(netOut);
            if (n == 0) {
                // socket full; leave netOut in read-mode with leftover, or flip back
                // We need netOut in write-mode for next wrap. Compact does that.
                netOut.compact();
                return false;
            }
        }
        netOut.clear();
        return true;
    }

    /**
     * Wrap plaintext into netOut. Caller must subsequently flush via {@link #flushNetOut()}.
     * @return bytes consumed from src
     */
    int wrap(ByteBuffer src) throws IOException {
        if (!handshakeComplete) {
            throw new SSLException("Handshake not complete");
        }
        int consumed = 0;
        while (src.hasRemaining()) {
            SSLEngineResult r = engine.wrap(src, netOut);
            consumed += r.bytesConsumed();
            Status st = r.getStatus();
            if (st == Status.BUFFER_OVERFLOW) {
                // netOut full; caller must flush before continuing
                return consumed;
            }
            if (st == Status.CLOSED) {
                throw new SSLException("Engine closed during wrap");
            }
            if (r.bytesConsumed() == 0 && r.bytesProduced() == 0) {
                break;
            }
        }
        return consumed;
    }

    /**
     * Called by the event loop when OP_READ fires. Reads socket into netIn and unwraps
     * as much as possible into the internal appIn buffer.
     * @return bytes of plaintext now available; -1 on EOF
     */
    int onReadable() throws IOException {
        int n = channel.read(netIn);
        if (n < 0) {
            eof = true;
            return appIn.hasRemaining() ? appIn.remaining() : -1;
        }
        unwrapAll();
        return appIn.remaining();
    }

    private void unwrapAll() throws IOException {
        if (netIn.position() == 0) {
            return;
        }
        // We unwrap into appIn. appIn may have unread plaintext; compact to preserve it.
        netIn.flip();
        try {
            appIn.compact(); // now write-mode with existing unread bytes preserved
            while (netIn.hasRemaining()) {
                SSLEngineResult r = engine.unwrap(netIn, appIn);
                Status st = r.getStatus();
                if (st == Status.BUFFER_UNDERFLOW) {
                    break;
                }
                if (st == Status.BUFFER_OVERFLOW) {
                    // Grow appIn
                    ByteBuffer bigger = ByteBuffer.allocate(appIn.capacity() * 2);
                    appIn.flip();
                    bigger.put(appIn);
                    appIn = bigger;
                    continue;
                }
                if (st == Status.CLOSED) {
                    eof = true;
                    break;
                }
                if (r.bytesConsumed() == 0 && r.bytesProduced() == 0) {
                    break;
                }
            }
        } finally {
            netIn.compact();
            appIn.flip(); // back to read mode
        }
    }

    /**
     * Drain decrypted bytes into the caller's buffer. Returns bytes transferred.
     */
    int readPlaintext(ByteBuffer dst) {
        if (!appIn.hasRemaining()) {
            return eof ? -1 : 0;
        }
        int n = Math.min(appIn.remaining(), dst.remaining());
        int oldLimit = appIn.limit();
        appIn.limit(appIn.position() + n);
        dst.put(appIn);
        appIn.limit(oldLimit);
        return n;
    }

    int availablePlaintext() {
        return appIn.remaining();
    }

    boolean netOutHasPending() {
        return netOut.position() > 0;
    }

    void setInterestOps(int ops) {
        key.interestOps(ops);
    }

    int interestOps() {
        return key.interestOps();
    }

    void close() throws IOException {
        try {
            engine.closeOutbound();
        } catch (Exception ignored) {
        }
        try {
            channel.close();
        } finally {
            key.cancel();
        }
    }
}
