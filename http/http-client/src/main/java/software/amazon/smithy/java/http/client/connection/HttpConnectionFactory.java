/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.client.ProxyConfiguration;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.h1.H1Connection;
import software.amazon.smithy.java.http.client.h2.H2Connection;

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
 */
final class HttpConnectionFactory {

    private final Duration connectTimeout;
    private final Duration tlsNegotiationTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final SSLContext sslContext;
    private final HttpVersionPolicy versionPolicy;
    private final DnsResolver dnsResolver;
    private final HttpSocketFactory socketFactory;

    HttpConnectionFactory(
            Duration connectTimeout,
            Duration tlsNegotiationTimeout,
            Duration readTimeout,
            Duration writeTimeout,
            SSLContext sslContext,
            HttpVersionPolicy versionPolicy,
            DnsResolver dnsResolver,
            HttpSocketFactory socketFactory
    ) {
        this.connectTimeout = connectTimeout;
        this.tlsNegotiationTimeout = tlsNegotiationTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.sslContext = sslContext;
        this.versionPolicy = versionPolicy;
        this.dnsResolver = dnsResolver;
        this.socketFactory = socketFactory;
    }

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
            socket.connect(new InetSocketAddress(address, route.port()), (int) connectTimeout.toMillis());
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }

        if (route.isSecure()) {
            socket = performTlsHandshake(socket, route);
        }

        return createProtocolConnection(socket, route);
    }

    private Socket performTlsHandshake(Socket socket, Route route) throws IOException {
        try {
            SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, route.host(), route.port(), true);

            SSLParameters params = sslSocket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            params.setApplicationProtocols(versionPolicy.alpnProtocols());
            sslSocket.setSSLParameters(params);

            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout((int) tlsNegotiationTimeout.toMillis());
            try {
                sslSocket.startHandshake();
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }

            return sslSocket;
        } catch (IOException e) {
            closeQuietly(socket);
            throw new IOException("TLS handshake failed for " + route.host(), e);
        }
    }

    private HttpConnection createProtocolConnection(Socket socket, Route route) throws IOException {
        String protocol = "http/1.1";

        if (socket instanceof SSLSocket sslSocket) {
            String negotiated = sslSocket.getApplicationProtocol();
            if (negotiated != null && !negotiated.isEmpty()) {
                protocol = negotiated;
            }
        } else if (versionPolicy.usesH2cForCleartext()) {
            protocol = "h2c";
        }

        try {
            if ("h2".equals(protocol) || "h2c".equals(protocol)) {
                return new H2Connection(socket, route, readTimeout, writeTimeout);
            } else {
                return new H1Connection(socket, route, readTimeout);
            }
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
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
            proxySocket.connect(
                    new InetSocketAddress(proxyAddress, proxy.port()),
                    (int) connectTimeout.toMillis());

            if (proxy.type() == ProxyConfiguration.ProxyType.HTTPS) {
                proxySocket = performTlsHandshakeToProxy(proxySocket, proxy);
            }

            if (route.isSecure()) {
                H1Connection.establishConnectTunnel(proxySocket, route.host(), route.port(), proxy);
                proxySocket = performTlsHandshake(proxySocket, route);
            }

            return createProtocolConnection(proxySocket, route);
        } catch (IOException e) {
            closeQuietly(proxySocket);
            throw new IOException(
                    "Failed to connect to " + route.host() + " via proxy " +
                            proxy.hostname() + ":" + proxy.port() + " (" + proxyAddress.getHostAddress() + ")",
                    e);
        }
    }

    private Socket performTlsHandshakeToProxy(Socket socket, ProxyConfiguration proxy) throws IOException {
        try {
            SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, proxy.hostname(), proxy.port(), true);

            SSLParameters params = sslSocket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(params);

            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout((int) tlsNegotiationTimeout.toMillis());
            try {
                sslSocket.startHandshake();
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }

            return sslSocket;
        } catch (IOException e) {
            throw new IOException("TLS handshake to HTTPS proxy " + proxy.hostname() + " failed", e);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // ignored
        }
    }
}
