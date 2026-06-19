/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.boringssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import software.amazon.smithy.java.http.client.connection.ConnectionTransport;
import software.amazon.smithy.java.http.client.connection.SslEngineTransports;
import software.amazon.smithy.java.http.client.connection.TlsConnectionContext;
import software.amazon.smithy.java.http.client.connection.TlsProvider;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A {@link TlsProvider} backed by netty-tcnative's BoringSSL {@link SSLEngine}
 * ({@code ReferenceCountedOpenSslEngine}), whose AES-GCM (VAES/AVX-512 on modern x86-64) is markedly
 * cheaper than the JDK {@code SSLEngine}. The engine is a standard {@code javax.net.ssl.SSLEngine}, so
 * the connection runs on the built-in {@code SSLEngineTransport} (via {@link SslEngineTransports}) with
 * no Netty pipeline, event loop, or {@code SslHandler} — keeping the crypto win without the
 * per-connection pipeline overhead.
 *
 * <p>This is the only place {@code io.netty}/tcnative types appear in the HTTP client stack; it is
 * plugged in through the provider-neutral {@link TlsProvider} seam via
 * {@code HttpClient.Builder.tlsProvider(...)}.
 *
 * <h2>Engine lifecycle</h2>
 * The BoringSSL engine is reference-counted and holds off-heap memory, so each minted engine is paired
 * with a releaser that the transport invokes exactly once on connection close. While
 * {@code OpenSslEngine} also frees via a finalizer, explicit release avoids finalizer lag and GC
 * pressure under high connection churn.
 */
public final class BoringSslTlsProvider implements TlsProvider {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(BoringSslTlsProvider.class);

    static {
        // The BoringSSL engine ({@code ReferenceCountedOpenSslEngine}) tracks its pooled off-heap
        // buffers through Netty's leak detector, which defaults to SIMPLE: a sampled fraction of
        // every buffer allocate/release captures a Throwable stack trace. On this hot transport path
        // (driven by SSLEngineTransport, NOT the Netty pipeline) that costs CPU + per-record alloc
        // churn with no diagnostic value — and unlike the Netty transport's VtH1Transport, nothing
        // else on the smithy+BoringSSL path disables it. Disable unless the operator has explicitly
        // chosen a level via either Netty system property.
        if (System.getProperty("io.netty.leakDetection.level") == null
                && System.getProperty("io.netty.leakDetectionLevel") == null) {
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        }
    }

    private final boolean trustAll;
    // OpenSSL only negotiates ALPN when the protocol list is configured on the SslContext at build
    // time, but the list arrives per-engine. Contexts are immutable and reusable, so cache one per
    // distinct ALPN list (in practice a single client uses one list for its lifetime).
    private final ConcurrentHashMap<List<String>, SslContext> contextsByAlpn = new ConcurrentHashMap<>();

    /**
     * No-arg constructor for {@link java.util.ServiceLoader} discovery (production defaults:
     * {@code trustAll=false}). Selected via {@code -Dsmithy-java.tls-provider=software.amazon.smithy.java.client.http.boringssl.BoringSslTlsProvider}.
     */
    public BoringSslTlsProvider() {
        this(false);
    }

    private BoringSslTlsProvider(boolean trustAll) {
        this.trustAll = trustAll;
    }

    /**
     * Create a BoringSSL TLS provider for {@code HttpClient.Builder.tlsProvider(...)}.
     *
     * @param trustAll when true, trust all server certificates (benchmark/testing only — never in production)
     * @return a TLS provider backed by BoringSSL
     * @throws IllegalStateException if the native provider is unavailable
     */
    public static BoringSslTlsProvider create(boolean trustAll) {
        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException(
                    "netty-tcnative (BoringSSL) is unavailable: " + OpenSsl.unavailabilityCause());
        }
        return new BoringSslTlsProvider(trustAll);
    }

    /**
     * Whether the native BoringSSL provider is loadable on this host. When false, callers should fall
     * back to the JDK provider (do not construct this provider).
     *
     * @return true if netty-tcnative (BoringSSL) is available
     */
    @Override
    public boolean isAvailable() {
        return OpenSsl.isAvailable();
    }

    /**
     * @return true if netty-tcnative (BoringSSL) is loadable on this host.
     */
    public static boolean available() {
        return OpenSsl.isAvailable();
    }

    @Override
    public ConnectionTransport connect(TlsConnectionContext context) throws IOException {
        SSLEngine engine = newEngine(context.host(), context.port(), context.alpnProtocols());
        return SslEngineTransports.connect(context, engine, releaser(engine));
    }

    @Override
    public boolean supportsEpoll() {
        // Engine-based: SslEngineTransports consumes the internal epoll channel directly.
        return true;
    }

    private SslContext contextFor(List<String> alpnProtocols) {
        return contextsByAlpn.computeIfAbsent(alpnProtocols, protocols -> {
            try {
                var builder = SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL);
                if (trustAll) {
                    builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                }
                // netty-tcnative's OpenSSL engine only negotiates ALPN when the protocol list is configured
                // on the SslContext at build time; SSLParameters.setApplicationProtocols() on the engine
                // afterward is ignored by the OpenSSL provider. NO_ADVERTISE/ACCEPT is the standard client
                // ALPN behavior. An empty list builds a context with no ALPN.
                if (!protocols.isEmpty()) {
                    builder.applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            protocols.toArray(new String[0])));
                }
                return builder.build();
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to build BoringSSL client context", e);
            }
        });
    }

    // Mint a client-mode BoringSSL engine for host:port with the given ALPN list. Package-private so
    // low-level engine tests can exercise the engine directly; production goes through connect().
    SSLEngine newEngine(String host, int port, List<String> alpnProtocols) {
        // ALPN must be configured on the SslContext (see contextFor); pick the context matching this
        // call's protocol list. newEngine(alloc, host, port) returns a standard SSLEngine in
        // jdkCompatibilityMode (one TLS record per wrap, standard BUFFER_OVERFLOW semantics) — exactly
        // what SSLEngineTransport's wrap/unwrap loop expects.
        List<String> protocols = alpnProtocols == null ? List.of() : List.copyOf(alpnProtocols);
        SSLEngine engine = contextFor(protocols).newEngine(ByteBufAllocator.DEFAULT, host, port);
        engine.setUseClientMode(true);

        SSLParameters params = engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(params);
        return engine;
    }

    // The release callback for a minted engine: drops the off-heap reference count exactly once.
    static Runnable releaser(SSLEngine engine) {
        return () -> {
            try {
                ReferenceCountUtil.release(engine);
            } catch (RuntimeException e) {
                LOGGER.debug("Failed to release BoringSSL engine: {}", e.getMessage());
            }
        };
    }
}
