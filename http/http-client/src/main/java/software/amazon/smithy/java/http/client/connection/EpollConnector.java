/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.util.Timer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Owns the experimental persistent-registration epoll socket backend for secure connections: the
 * shared {@link EpollRuntime} and the socket options to apply to each new {@link EpollChannel}.
 *
 * <p>This is an opt-in benchmarking alternative to the JDK NIO {@link java.nio.channels.SocketChannel}
 * for the TLS ({@link SSLEngineTransport}) path. It is created by {@link HttpConnectionPool} only when
 * {@link HttpConnectionPoolBuilder#useEpollTransport(boolean) useEpollTransport} is enabled AND
 * {@link EpollRuntime#isAvailable()} is true (Linux with the native epoll library). On any other host
 * the pool leaves this null and every connection uses the standard NIO path.
 *
 * <p>Because epoll connections do not flow through {@link HttpSocketFactory}, the connector applies
 * the same {@code SO_RCVBUF}/{@code SO_SNDBUF}/{@code SO_KEEPALIVE}/{@code TCP_NODELAY} options the
 * NIO socket factory would, so an A/B benchmark compares only the socket backend, not socket tuning.
 */
final class EpollConnector {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(EpollConnector.class);

    private final EpollRuntime runtime;
    private final EpollChannel.SocketOptions socketOptions;
    // Shared wheel-timer watchdog for read deadlines, handed to each channel. The SAME timer the NIO
    // SSLEngineTransport path uses, so both backends enforce read timeouts identically (O(1) wheel
    // arm/cancel) without the per-read DelayScheduler tax of LockSupport.parkNanos.
    private final Timer readTimer;

    private EpollConnector(EpollRuntime runtime, EpollChannel.SocketOptions socketOptions, Timer readTimer) {
        this.runtime = runtime;
        this.socketOptions = socketOptions;
        this.readTimer = readTimer;
    }

    /**
     * Create a connector if the epoll backend is requested and available; otherwise return null so
     * the caller falls back to the NIO socket path.
     *
     * @param receiveBufferSize SO_RCVBUF to apply, or null for kernel autotune
     * @param sendBufferSize SO_SNDBUF to apply, or null for kernel autotune
     * @param readTimer shared wheel-timer watchdog for read deadlines
     * @return a connector, or null if epoll is disabled or unavailable
     */
    static EpollConnector createIfAvailable(Integer receiveBufferSize, Integer sendBufferSize, Timer readTimer) {
        if (!EpollRuntime.isAvailable()) {
            LOGGER.warn("Epoll transport requested but native epoll is unavailable on this host; "
                    + "falling back to the JDK NIO socket transport", EpollRuntime.unavailabilityCause());
            return null;
        }
        var options = new EpollChannel.SocketOptions(receiveBufferSize, sendBufferSize, true);
        return new EpollConnector(EpollRuntime.shared(), options, readTimer);
    }

    /**
     * Open and connect a new epoll-backed channel to {@code address:port}.
     *
     * @param address the resolved remote IP
     * @param port the remote port
     * @param connectTimeoutMs connect deadline in milliseconds (0 = wait indefinitely)
     * @return a connected channel (TLS not yet started)
     */
    EpollChannel connect(InetAddress address, int port, int connectTimeoutMs) throws IOException {
        return EpollChannel.connect(
                runtime,
                new InetSocketAddress(address, port),
                connectTimeoutMs,
                socketOptions,
                readTimer);
    }
}
