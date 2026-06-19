/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/**
 * The built-in {@link TlsProvider} backed by the JDK's {@code javax.net.ssl} stack.
 *
 * <p>It uses two strategies, both producing a standard {@link ConnectionTransport}:
 * <ul>
 *   <li><b>SSLSocket</b> for an HTTP/1.1-only connection on a plain socket (no epoll): a blocking
 *       {@link SSLSocket} read/written directly, which is cheaper than the {@code SSLEngine}
 *       wrap/unwrap loop and is the common case for HTTP/1.1 services.</li>
 *   <li><b>SSLEngine</b> (via {@link SslEngineTransports}) otherwise — HTTP/2 / ALPN negotiation, or
 *       the epoll backend, where the engine drives TLS over the byte channel.</li>
 * </ul>
 *
 * <p>This is the default provider when none is selected, and the target of the convenience
 * {@code HttpClient.Builder.sslContext(...)} / {@code sslParameters(...)} setters: those build a
 * {@code JdkTlsProvider} implicitly, equivalent to selecting one explicitly via
 * {@code tlsProvider(JdkTlsProvider.builder().sslContext(...).sslParameters(...).build())}.
 */
public final class JdkTlsProvider implements TlsProvider {

    private static final List<String> HTTP1_ONLY = List.of("http/1.1");

    private final SSLContext sslContext;
    private final SSLParameters sslParameters;

    private JdkTlsProvider(Builder builder) {
        this.sslContext = builder.sslContext != null ? builder.sslContext : defaultContext();
        this.sslParameters = builder.sslParameters;
    }

    /**
     * @return a JDK provider using the default {@link SSLContext} and no custom parameters.
     */
    public static JdkTlsProvider create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ConnectionTransport connect(TlsConnectionContext context) throws IOException {
        // SSLSocket fast path: HTTP/1.1-only over a plain socket (the epoll backend has no socket and is
        // driven through the engine instead). Cheaper than the SSLEngine wrap/unwrap loop.
        if (context.socket() != null && HTTP1_ONLY.equals(context.alpnProtocols())) {
            return connectSslSocket(context);
        }
        // Engine creation can fail before SslEngineTransports.connect takes ownership of the substrate
        // (e.g. invalid SSLParameters -> IllegalArgumentException). The TlsProvider contract requires
        // releasing the supplied socket/channel on failure, so close it before rethrowing.
        SSLEngine engine;
        try {
            engine = createEngine(context.host(), context.port(), context.alpnProtocols());
        } catch (RuntimeException e) {
            closeSubstrate(context);
            throw e;
        }
        // The JDK engine holds no native resources, so there is nothing to release on close.
        return SslEngineTransports.connect(context, engine, null);
    }

    // Close whichever transport substrate the context carries (socket or internal epoll channel),
    // honoring the TlsProvider contract that connect() releases it on failure.
    private static void closeSubstrate(TlsConnectionContext context) {
        if (context.socket() != null) {
            closeQuietly(context.socket());
        } else if (context.epollChannel() != null) {
            context.epollChannel().close();
        }
    }

    @Override
    public boolean supportsEpoll() {
        // Engine-based path: SslEngineTransports consumes the internal epoll channel directly.
        return true;
    }

    private ConnectionTransport connectSslSocket(TlsConnectionContext context) throws IOException {
        var socket = context.socket();
        SSLSocket sslSocket = null;
        try {
            sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, context.host(), context.port(), true);

            SSLParameters params = sslParameters != null ? copyParameters(sslParameters)
                    : sslSocket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            params.setApplicationProtocols(context.alpnProtocols().toArray(new String[0]));
            sslSocket.setSSLParameters(params);

            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout(context.negotiationTimeoutMillis());
            try {
                sslSocket.startHandshake();
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }
            return ConnectionTransport.of(sslSocket);
        } catch (IOException e) {
            closeQuietly(sslSocket != null ? sslSocket : socket);
            throw new IOException("TLS handshake failed for " + context.host(), e);
        } catch (RuntimeException e) {
            // e.g. invalid SSLParameters from setSSLParameters; release the socket before rethrowing.
            closeQuietly(sslSocket != null ? sslSocket : socket);
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

    private SSLEngine createEngine(String host, int port, List<String> alpnProtocols) {
        SSLEngine engine = sslContext.createSSLEngine(host, port);
        engine.setUseClientMode(true);

        SSLParameters params = sslParameters != null ? copyParameters(sslParameters) : engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        params.setApplicationProtocols(alpnProtocols.toArray(new String[0]));
        engine.setSSLParameters(params);
        return engine;
    }

    private static SSLContext defaultContext() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to get default SSLContext", e);
        }
    }

    // Deep-copy SSLParameters so per-connection mutation (endpoint id, ALPN) does not alter the shared
    // user-supplied instance. Used by both the SSLSocket and SSLEngine strategies here, and by the
    // factory's proxy-leg TLS so all JDK TLS paths copy the same full set of fields.
    static SSLParameters copyParameters(SSLParameters src) {
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

    public static final class Builder {
        private SSLContext sslContext;
        private SSLParameters sslParameters;

        private Builder() {}

        /**
         * @param sslContext the SSL context, or null to use {@link SSLContext#getDefault()}
         * @return this builder
         */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * @param sslParameters custom parameters (cipher suites, protocols, SNI, ...), or null for the
         *     context defaults
         * @return this builder
         */
        public Builder sslParameters(SSLParameters sslParameters) {
            this.sslParameters = sslParameters;
            return this;
        }

        public JdkTlsProvider build() {
            return new JdkTlsProvider(this);
        }
    }
}
