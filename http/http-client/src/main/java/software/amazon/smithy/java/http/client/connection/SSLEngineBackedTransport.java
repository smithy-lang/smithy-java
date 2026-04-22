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
 * Transport backed by {@link SSLEngineTransport} for zero-copy TLS.
 */
final class SSLEngineBackedTransport implements Transport {

    private final SSLEngineTransport transport;

    SSLEngineBackedTransport(SSLEngineTransport transport) {
        this.transport = transport;
    }

    @Override
    public InputStream inputStream() {
        return transport.inputStream();
    }

    @Override
    public OutputStream outputStream() {
        return transport.outputStream();
    }

    @Override
    public ReadableByteChannel readableChannel() {
        return transport.readableChannel();
    }

    @Override
    public boolean hasBufferedData() {
        return transport.hasBufferedData();
    }

    @Override
    public WritableByteChannel writableChannel() {
        return transport.writableChannel();
    }

    @Override
    public SSLSession sslSession() {
        return transport.getSession();
    }

    @Override
    public String negotiatedProtocol() {
        String proto = transport.getApplicationProtocol();
        return (proto != null && !proto.isEmpty()) ? proto : null;
    }

    @Override
    public boolean isOpen() {
        return !transport.isClosed();
    }

    @Override
    public void setReadTimeout(int timeoutMs) throws IOException {
        transport.setReadTimeout(timeoutMs);
    }

    @Override
    public int getReadTimeout() throws IOException {
        return transport.getReadTimeout();
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }
}
