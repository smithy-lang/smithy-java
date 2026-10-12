/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import javax.net.ssl.SSLEngine;

/**
 * Exposes the HTTP client's package-private socket and TLS transports to the fixture server.
 */
public final class FixtureServerTransports {

    private FixtureServerTransports() {}

    public static ConnectionTransport plaintext(Socket socket) {
        return new SocketTransport(socket);
    }

    /**
     * Requires a server-mode engine with ALPN configured.
     * Calls releaser exactly once on close or handshake failure. Failure also closes the socket.
     * The read buffer must hold at least one TLS record.
     */
    public static ConnectionTransport tls(
            Socket socket,
            SSLEngine engine,
            Runnable releaser,
            int readBufferSize,
            int writeBufferSize
    ) throws IOException {
        var context = TlsConnectionContext.builder()
                .host(socket.getInetAddress().getHostAddress())
                .port(socket.getPort())
                .alpnProtocols(List.of("http/1.1"))
                .negotiationTimeoutMillis(10_000)
                .readTimeoutMillis(0)
                .tlsReadBufferSize(readBufferSize)
                .tlsWriteBufferSize(writeBufferSize)
                .socket(socket)
                .build();
        return SslEngineTransports.connect(context, engine, releaser);
    }
}
