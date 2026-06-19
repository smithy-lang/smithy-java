/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.util.Timer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.java.http.client.HttpClientListener;
import software.amazon.smithy.java.http.client.HttpCredentials;
import software.amazon.smithy.java.http.client.ProxyConfiguration;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.h1.H1Connection;
import software.amazon.smithy.java.http.client.h2.H2Connection;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Factory for creating HTTP connections.
 *
 * <p>Handles connection creation including:
 * <ul>
 *   <li>DNS resolution with multi-IP failover</li>
 *   <li>TLS handshake and ALPN negotiation</li>
 *   <li>Proxy tunneling (HTTP and HTTPS proxies)</li>
 *   <li>Protocol selection (HTTP/1.1 vs HTTP/2)</li>
 * </ul>
 *
 * @param sslParameters may be null
 */
record HttpConnectionFactory(
        Duration connectTimeout,
        Duration tlsNegotiationTimeout,
        Duration readTimeout,
        Duration writeTimeout,
        SSLContext sslContext,
        SSLParameters sslParameters,
        TlsProvider tlsProvider,
        // True when tlsProvider is the built-in JDK provider derived from the config's sslContext/
        // sslParameters (no explicit or discovered provider). Gates the HTTP/1.1-only SSLSocket fast
        // path, which is a JDK-only optimization using those same config-level settings.
        boolean defaultJdkTls,
        HttpVersionPolicy versionPolicy,
        DnsResolver dnsResolver,
        List<HttpClientListener> listeners,
        boolean hasListeners,
        HttpSocketFactory socketFactory,
        Timer readTimer,
        EpollConnector epollConnector,
        int h2InitialWindowSize,
        int h2MaxFrameSize,
        int h2BufferSize,
        int tlsReadBufferSize,
        int tlsWriteBufferSize) {

    /**
     * Create a new connection to the given route.
     *
     * @param route the route to connect to
     * @return a new HttpConnection
     * @throws IOException if connection fails
     */
    HttpConnection create(Route route, long exchangeId) throws IOException {
        if (route.usesProxy()) {
            return connectViaProxy(route, exchangeId);
        }

        List<InetAddress> addresses = resolve(route.host(), exchangeId);

        IOException lastException = null;
        for (InetAddress address : addresses) {
            try {
                return connectToAddress(address, route, addresses, exchangeId);
            } catch (IOException e) {
                lastException = e;
                dnsResolver.reportFailure(address);
            }
        }

        throw new IOException(
                "Failed to connect to " + route.host() + " on any resolved IP (" + addresses.size() + " tried)",
                lastException);
    }

    private HttpConnection connectToAddress(
            InetAddress address,
            Route route,
            List<InetAddress> allEndpoints,
            long exchangeId
    ) throws IOException {
        if (epollConnector != null) {
            return route.isSecure()
                    ? connectEpollTls(address, route, exchangeId)
                    : connectEpollCleartext(address, route, exchangeId);
        }

        Socket socket = socketFactory.newSocket(route, allEndpoints);
        connectSocket(address, route, exchangeId, socket, route.port());

        ConnectionTransport transport;
        if (!route.isSecure()) {
            transport = ConnectionTransport.of(socket);
        } else if (versionPolicy == HttpVersionPolicy.ENFORCE_HTTP_1_1 && defaultJdkTls) {
            // HTTP/1.1-only on the default JDK provider: the SSLSocket path is cheaper than the
            // SSLEngine wrap/unwrap loop and uses the same config sslContext/sslParameters. A custom or
            // discovered provider must own the handshake, so it takes the provider path instead.
            transport = performTlsSocketHandshake(socket, route, exchangeId);
        } else {
            transport = performTlsHandshake(socket, route, exchangeId);
        }

        return createProtocolConnection(transport, route);
    }

    private void connectSocket(InetAddress address, Route route, long exchangeId, Socket socket, int port)
            throws IOException {
        notifyConnectStart(exchangeId, route, address);
        try {
            socket.connect(new InetSocketAddress(address, port), toIntMillis(connectTimeout));
            notifyConnectEnd(exchangeId, route, address, null);
        } catch (IOException | RuntimeException e) {
            notifyConnectEnd(exchangeId, route, address, e);
            // This helper closes the socket on connect failure so the direct path (which has no outer
            // catch) does not leak it. Callers with an outer catch-all (the proxy path) may close it
            // again; that is safe since closeQuietly and Socket.close are idempotent.
            closeQuietly(socket);
            throw e;
        }
    }

    private EpollChannel connectEpollChannel(InetAddress address, Route route, long exchangeId) throws IOException {
        try {
            notifyConnectStart(exchangeId, route, address);
            try {
                EpollChannel channel = epollConnector.connect(address, route.port(), toIntMillis(connectTimeout));
                notifyConnectEnd(exchangeId, route, address, null);
                return channel;
            } catch (IOException | RuntimeException e) {
                notifyConnectEnd(exchangeId, route, address, e);
                throw e;
            }
        } catch (IOException e) {
            throw new IOException("Failed to connect to " + route.host() + " via epoll transport", e);
        }
    }

    private HttpConnection connectEpollCleartext(InetAddress address, Route route, long exchangeId) throws IOException {
        EpollChannel channel = connectEpollChannel(address, route, exchangeId);
        try {
            return createProtocolConnection(new EpollTransport(channel, toIntMillis(readTimeout)), route);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private HttpConnection connectEpollTls(InetAddress address, Route route, long exchangeId) throws IOException {
        EpollChannel channel = connectEpollChannel(address, route, exchangeId);

        TlsConnectionContext connection = tlsConnection(route)
                .epollChannel(channel)
                // The negotiation deadline is honored by SSLEngineTransport's own timed-park read path
                // (epoll has no SO_TIMEOUT); readTimeoutMillis is applied as the steady-state deadline.
                .readTimeoutMillis(toIntMillis(readTimeout))
                .build();

        notifyTlsStart(exchangeId, route);
        ConnectionTransport transport;
        try {
            transport = tlsProvider.connect(connection);
        } catch (IOException | RuntimeException e) {
            // connect() already released the engine and closed the channel on failure.
            notifyTlsEnd(exchangeId, route, null, e);
            throw e;
        }
        notifyTlsEnd(exchangeId, route, transport, null);
        return createProtocolConnection(transport, route);
    }

    private ConnectionTransport performTlsHandshake(Socket socket, Route route, long exchangeId) throws IOException {
        TlsConnectionContext connection = tlsConnection(route)
                .socket(socket)
                .readTimer(readTimer)
                .build();

        notifyTlsStart(exchangeId, route);
        ConnectionTransport transport;
        try {
            transport = tlsProvider.connect(connection);
        } catch (IOException | RuntimeException e) {
            // connect() already released the engine and closed the socket on failure.
            notifyTlsEnd(exchangeId, route, null, e);
            throw e;
        }
        notifyTlsEnd(exchangeId, route, transport, null);
        return transport;
    }

    // Shared TlsConnectionContext skeleton (host/port/ALPN/negotiation deadline/buffer sizes); the caller
    // adds the transport substrate (socket or epoll channel).
    private TlsConnectionContext.Builder tlsConnection(Route route) {
        return TlsConnectionContext.builder()
                .host(route.host())
                .port(route.port())
                .alpnProtocols(List.of(versionPolicy.alpnProtocols()))
                .negotiationTimeoutMillis(toIntMillis(tlsNegotiationTimeout))
                .tlsReadBufferSize(tlsReadBufferSize)
                .tlsWriteBufferSize(tlsWriteBufferSize);
    }

    private ConnectionTransport performTlsSocketHandshake(Socket socket, Route route, long exchangeId)
            throws IOException {
        SSLSocket sslSocket = null;
        try {
            sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, route.host(), route.port(), true);
            sslSocket.setSSLParameters(socketParameters(sslSocket, versionPolicy.alpnProtocols()));

            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout(toIntMillis(tlsNegotiationTimeout));
            try {
                notifyTlsStart(exchangeId, route);
                try {
                    sslSocket.startHandshake();
                    notifyTlsEnd(exchangeId,
                            route,
                            sslSocket.getApplicationProtocol(),
                            sslSocket.getSession().getCipherSuite(),
                            null);
                } catch (IOException | RuntimeException e) {
                    notifyTlsEnd(exchangeId, route, null, null, e);
                    throw e;
                }
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }

            return ConnectionTransport.of(sslSocket);
        } catch (IOException e) {
            closeQuietly(sslSocket != null ? sslSocket : socket);
            throw new IOException("TLS handshake failed for " + route.host(), e);
        }
    }

    // SSLParameters for the HTTP/1.1-only SSLSocket fast path (and proxy TLS). The SSLEngine path is
    // handled by JdkTlsProvider; this mirrors its parameter handling for the SSLSocket case.
    private SSLParameters socketParameters(SSLSocket sslSocket, String[] applicationProtocols) {
        SSLParameters params = sslParameters != null
                ? JdkTlsProvider.copyParameters(sslParameters)
                : sslSocket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        if (applicationProtocols != null) {
            params.setApplicationProtocols(applicationProtocols);
        }
        return params;
    }

    enum Protocol {
        H1, H2
    }

    private HttpConnection createProtocolConnection(ConnectionTransport transport, Route route) throws IOException {
        try {
            Protocol protocol = selectProtocol(transport.negotiatedProtocol(), route.isSecure(), versionPolicy);
            return switch (protocol) {
                case H2 -> createH2Connection(transport, route);
                case H1 -> new H1Connection(transport, route, readTimeout);
            };
        } catch (IOException e) {
            try {
                transport.close();
            } catch (IOException ignored) {
                // ignored
            }
            throw e;
        }
    }

    static Protocol selectProtocol(String negotiated, boolean secure, HttpVersionPolicy policy) throws IOException {
        if (negotiated != null && !negotiated.isEmpty()) {
            return switch (negotiated) {
                case "h2" -> {
                    if (policy == HttpVersionPolicy.ENFORCE_HTTP_1_1) {
                        throw new IOException("Server negotiated HTTP/2 but client is configured for HTTP/1.1 only");
                    }
                    yield Protocol.H2;
                }
                case "http/1.1" -> {
                    if (policy == HttpVersionPolicy.ENFORCE_HTTP_2 || policy == HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE) {
                        throw new IOException("Server negotiated HTTP/1.1 but client is configured for HTTP/2 only");
                    }
                    yield Protocol.H1;
                }
                default -> throw new IOException("Unsupported negotiated protocol: " + negotiated);
            };
        }

        if (secure) {
            if (policy == HttpVersionPolicy.ENFORCE_HTTP_2 || policy == HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE) {
                throw new IOException("No HTTP/2 protocol negotiated by TLS ALPN");
            }
            return Protocol.H1;
        } else if (policy.usesH2cForCleartext()) {
            return Protocol.H2;
        } else if (policy == HttpVersionPolicy.ENFORCE_HTTP_2) {
            throw new IOException("HTTP/2 without TLS requires h2c prior knowledge");
        } else {
            return Protocol.H1;
        }
    }

    private H2Connection createH2Connection(ConnectionTransport transport, Route route) throws IOException {
        return new H2Connection(transport,
                route,
                readTimeout,
                writeTimeout,
                h2InitialWindowSize,
                h2MaxFrameSize,
                h2BufferSize);
    }

    private HttpConnection connectViaProxy(Route route, long exchangeId) throws IOException {
        ProxyConfiguration proxy = route.proxy();

        if (proxy.type() == ProxyConfiguration.ProxyType.SOCKS4
                || proxy.type() == ProxyConfiguration.ProxyType.SOCKS5) {
            // IOException (not UnsupportedOperationException) so the caller's per-proxy catch treats this
            // as a route-attempt failure: ProxySelector.connectFailed runs and the next proxy is tried.
            throw new IOException("SOCKS proxies not yet supported: " + proxy.type());
        }

        List<InetAddress> proxyAddresses = resolve(proxy.hostname(), exchangeId);

        IOException lastException = null;
        for (InetAddress proxyAddress : proxyAddresses) {
            try {
                return connectToProxy(proxyAddress, route, proxy, proxyAddresses, exchangeId);
            } catch (IOException e) {
                lastException = e;
                dnsResolver.reportFailure(proxyAddress);
            }
        }

        throw new IOException(
                "Failed to connect to proxy " + proxy.hostname() + " on any resolved IP (" +
                        proxyAddresses.size() + " tried)",
                lastException);
    }

    private HttpConnection connectToProxy(
            InetAddress proxyAddress,
            Route route,
            ProxyConfiguration proxy,
            List<InetAddress> allProxyEndpoints,
            long exchangeId
    ) throws IOException {
        Socket proxySocket = socketFactory.newSocket(route, allProxyEndpoints);

        try {
            connectSocket(proxyAddress, route, exchangeId, proxySocket, proxy.port());

            // Connect to the proxy over TLS if the scheme is https
            if ("https".equalsIgnoreCase(proxy.proxyUri().getScheme())) {
                // Use SSLSocket for proxy TLS; the tunnel itself does not need the SSLEngine path.
                proxySocket = performTlsHandshakeToProxy(proxySocket, proxy);
            }

            if (route.isSecure()) {
                notifyProxyConnectStart(exchangeId, route, proxy, proxyAddress);
                TunnelResult result;
                try {
                    result = establishTunnel(
                            proxySocket,
                            route.host(),
                            route.port(),
                            proxy.credentials(),
                            readTimeout);
                } catch (IOException | RuntimeException e) {
                    notifyProxyConnectEnd(exchangeId, route, proxy, proxyAddress, -1, e);
                    throw e;
                }

                // A non-200 CONNECT is a tunnel failure, not a success: report it through the terminal
                // event with an error (consistent with every other *End event) before throwing.
                if (result.statusCode() != 200) {
                    var failure = new IOException("Proxy CONNECT failed: " + result.statusCode());
                    notifyProxyConnectEnd(exchangeId, route, proxy, proxyAddress, result.statusCode(), failure);
                    closeQuietly(proxySocket);
                    throw failure;
                }
                notifyProxyConnectEnd(exchangeId, route, proxy, proxyAddress, result.statusCode(), null);

                // Mirror the direct path: the SSLSocket shortcut is a JDK-default optimization, so a
                // custom or discovered provider must own the end-to-end handshake through the tunnel.
                ConnectionTransport transport =
                        versionPolicy == HttpVersionPolicy.ENFORCE_HTTP_1_1 && defaultJdkTls
                                ? performTlsSocketHandshake(proxySocket, route, exchangeId)
                                : performTlsHandshake(proxySocket, route, exchangeId);
                return createProtocolConnection(transport, route);
            }

            return createProtocolConnection(ConnectionTransport.of(proxySocket), route);
        } catch (IOException e) {
            closeQuietly(proxySocket);
            throw new IOException(
                    "Failed to connect to " + route.host() + " via proxy " +
                            proxy.hostname() + ":" + proxy.port() + " (" + proxyAddress.getHostAddress() + ")",
                    e);
        }
    }

    record TunnelResult(Socket socket, int statusCode, HttpHeaders headers) {}

    static TunnelResult establishTunnel(
            Socket proxySocket,
            String targetHost,
            int targetPort,
            HttpCredentials credentials,
            Duration readTimeout
    ) throws IOException {
        Route proxyRoute = Route.direct(
                "http",
                proxySocket.getInetAddress().getHostAddress(),
                proxySocket.getPort());
        H1Connection conn = new H1Connection(ConnectionTransport.of(proxySocket), proxyRoute, readTimeout);

        HttpResponse priorResponse = null;

        do {
            String authority = targetHost + ":" + targetPort;
            ModifiableHttpRequest connectRequest = HttpRequest.create()
                    .setMethod("CONNECT")
                    .setUri(SmithyUri.of("http://" + authority))
                    .addHeader("Host", authority)
                    .addHeader("Proxy-Connection", "Keep-Alive");

            if (credentials != null) {
                boolean applied = credentials.authenticate(connectRequest, priorResponse);
                if (!applied && priorResponse != null) {
                    break;
                }
            }

            var exchange = conn.newExchange(connectRequest);
            exchange.writeRequestBody(null);

            int status = exchange.responseStatusCode();
            HttpHeaders headers = exchange.responseHeaders();

            if (status == 200) {
                return new TunnelResult(proxySocket, status, headers);
            }

            try (var body = exchange.responseBody()) {
                body.transferTo(OutputStream.nullOutputStream());
            }

            priorResponse = HttpResponse.create()
                    .setStatusCode(status)
                    .setHeaders(headers);

        } while (priorResponse.statusCode() == 407 && credentials != null);

        return new TunnelResult(null, priorResponse.statusCode(), priorResponse.headers());
    }

    private Socket performTlsHandshakeToProxy(Socket socket, ProxyConfiguration proxy) throws IOException {
        SSLSocket sslSocket = null;
        try {
            sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, proxy.hostname(), proxy.port(), true);
            sslSocket.setSSLParameters(socketParameters(sslSocket, null));

            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout(toIntMillis(tlsNegotiationTimeout));
            try {
                sslSocket.startHandshake();
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }

            return sslSocket;
        } catch (IOException e) {
            closeQuietly(sslSocket != null ? sslSocket : socket);
            throw new IOException("TLS handshake to HTTPS proxy " + proxy.hostname() + " failed", e);
        }
    }

    private List<InetAddress> resolve(String host, long exchangeId) throws IOException {
        notifyDnsStart(exchangeId, host);
        try {
            List<InetAddress> addresses = dnsResolver.resolve(host);
            // An empty result is a DNS failure indistinguishable from a thrown one — both mean no usable
            // address. Report it as such (onDnsEnd with an error) rather than firing a success event and
            // letting the caller discover emptiness afterwards.
            if (addresses.isEmpty()) {
                throw new IOException("DNS resolution failed: no addresses for " + host);
            }
            notifyDnsEnd(exchangeId, host, addresses, null);
            return addresses;
        } catch (IOException | RuntimeException e) {
            notifyDnsEnd(exchangeId, host, List.of(), e);
            throw e;
        }
    }

    private void notifyDnsStart(long exchangeId, String host) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onDnsStart(exchangeId, host);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onDnsStart", e);
                }
            }
        }
    }

    private void notifyDnsEnd(long exchangeId, String host, List<InetAddress> addresses, Throwable error) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onDnsEnd(exchangeId, host, addresses, error);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onDnsEnd", e);
                }
            }
        }
    }

    private void notifyConnectStart(long exchangeId, Route route, InetAddress address) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onConnectStart(exchangeId, route, address);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onConnectStart", e);
                }
            }
        }
    }

    private void notifyConnectEnd(long exchangeId, Route route, InetAddress address, Throwable error) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onConnectEnd(exchangeId, route, address, error);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onConnectEnd", e);
                }
            }
        }
    }

    private void notifyTlsStart(long exchangeId, Route route) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onTlsStart(exchangeId, route);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onTlsStart", e);
                }
            }
        }
    }

    private void notifyTlsEnd(long exchangeId, Route route, ConnectionTransport transport, Throwable error) {
        String cipherSuite = null;
        if (transport != null && transport.sslSession() != null) {
            cipherSuite = transport.sslSession().getCipherSuite();
        }
        notifyTlsEnd(exchangeId, route, transport == null ? null : transport.negotiatedProtocol(), cipherSuite, error);
    }

    private void notifyTlsEnd(
            long exchangeId,
            Route route,
            String protocol,
            String cipherSuite,
            Throwable error
    ) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onTlsEnd(exchangeId, route, protocol, cipherSuite, error);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onTlsEnd", e);
                }
            }
        }
    }

    private void notifyProxyConnectStart(long exchangeId, Route route, ProxyConfiguration proxy, InetAddress address) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onProxyConnectStart(exchangeId, route, proxy, address);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onProxyConnectStart", e);
                }
            }
        }
    }

    private void notifyProxyConnectEnd(
            long exchangeId,
            Route route,
            ProxyConfiguration proxy,
            InetAddress address,
            int statusCode,
            Throwable error
    ) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onProxyConnectEnd(exchangeId, route, proxy, address, statusCode, error);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onProxyConnectEnd", e);
                }
            }
        }
    }

    /**
     * Convert Duration to int milliseconds, clamping to Integer.MAX_VALUE to avoid overflow.
     */
    private static int toIntMillis(Duration d) {
        long ms = d.toMillis();
        return ms > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ms;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // ignored
        }
    }
}
