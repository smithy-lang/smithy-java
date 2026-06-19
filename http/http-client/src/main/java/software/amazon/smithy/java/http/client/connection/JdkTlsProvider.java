/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

/**
 * The built-in {@link TlsProvider} backed by the JDK's {@code javax.net.ssl} stack. It mints a
 * client-mode {@link SSLEngine} from a {@link SSLContext} (with optional {@link SSLParameters}) and
 * drives it through the {@code SSLEngineTransport}.
 *
 * <p>This is the default provider when none is selected. It is also the target of the convenience
 * {@code HttpClient.Builder.sslContext(...)} / {@code sslParameters(...)} setters: those build a
 * {@code JdkTlsProvider} implicitly, equivalent to selecting one explicitly via
 * {@code tlsProvider(JdkTlsProvider.builder().sslContext(...).sslParameters(...).build())}.
 */
public final class JdkTlsProvider implements TlsProvider {

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
        SSLEngine engine = createEngine(context.host(), context.port(), context.alpnProtocols());
        // The JDK engine holds no native resources, so there is nothing to release on close.
        return SslEngineTransports.connect(context, engine, null);
    }

    @Override
    public boolean supportsEpoll() {
        // Engine-based: SslEngineTransports consumes the internal epoll channel directly.
        return true;
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
    // user-supplied instance. Shared by the SSLSocket fast path in HttpConnectionFactory.
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
