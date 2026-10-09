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
 * Lets the benchmark fixture server, which lives in another module, reuse this package's blocking transports on
 * the accepting side: the plain socket transport, and the {@code SSLEngine} transport with its direct buffers,
 * handshake driver and batched record writes. Both are package-private by design, so this class is compiled into
 * the fixture server jar under this package name. It is not part of the client's API.
 */
public final class FixtureServerTransports {

    private FixtureServerTransports() {}

    /** The accepted socket as a transport, one system call per read or write. */
    public static ConnectionTransport plaintext(Socket socket) {
        return new SocketTransport(socket);
    }

    /**
     * Performs the TLS handshake on the accepted socket with a server-mode engine and returns the transport;
     * on failure the engine is released and the socket closed before the exception propagates.
     *
     * @param socket          the accepted, connected socket
     * @param engine          a server-mode engine with ALPN already configured on its context
     * @param releaser        frees the engine's native resources; invoked exactly once on close or on failure
     * @param readBufferSize  capacity for buffered ciphertext and plaintext; at least one TLS record
     * @param writeBufferSize capacity for outbound records; larger values coalesce more records per write
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
