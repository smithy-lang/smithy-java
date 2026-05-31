/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.boringssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import software.amazon.smithy.java.http.client.connection.ClientSslEngineFactory;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A {@link ClientSslEngineFactory} backed by netty-tcnative's BoringSSL {@link SSLEngine}
 * ({@code ReferenceCountedOpenSslEngine}), whose AES-GCM (VAES/AVX-512 on modern x86-64) is markedly
 * cheaper than the JDK {@code SSLEngine}. The engine is a standard {@code javax.net.ssl.SSLEngine},
 * so the {@code http-client} {@link software.amazon.smithy.java.http.client.connection.SSLEngineTransport}
 * drives it with no Netty pipeline, event loop, or {@code SslHandler} — keeping the crypto win
 * without the per-connection pipeline overhead.
 *
 * <p>This is the only place {@code io.netty}/tcnative types appear in the HTTP client stack; the
 * factory is injected through the provider-agnostic {@link ClientSslEngineFactory} seam.
 *
 * <h2>Engine lifecycle</h2>
 * The BoringSSL engine is reference-counted and holds off-heap memory, so each minted engine is
 * paired with a {@code releaser} that the transport invokes exactly once on connection close. While
 * {@code OpenSslEngine} also frees via a finalizer, explicit release avoids finalizer lag and GC
 * pressure under high connection churn.
 */
public final class BoringSslEngineFactory implements ClientSslEngineFactory {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(BoringSslEngineFactory.class);

    private final SslContext sslContext;

    private BoringSslEngineFactory(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Whether the native BoringSSL provider is loadable on this host. When false, callers should
     * fall back to the JDK provider (do not construct this factory).
     */
    public static boolean isAvailable() {
        return OpenSsl.isAvailable();
    }

    /**
     * Create a factory using the BoringSSL provider.
     *
     * @param trustAll when true, trust all server certificates (benchmark/testing only — never in production)
     * @return a new factory
     * @throws IllegalStateException if the native provider is unavailable or context build fails
     */
    public static BoringSslEngineFactory create(boolean trustAll) {
        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException(
                    "netty-tcnative (BoringSSL) is unavailable: " + String.valueOf(OpenSsl.unavailabilityCause()));
        }
        try {
            var builder = SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL);
            if (trustAll) {
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            }
            return new BoringSslEngineFactory(builder.build());
        } catch (SSLException e) {
            throw new IllegalStateException("Failed to build BoringSSL client context", e);
        }
    }

    @Override
    public Handle newEngine(String host, int port, List<String> alpnProtocols) {
        // newEngine(alloc, host, port) returns a standard SSLEngine in jdkCompatibilityMode (one TLS
        // record per wrap, standard BUFFER_OVERFLOW semantics) — exactly what SSLEngineTransport's
        // wrap/unwrap loop expects. ALPN/endpoint-identification are set via SSLParameters to mirror
        // the JDK default path so behavior is identical apart from the provider.
        SSLEngine engine = sslContext.newEngine(ByteBufAllocator.DEFAULT, host, port);
        engine.setUseClientMode(true);

        SSLParameters params = engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        if (alpnProtocols != null && !alpnProtocols.isEmpty()) {
            params.setApplicationProtocols(alpnProtocols.toArray(new String[0]));
        }
        engine.setSSLParameters(params);

        return new Handle(engine, () -> releaseEngine(engine));
    }

    private static void releaseEngine(SSLEngine engine) {
        try {
            ReferenceCountUtil.release(engine);
        } catch (RuntimeException e) {
            LOGGER.debug("Failed to release BoringSSL engine: {}", e.getMessage());
        }
    }
}
