/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
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
        HttpVersionPolicy versionPolicy,
        DnsResolver dnsResolver,
        HttpSocketFactory socketFactory,
        boolean usePlatformReaderForH2,
        int h2InitialWindowSize,
        int h2MaxFrameSize,
        int h2BufferSize) {
    /**
     * Create a new connection to the given route.
     *
     * @param route the route to connect to
     * @return a new HttpConnection
     * @throws IOException if connection fails
     */
    HttpConnection create(Route route) throws IOException {
        if (route.usesProxy()) {
            return connectViaProxy(route);
        }

        List<InetAddress> addresses = dnsResolver.resolve(route.host());
        if (addresses.isEmpty()) {
            throw new IOException("DNS resolution failed: no addresses for " + route.host());
        }

        IOException lastException = null;
        for (InetAddress address : addresses) {
            try {
                return connectToAddress(address, route, addresses);
            } catch (IOException e) {
                lastException = e;
                dnsResolver.reportFailure(address);
            }
        }

        throw new IOException(
                "Failed to connect to " + route.host() + " on any resolved IP (" + addresses.size() + " tried)",
                lastException);
    }

    private HttpConnection connectToAddress(InetAddress address, Route route, List<InetAddress> allEndpoints)
            throws IOException {
        Socket socket = socketFactory.newSocket(route, allEndpoints);

        try {
            socket.connect(new InetSocketAddress(address, route.port()), toIntMillis(connectTimeout));
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }

        ConnectionTransport transport;
        if (route.isSecure()) {
            transport = versionPolicy == HttpVersionPolicy.ENFORCE_HTTP_1_1
                    ? performTlsSocketHandshake(socket, route)
                    : performTlsHandshake(socket, route);
        } else {
            transport = ConnectionTransport.of(socket);
        }

        return createProtocolConnection(transport, route);
    }

    private ConnectionTransport performTlsHandshake(Socket socket, Route route) throws IOException {
        try {
            SSLEngine engine = createClientEngine(route);

            int originalTimeout = socket.getSoTimeout();
            socket.setSoTimeout(toIntMillis(tlsNegotiationTimeout));
            try {
                SSLEngineTransport transport = new SSLEngineTransport(socket, engine);
                transport.handshake();
                return transport;
            } finally {
                socket.setSoTimeout(originalTimeout);
            }
        } catch (IOException e) {
            closeQuietly(socket);
            throw new IOException("TLS handshake failed for " + route.host(), e);
        }
    }

    private ConnectionTransport performTlsSocketHandshake(Socket socket, Route route) throws IOException {
        SSLSocket sslSocket = null;
        try {
            sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, route.host(), route.port(), true);
            sslSocket.setSSLParameters(socketParameters(sslSocket, versionPolicy.alpnProtocols()));

            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout(toIntMillis(tlsNegotiationTimeout));
            try {
                sslSocket.startHandshake();
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }

            return ConnectionTransport.of(sslSocket);
        } catch (IOException e) {
            closeQuietly(sslSocket != null ? sslSocket : socket);
            throw new IOException("TLS handshake failed for " + route.host(), e);
        }
    }

    private SSLEngine createClientEngine(Route route) {
        SSLEngine engine = sslContext.createSSLEngine(route.host(), route.port());
        engine.setUseClientMode(true);

        SSLParameters params = sslParameters != null
                ? copyParameters(sslParameters)
                : engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        params.setApplicationProtocols(versionPolicy.alpnProtocols());
        engine.setSSLParameters(params);
        return engine;
    }

    private SSLParameters socketParameters(SSLSocket sslSocket, String[] applicationProtocols) {
        SSLParameters params = sslParameters != null
                ? copyParameters(sslParameters)
                : sslSocket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        if (applicationProtocols != null) {
            params.setApplicationProtocols(applicationProtocols);
        }
        return params;
    }

    private static SSLParameters copyParameters(SSLParameters src) {
        SSLParameters dst = new SSLParameters();
        dst.setCipherSuites(src.getCipherSuites());
        dst.setProtocols(src.getProtocols());
        dst.setWantClientAuth(src.getWantClientAuth());
        dst.setNeedClientAuth(src.getNeedClientAuth());
        dst.setAlgorithmConstraints(src.getAlgorithmConstraints());
        dst.setEndpointIdentificationAlgorithm(src.getEndpointIdentificationAlgorithm());
        dst.setServerNames(src.getServerNames());
        dst.setSNIMatchers(src.getSNIMatchers());
        dst.setUseCipherSuitesOrder(src.getUseCipherSuitesOrder());
        dst.setEnableRetransmissions(src.getEnableRetransmissions());
        dst.setMaximumPacketSize(src.getMaximumPacketSize());
        dst.setApplicationProtocols(src.getApplicationProtocols());
        return dst;
    }

    enum Protocol { H1, H2 }

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
                                usePlatformReaderForH2,
                                h2InitialWindowSize,
                                h2MaxFrameSize,
                                h2BufferSize);
    }

    private HttpConnection connectViaProxy(Route route) throws IOException {
        ProxyConfiguration proxy = route.proxy();

        if (proxy.type() == ProxyConfiguration.ProxyType.SOCKS4
                || proxy.type() == ProxyConfiguration.ProxyType.SOCKS5) {
            throw new UnsupportedOperationException("SOCKS proxies not yet supported: " + proxy.type());
        }

        List<InetAddress> proxyAddresses = dnsResolver.resolve(proxy.hostname());
        if (proxyAddresses.isEmpty()) {
            throw new IOException("DNS resolution failed for proxy: " + proxy.hostname());
        }

        IOException lastException = null;
        for (InetAddress proxyAddress : proxyAddresses) {
            try {
                return connectToProxy(proxyAddress, route, proxy, proxyAddresses);
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
            List<InetAddress> allProxyEndpoints
    ) throws IOException {
        Socket proxySocket = socketFactory.newSocket(route, allProxyEndpoints);

        try {
            proxySocket.connect(new InetSocketAddress(proxyAddress, proxy.port()), toIntMillis(connectTimeout));

            // Connect to the proxy over TLS if the scheme is https
            if ("https".equalsIgnoreCase(proxy.proxyUri().getScheme())) {
                // Use SSLSocket for proxy TLS (proxy tunnel doesn't need zero-copy)
                proxySocket = performTlsHandshakeToProxy(proxySocket, proxy);
            }

            if (route.isSecure()) {
                var result = establishTunnel(
                        proxySocket,
                        route.host(),
                        route.port(),
                        proxy.credentials(),
                        readTimeout);

                if (result.statusCode() != 200) {
                    closeQuietly(proxySocket);
                    throw new IOException("Proxy CONNECT failed: " + result.statusCode());
                }

                ConnectionTransport transport = versionPolicy == HttpVersionPolicy.ENFORCE_HTTP_1_1
                        ? performTlsSocketHandshake(proxySocket, route)
                        : performTlsHandshake(proxySocket, route);
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
            exchange.requestBody().close();

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
