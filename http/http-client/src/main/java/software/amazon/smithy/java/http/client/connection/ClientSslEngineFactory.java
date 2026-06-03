/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.util.List;
import javax.net.ssl.SSLEngine;

/**
 * Pluggable factory for the {@link SSLEngine} that drives TLS for a connection.
 *
 * <p>This is the seam that lets the blocking HTTP client use an alternate TLS provider — most
 * notably a native engine (e.g. BoringSSL via netty-tcnative) whose AES-GCM is markedly cheaper
 * than the JDK {@code SSLEngine} — <em>without</em> the {@code http-client} module taking any
 * dependency on that provider. An adapter module supplies the implementation; this module only
 * sees {@code javax.net.ssl} types.
 *
 * <p>When a factory is configured, every secure connection — HTTP/1.1 included — is driven through
 * {@link SSLEngineTransport} (the ByteBuffer-based {@code SSLEngine} driver), rather than the JDK
 * {@code SSLSocket} path. The factory mints a fresh engine per connection and, because some native
 * engines are reference-counted and hold off-heap memory, also hands back a {@linkplain Handle#releaser()
 * releaser} that the transport invokes exactly once when the connection closes.
 */
@FunctionalInterface
public interface ClientSslEngineFactory {

    /**
     * Mint a client-mode {@link SSLEngine} for a connection to {@code host:port}.
     *
     * <p>Implementations must configure client mode, endpoint identification ({@code "HTTPS"}), and
     * the supplied ALPN protocols, mirroring the JDK default path so behavior is identical apart
     * from the provider.
     *
     * @param host peer host (for SNI / endpoint identification)
     * @param port peer port
     * @param alpnProtocols ALPN protocols to advertise (e.g. {@code ["http/1.1"]}); never null
     * @return a handle carrying the engine and its release callback
     */
    Handle newEngine(String host, int port, List<String> alpnProtocols);

    /**
     * An {@link SSLEngine} paired with a release callback. The {@code releaser} frees any
     * provider-native resources (a no-op for the JDK engine) and is invoked exactly once by the
     * owning {@link SSLEngineTransport} on close — including error/early-close paths.
     *
     * @param engine the configured client engine
     * @param releaser idempotent release callback; never null (use {@code () -> {}} when nothing to free)
     */
    record Handle(SSLEngine engine, Runnable releaser) {
        public Handle {
            if (engine == null) {
                throw new IllegalArgumentException("engine must not be null");
            }
            if (releaser == null) {
                releaser = () -> {};
            }
        }
    }
}
