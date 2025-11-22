/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.HttpConnection;
import software.amazon.smithy.java.http.client.HttpExchange;

public final class Http1Connection implements HttpConnection {
    private final Socket socket;
    private final InputStream socketIn;
    private final OutputStream socketOut;

    // HTTP/1.1: only one exchange at a time
    private final Semaphore exchangePermit = new Semaphore(1);
    private volatile boolean active = true;
    private volatile boolean keepAlive = true;

    public Http1Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = socket.getInputStream();
        this.socketOut = socket.getOutputStream();
    }

    @Override
    public HttpExchange newExchange(HttpRequest request) throws IOException {
        if (!active) {
            throw new IOException("Connection is closed");
        }

        // Block if another exchange is in progress
        try {
            exchangePermit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for connection", e);
        }

        try {
            return new Http1Exchange(this, request);
        } catch (IOException e) {
            // Failed to create exchange, release permit
            exchangePermit.release();
            throw e;
        }
    }

    @Override
    public String protocol() {
        return "http/1.1";
    }

    @Override
    public boolean isActive() {
        if (!active || socket.isClosed() || !keepAlive) {
            return false;
        }

        // Check for half-closed connection (either input or output shutdown)
        if (socket.isInputShutdown() || socket.isOutputShutdown()) {
            active = false;
            return false;
        }

        // Check if socket was closed by remote end
        // When idle (no exchange in progress), if there's data available,
        // it's likely a FIN packet indicating the server closed the connection
        if (exchangePermit.availablePermits() > 0) {
            try {
                // Non-blocking check: available() > 0 means server closed or sent unexpected data
                if (socketIn.available() > 0) {
                    // Socket has data when it shouldn't - server likely closed or sent garbage
                    active = false;
                    return false;
                }
            } catch (IOException e) {
                // Error checking socket state - consider it dead
                active = false;
                return false;
            }
        }

        return true;
    }

    @Override
    public void close() throws IOException {
        active = false;
        socket.close();
    }

    void releaseExchange() {
        exchangePermit.release();
    }

    void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    boolean isKeepAlive() {
        return keepAlive;
    }

    InputStream getInputStream() {
        return socketIn;
    }

    OutputStream getOutputStream() {
        return socketOut;
    }
}
