/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.util.Timer;
import java.net.Socket;
import java.util.List;

/**
 * The inputs a {@link TlsProvider} needs to establish a TLS connection: the connected (plaintext)
 * endpoint plus negotiation parameters.
 *
 * <p>New inputs are added as fields here rather than as parameters on {@link TlsProvider#connect},
 * so the provider contract stays stable as the client evolves.
 *
 * <p>Exactly one transport substrate is present. For the common path that is a connected
 * {@link #socket()}. The experimental epoll backend is carried internally and is not exposed to
 * out-of-module providers.
 */
public final class TlsConnectionContext {

    private final String host;
    private final int port;
    private final List<String> alpnProtocols;
    private final int negotiationTimeoutMillis;
    private final int readTimeoutMillis;
    private final int tlsReadBufferSize;
    private final int tlsWriteBufferSize;

    // Exactly one of these is non-null. socket is the public substrate; epollChannel is the internal
    // (Linux-only) backend, kept package-private so out-of-module providers only see the socket path.
    private final Socket socket;
    private final EpollChannel epollChannel;
    private final Timer readTimer;

    private TlsConnectionContext(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.alpnProtocols = b.alpnProtocols == null ? List.of() : List.copyOf(b.alpnProtocols);
        this.negotiationTimeoutMillis = b.negotiationTimeoutMillis;
        this.readTimeoutMillis = b.readTimeoutMillis;
        this.tlsReadBufferSize = b.tlsReadBufferSize;
        this.tlsWriteBufferSize = b.tlsWriteBufferSize;
        this.socket = b.socket;
        this.epollChannel = b.epollChannel;
        this.readTimer = b.readTimer;
    }

    /** Peer host, for SNI and endpoint identification. */
    public String host() {
        return host;
    }

    /** Peer port. */
    public int port() {
        return port;
    }

    /** ALPN protocols to advertise (e.g. {@code ["h2", "http/1.1"]}); never null, possibly empty. */
    public List<String> alpnProtocols() {
        return alpnProtocols;
    }

    /** Handshake deadline in milliseconds; 0 means none. */
    public int negotiationTimeoutMillis() {
        return negotiationTimeoutMillis;
    }

    /** Steady-state read timeout in milliseconds to apply after the handshake; 0 means none. */
    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    /** Target capacity for the ciphertext-read / plaintext-unwrap buffers. */
    public int tlsReadBufferSize() {
        return tlsReadBufferSize;
    }

    /** Target capacity for the ciphertext-write buffer. */
    public int tlsWriteBufferSize() {
        return tlsWriteBufferSize;
    }

    /**
     * The connected plaintext socket, or null when this request uses the internal epoll backend.
     * Out-of-module providers always receive a socket.
     *
     * @return the connected socket, or null
     */
    public Socket socket() {
        return socket;
    }

    // ----- internal accessors for the in-module epoll backend -----

    EpollChannel epollChannel() {
        return epollChannel;
    }

    Timer readTimer() {
        return readTimer;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private String host;
        private int port;
        private List<String> alpnProtocols;
        private int negotiationTimeoutMillis;
        private int readTimeoutMillis;
        private int tlsReadBufferSize;
        private int tlsWriteBufferSize;
        private Socket socket;
        private EpollChannel epollChannel;
        private Timer readTimer;

        Builder host(String host) {
            this.host = host;
            return this;
        }

        Builder port(int port) {
            this.port = port;
            return this;
        }

        Builder alpnProtocols(List<String> alpnProtocols) {
            this.alpnProtocols = alpnProtocols;
            return this;
        }

        Builder negotiationTimeoutMillis(int millis) {
            this.negotiationTimeoutMillis = millis;
            return this;
        }

        Builder readTimeoutMillis(int millis) {
            this.readTimeoutMillis = millis;
            return this;
        }

        Builder tlsReadBufferSize(int size) {
            this.tlsReadBufferSize = size;
            return this;
        }

        Builder tlsWriteBufferSize(int size) {
            this.tlsWriteBufferSize = size;
            return this;
        }

        Builder socket(Socket socket) {
            this.socket = socket;
            return this;
        }

        Builder epollChannel(EpollChannel epollChannel) {
            this.epollChannel = epollChannel;
            return this;
        }

        Builder readTimer(Timer readTimer) {
            this.readTimer = readTimer;
            return this;
        }

        TlsConnectionContext build() {
            return new TlsConnectionContext(this);
        }
    }
}
