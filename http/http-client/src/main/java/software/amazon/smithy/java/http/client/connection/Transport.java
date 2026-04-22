/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * (ReadableByteChannel/WritableByteChannel) I/O. The channel API enables
 * zero-copy data paths by operating directly on ByteBuffers.
 */
public interface Transport extends AutoCloseable {

    InputStream inputStream() throws IOException;

    OutputStream outputStream() throws IOException;

    /**
     * Get a readable channel for zero-copy reads into ByteBuffers.
     *
     * <p>For TLS transports, this unwraps data directly into the caller's
     * ByteBuffer, avoiding intermediate byte[] copies. For plain socket
     * transports, this wraps the socket's channel or input stream.
     *
     * @return a readable byte channel
     */
    ReadableByteChannel readableChannel() throws IOException;

    /**
     * Get a writable channel for zero-copy writes from ByteBuffers.
     *
     * <p>For TLS transports, this wraps data directly from the caller's
     * ByteBuffer through SSLEngine, avoiding intermediate copies.
     *
     * @return a writable byte channel
     */
    WritableByteChannel writableChannel() throws IOException;

    /**
     * @return the SSL session if this is a TLS connection, null otherwise.
     */
    SSLSession sslSession();

    /**
     * @return the ALPN-negotiated protocol (e.g. "h2", "http/1.1"), or null.
     */
    String negotiatedProtocol();

    /**
     * Check if the underlying connection is still open.
     */
    boolean isOpen();

    /**
     * Set the read timeout in milliseconds. 0 means infinite.
     */
    void setReadTimeout(int timeoutMs) throws IOException;

    /**
     * Get the current read timeout in milliseconds.
     */
    int getReadTimeout() throws IOException;

    @Override
    void close() throws IOException;
}
