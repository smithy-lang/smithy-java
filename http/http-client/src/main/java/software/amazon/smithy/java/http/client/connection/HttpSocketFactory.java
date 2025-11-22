/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

/**
 * Factory for creating sockets used by the connection pool.
 *
 * <p>Implementations can customize socket creation and configuration based on the target route
 * and resolved endpoints. This enables use cases like different socket options per host (buffer sizes, timeouts),
 * binding to specific local interfaces based on destination, different configuration for IPv4 vs IPv6 endpoints,
 * and custom socket subclasses or wrappers.
 *
 * <p>The socket returned should be unconnected. The pool will call {@link Socket#connect} after receiving the socket
 * from this factory.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * HttpConnectionPool pool = HttpConnectionPool.builder()
 *     .socketFactory((route, endpoints) -> {
 *         Socket socket = new Socket();
 *         socket.setTcpNoDelay(true);
 *         socket.setKeepAlive(true);
 *         if (route.host().endsWith(".internal")) {
 *             socket.setSendBufferSize(256 * 1024);
 *         }
 *         return socket;
 *     })
 *     .build();
 * }</pre>
 *
 * @see HttpConnectionPool.Builder#socketFactory(HttpSocketFactory)
 */
@FunctionalInterface
public interface HttpSocketFactory {
    /**
     * Create a new unconnected socket for the given route.
     *
     * <p>The socket should be configured but not connected. The pool will connect it
     * to one of the provided endpoints.
     *
     * @param route the target route (host, port, secure flag)
     * @param endpoints the resolved IP addresses for the route's host, in preference order
     * @return a new unconnected socket
     */
    Socket newSocket(Route route, List<InetAddress> endpoints);

    /**
     * Default factory used to create sockets.
     *
     * <p>Creates sockets with TCP_NODELAY=true, SO_KEEPALIVE=true, and 64KB send/receive buffers.
     *
     * @param route the target route (unused in default implementation)
     * @param endpoints the resolved endpoints (unused in default implementation)
     * @return the created socket
     */
    static Socket defaultSocketFactory(Route route, List<InetAddress> endpoints) {
        try {
            Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            // Larger buffers for high throughput
            socket.setSendBufferSize(64 * 1024);
            socket.setReceiveBufferSize(64 * 1024);
            return socket;
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        }
    }
}
