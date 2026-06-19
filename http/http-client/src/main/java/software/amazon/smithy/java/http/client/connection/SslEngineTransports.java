/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.net.Socket;
import javax.net.ssl.SSLEngine;

/**
 * Builds the built-in {@link ConnectionTransport} that drives a {@code javax.net.ssl.SSLEngine}.
 *
 * <p>This is the helper a {@link TlsProvider} uses when its TLS is engine-based: the provider mints a
 * client-mode {@link SSLEngine} (JDK, BoringSSL via netty-tcnative, …) and hands it here, and this
 * performs the connect-time dance — select the epoll vs. socket I/O backend, apply the negotiation
 * deadline, run the handshake, and release the engine on any failure. The underlying transport type is
 * internal to this module; providers in other modules reach it only through this entry point.
 */
public final class SslEngineTransports {

    private SslEngineTransports() {}

    /**
     * Wrap {@code engine} in a transport over the connection in {@code context}, perform the TLS
     * handshake, and return the ready transport.
     *
     * <p>On success the returned transport owns both the engine and the underlying socket/channel, and
     * frees them on {@link ConnectionTransport#close()}. On failure this invokes {@code releaser} and
     * closes the socket/channel before throwing, so the caller need not clean up.
     *
     * @param context the connected endpoint plus negotiation parameters
     * @param engine a configured client-mode SSL engine (client mode, endpoint id, ALPN already set)
     * @param releaser frees engine-native resources; invoked exactly once on close or on failure. May
     *     be null when there is nothing to release (e.g. the JDK engine).
     * @return a handshaken transport
     * @throws IOException if the handshake fails
     */
    public static ConnectionTransport connect(TlsConnectionContext context, SSLEngine engine, Runnable releaser)
            throws IOException {
        Runnable release = releaser != null ? releaser : () -> {};
        if (context.epollChannel() != null) {
            return connectEpoll(context, engine, release);
        }
        return connectSocket(context, engine, release);
    }

    private static ConnectionTransport connectEpoll(TlsConnectionContext context, SSLEngine engine, Runnable releaser)
            throws IOException {
        EpollChannel channel = context.epollChannel();
        try {
            // The negotiation deadline is honored by SSLEngineTransport's own timed-park read path
            // (epoll has no SO_TIMEOUT), then reset to the steady-state read timeout for requests.
            SSLEngineTransport transport = new SSLEngineTransport(
                    channel,
                    engine,
                    releaser,
                    context.negotiationTimeoutMillis(),
                    context.tlsReadBufferSize(),
                    context.tlsWriteBufferSize());
            transport.handshake();
            transport.setReadTimeout(context.readTimeoutMillis());
            return transport;
        } catch (IOException e) {
            releaser.run();
            channel.close();
            throw new IOException("TLS handshake failed for " + context.host(), e);
        } catch (RuntimeException e) {
            releaser.run();
            channel.close();
            throw e;
        }
    }

    private static ConnectionTransport connectSocket(TlsConnectionContext context, SSLEngine engine, Runnable releaser)
            throws IOException {
        Socket socket = context.socket();
        try {
            int originalTimeout = socket.getSoTimeout();
            socket.setSoTimeout(context.negotiationTimeoutMillis());
            try {
                SSLEngineTransport transport = new SSLEngineTransport(
                        socket,
                        engine,
                        releaser,
                        context.readTimer(),
                        context.tlsReadBufferSize(),
                        context.tlsWriteBufferSize());
                transport.handshake();
                return transport;
            } finally {
                socket.setSoTimeout(originalTimeout);
            }
        } catch (IOException e) {
            // Handshake/setup failed before SSLEngineTransport took ownership of the engine; release
            // any native engine resources here so they don't leak on the error path.
            releaser.run();
            closeQuietly(socket);
            throw new IOException("TLS handshake failed for " + context.host(), e);
        } catch (RuntimeException e) {
            releaser.run();
            closeQuietly(socket);
            throw e;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // ignored
        }
    }
}
