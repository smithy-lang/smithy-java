/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.util.List;
import javax.net.ssl.SSLException;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Builds and caches the {@link SslContext} used by the virtual-thread-blocking transport, and
 * mints per-connection {@link SslHandler}s from it.
 *
 * <p>This is the TLS provider seam. It prefers netty-tcnative (BoringSSL) — selected via
 * {@link SslProvider#OPENSSL} — for its faster AES-GCM, and transparently falls back to the JDK
 * {@link SslProvider#JDK} provider when the native library is unavailable on the host. The chosen
 * provider is fixed at construction so every connection on a given transport uses the same TLS
 * stack.
 *
 * <p>The {@code SslHandler}s minted here are driven synchronously inside an
 * {@link io.netty.channel.embedded.EmbeddedChannel} on the calling virtual thread (no event loop),
 * so the handshake timeout — which {@code SslHandler} implements as a <em>scheduled</em> task that
 * an embedded loop never fires — is disabled here; the connection relies on the socket read timeout
 * instead. Delegated TLS tasks run inline because {@code SslContext.newHandler} uses an immediate
 * executor by default.
 */
public final class VtTlsContext {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(VtTlsContext.class);

    private final SslContext sslContext;
    private final SslProvider provider;

    private VtTlsContext(SslContext sslContext, SslProvider provider) {
        this.sslContext = sslContext;
        this.provider = provider;
    }

    /**
     * Build a client TLS context.
     *
     * @param preferOpenSsl when true, use netty-tcnative (BoringSSL) if available; otherwise JDK.
     * @param trustAll when true, trust all server certificates (benchmark/testing only).
     * @param alpnProtocols ALPN protocols to advertise (e.g. {@code ["http/1.1"]}), or null for none.
     */
    public static VtTlsContext create(boolean preferOpenSsl, boolean trustAll, List<String> alpnProtocols) {
        SslProvider chosen = preferOpenSsl && OpenSsl.isAvailable() ? SslProvider.OPENSSL : SslProvider.JDK;
        if (preferOpenSsl && chosen == SslProvider.JDK) {
            LOGGER.info("netty-tcnative (BoringSSL) requested but unavailable; falling back to JDK SSLEngine: {}",
                    String.valueOf(OpenSsl.unavailabilityCause()));
        }
        try {
            var builder = SslContextBuilder.forClient().sslProvider(chosen);
            if (trustAll) {
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            }
            if (alpnProtocols != null && !alpnProtocols.isEmpty()) {
                // NO_ADVERTISE / ACCEPT keeps a single deterministic protocol selection for the
                // blocking pump; for H1-only the list is just ["http/1.1"].
                builder.ciphers(null, SupportedCipherSuiteFilter.INSTANCE)
                        .applicationProtocolConfig(new ApplicationProtocolConfig(
                                ApplicationProtocolConfig.Protocol.ALPN,
                                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                alpnProtocols));
            }
            return new VtTlsContext(builder.build(), chosen);
        } catch (SSLException e) {
            throw new IllegalStateException("Failed to build SSL context (provider=" + chosen + ")", e);
        }
    }

    /**
     * Mint a new {@link SslHandler} for a connection to the given host/port, configured for the
     * synchronous blocking pump (handshake timeout disabled — see class javadoc).
     */
    public SslHandler newHandler(ByteBufAllocator alloc, String host, int port) {
        SslHandler handler = sslContext.newHandler(alloc, host, port);
        // The embedded event loop never advances time, so a scheduled handshake-timeout task would
        // never fire (or worse, leak). The socket read timeout bounds the handshake instead.
        handler.setHandshakeTimeoutMillis(0L);
        return handler;
    }

    /** @see #newHandler(ByteBufAllocator, String, int) */
    public SslHandler newHandler(Channel channel, String host, int port) {
        return newHandler(channel.alloc(), host, port);
    }

    /** The TLS provider actually in use (OPENSSL or JDK). */
    public SslProvider provider() {
        return provider;
    }

    /** True when the native BoringSSL provider is in use. */
    public boolean isOpenSsl() {
        return provider == SslProvider.OPENSSL;
    }
}
