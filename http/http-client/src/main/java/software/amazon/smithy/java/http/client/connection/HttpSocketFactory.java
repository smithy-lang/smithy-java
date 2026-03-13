/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

/**
 * Factory for creating sockets used by the connection pool, allowing for customizing socket options.
 *
 * <p>The socket returned should be unconnected. The pool will call {@link Socket#connect} after receiving the socket
 * from this factory.
 *
 * <h2>Example</h2>
 * {@snippet :
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
 * }
 *
 * @see HttpConnectionPoolBuilder#socketFactory(HttpSocketFactory)
 */
@FunctionalInterface
public interface HttpSocketFactory {
    /**
     * Create a new <strong>unconnected</strong> socket for the given route.
     *
     * @param route the target route (host, port, secure flag)
     * @param endpoints the resolved IP addresses for the route's host, in preference order
     * @return a new <strong>unconnected</strong> socket
     */
    Socket newSocket(Route route, List<InetAddress> endpoints) throws IOException;

    /**
     * Default factory that creates sockets with TCP_NODELAY=true, SO_KEEPALIVE=true, and 64KB send/receive buffers.
     */
    HttpSocketFactory DEFAULT = (route, endpoints) -> {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSendBufferSize(64 * 1024);
        socket.setReceiveBufferSize(64 * 1024);
        return socket;
    };
}
