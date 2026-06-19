/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import javax.net.ssl.SSLSession;

/**
 * A transport connection providing I/O streams, channels, and TLS metadata.
 *
 * <p>Abstracts over plain sockets and SSLEngine-based TLS connections,
 * allowing H1/H2 connections to work with either without knowing the
 * underlying transport mechanism.
 *
 * <p>Provides both stream-based (InputStream/OutputStream) and channel-based
 * (ReadableByteChannel/WritableByteChannel) I/O. The channel API lets callers use
 * ByteBuffers directly and avoid some intermediate byte[] copies.
 *
 * <p>This is the transport SPI for alternate TLS providers: a {@link TlsProvider} returns a
 * {@code ConnectionTransport} from {@link TlsProvider#connect}, and an implementation may live in a
 * separate module (e.g. a native TLS stack that does its own I/O rather than driving a JDK
 * {@code SSLEngine}). The H1/H2 layers consume only this interface and never observe which provider
 * produced it.
 */
public interface ConnectionTransport extends AutoCloseable {
    /**
     * Create a transport backed by a plain {@link Socket} or {@link javax.net.ssl.SSLSocket}.
     *
     * @param socket connected socket
     * @return socket-backed transport
     */
    static ConnectionTransport of(Socket socket) {
        return new SocketTransport(socket);
    }

    /**
     * Stream view of inbound (already-decrypted, for TLS transports) bytes. Reads honor the current
     * {@link #setReadTimeout(int) read timeout}.
     *
     * @return an input stream over the connection's plaintext bytes
     * @throws IOException if the stream cannot be obtained
     */
    InputStream inputStream() throws IOException;

    /**
     * Stream view for writing outbound bytes (encrypted before transmission, for TLS transports).
     *
     * @return an output stream over the connection
     * @throws IOException if the stream cannot be obtained
     */
    OutputStream outputStream() throws IOException;

    /**
     * Get a readable channel for ByteBuffer reads.
     *
     * <p>For TLS transports, this unwraps data directly into the caller's
     * ByteBuffer, avoiding intermediate byte[] copies. For plain socket
     * transports, this wraps the socket's channel or input stream.
     *
     * @return a readable byte channel
     */
    ReadableByteChannel readableChannel() throws IOException;

    /**
     * Returns true if this transport already has plaintext bytes buffered below
     * the channel returned by {@link #readableChannel()}.
     *
     * <p>This is a non-blocking hint used for read batching. Returning false is
     * always safe; it only means callers may wake a consumer earlier than necessary.
     *
     * @return true if plaintext data is available without socket I/O.
     */
    default boolean hasBufferedData() {
        return false;
    }

    /**
     * Get a writable channel for ByteBuffer writes.
     *
     * <p>For TLS transports, this wraps data directly from the caller's
     * ByteBuffer through SSLEngine, avoiding intermediate copies.
     *
     * @return a writable byte channel
     */
    WritableByteChannel writableChannel() throws IOException;

    /**
     * Per-connection TLS metadata. Used for observability (e.g. logging the negotiated cipher suite)
     * and is not required for I/O. A provider that does not expose a JSSE session may return null;
     * callers must tolerate null (including for plaintext connections).
     *
     * @return the SSL session for a TLS connection, or null if unavailable / not TLS
     */
    SSLSession sslSession();

    /**
     * The application protocol selected by ALPN during the handshake. Drives HTTP/1.1-vs-HTTP/2
     * selection, so a TLS provider that negotiated ALPN must report it here.
     *
     * @return the negotiated protocol (e.g. {@code "h2"}, {@code "http/1.1"}), or null if none
     */
    String negotiatedProtocol();

    /**
     * Whether the underlying connection is still usable. Pooled connections are validated with this
     * before reuse.
     *
     * @return true if the connection is open
     */
    boolean isOpen();

    /**
     * Set the read timeout applied to subsequent reads.
     *
     * @param timeoutMs timeout in milliseconds; 0 means no timeout (block indefinitely)
     * @throws IOException if the timeout cannot be applied to the underlying transport
     */
    void setReadTimeout(int timeoutMs) throws IOException;

    /**
     * @return the current read timeout in milliseconds (0 means no timeout)
     * @throws IOException if the timeout cannot be read from the underlying transport
     */
    int getReadTimeout() throws IOException;

    /**
     * Close the connection and release its resources. Implementations must be idempotent and must free
     * any provider-native resources (e.g. a reference-counted native engine) exactly once.
     *
     * @throws IOException if closing the underlying transport fails
     */
    @Override
    void close() throws IOException;
}
