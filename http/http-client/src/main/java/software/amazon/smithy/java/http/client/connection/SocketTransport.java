/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * Transport backed by a plain {@link Socket} or {@link SSLSocket}.
 *
 * <p>Used for plaintext connections and as a fallback for TLS when SSLEngine
 * transport is not available (e.g., proxy tunneling to the proxy itself).
 */
public final class SocketTransport implements Transport {

    private final Socket socket;

    public SocketTransport(Socket socket) {
        this.socket = socket;
    }

    Socket socket() {
        return socket;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public OutputStream outputStream() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        var ch = socket.getChannel();
        if (ch != null) {
            return ch;
        }
        return Channels.newChannel(socket.getInputStream());
    }

    @Override
    public WritableByteChannel writableChannel() throws IOException {
        var ch = socket.getChannel();
        if (ch != null) {
            return ch;
        }
        return Channels.newChannel(socket.getOutputStream());
    }

    @Override
    public SSLSession sslSession() {
        if (socket instanceof SSLSocket ssl) {
            return ssl.getSession();
        }
        return null;
    }

    @Override
    public String negotiatedProtocol() {
        if (socket instanceof SSLSocket ssl) {
            String proto = ssl.getApplicationProtocol();
            return (proto != null && !proto.isEmpty()) ? proto : null;
        }
        return null;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void setReadTimeout(int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
    }

    @Override
    public int getReadTimeout() throws IOException {
        return socket.getSoTimeout();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
