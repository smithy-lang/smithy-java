/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Pluggable TLS provider: turns a freshly connected (plaintext) socket into a handshaken,
 * ready-to-use {@link ConnectionTransport}.
 *
 * <p>This is the provider-neutral seam for selecting a TLS implementation. A provider need not be based
 * on a {@code javax.net.ssl.SSLEngine}: one that performs TLS some other way (for example a native
 * stack that does its own socket I/O) simply returns its own {@code ConnectionTransport}. Engine-based
 * providers (the built-in JDK provider, BoringSSL) build the standard transport via
 * {@link SslEngineTransports}; an out-of-module provider may return any {@code ConnectionTransport}.
 *
 * <p>The provider owns the handshake: {@link #connect} returns only after TLS negotiation has
 * succeeded, and the returned transport is positioned for application I/O. On failure the provider
 * must release any resources it allocated (including the supplied socket/channel) before throwing.
 *
 * <h2>Discovery (opt-in)</h2>
 * Providers may be registered for {@link ServiceLoader} (e.g. the BoringSSL module ships a
 * {@code META-INF/services} entry). Discovery is <b>opt-in</b>: a registered provider is engaged only
 * when the system property {@value #PROVIDER_PROPERTY} names its fully-qualified class name. Merely
 * having a provider on the classpath changes nothing — the built-in JDK provider remains the default —
 * and an explicit {@code HttpClient.Builder.tlsProvider(...)} always takes precedence over the property.
 */
@FunctionalInterface
public interface TlsProvider {

    /**
     * System property selecting a discovered provider by fully-qualified class name, e.g.
     * {@code -Dsmithy-java.tls-provider=software.amazon.smithy.java.client.http.boringssl.BoringSslTlsProvider}.
     */
    String PROVIDER_PROPERTY = "smithy-java.tls-provider";

    /**
     * Perform the TLS handshake over the connection described by {@code connection} and return a
     * handshaken transport.
     *
     * <p>The provider takes ownership of the connection's underlying socket/channel: on success the
     * returned transport owns it (and frees it on {@link ConnectionTransport#close()}); on failure the
     * provider releases it before throwing.
     *
     * @param connection the connected (plaintext) endpoint plus negotiation parameters
     * @return a handshaken transport ready for application reads/writes
     * @throws IOException if the connection or handshake fails
     */
    ConnectionTransport connect(TlsConnectionContext connection) throws IOException;

    /**
     * Whether this provider is usable in the current runtime. A provider backed by a native library
     * that failed to load should report {@code false} so callers can fall back to the JDK provider.
     *
     * @return true if the provider can establish connections
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Whether this provider can drive a connection over the client's internal epoll transport.
     *
     * <p>The epoll backend hands the provider a {@link TlsConnectionContext} whose
     * {@link TlsConnectionContext#socket()} is {@code null}; the underlying byte channel is internal and
     * is consumable only through {@link SslEngineTransports} (i.e. by engine-based providers). A provider
     * that does its own socket I/O therefore needs a real {@code socket()} and must return {@code false}
     * — the default — so the client uses the NIO socket path for it. Built-in engine-based providers
     * (JDK, BoringSSL) return {@code true} since they delegate to {@code SslEngineTransports}.
     *
     * @return true if the provider supports the internal epoll transport (and thus a null {@code socket()})
     */
    default boolean supportsEpoll() {
        return false;
    }

    /**
     * Resolve the provider selected by {@value #PROVIDER_PROPERTY}, if set.
     *
     * <p>When the property is set, the {@link ServiceLoader}-discovered provider whose class has the
     * named fully-qualified name is returned, provided it is {@link #isAvailable() available}. Returns
     * null when the property is unset (the caller should use its default, typically the JDK provider).
     *
     * @return the selected provider, or null if the property is unset
     * @throws IllegalStateException if the property names a provider that is not discoverable, or is
     *     discovered but reports unavailable
     */
    static TlsProvider fromSystemProperty() {
        String fqcn = System.getProperty(PROVIDER_PROPERTY);
        if (fqcn == null || fqcn.isBlank()) {
            return null;
        }
        // Pass null so byClassName uses TlsProvider's own loader (plus the thread-context loader as a
        // fallback). The interface's loader always sees a provider on the module path, including under
        // GraalVM native-image where the thread-context loader can be null.
        return byClassName(fqcn.trim(), null);
    }

    /**
     * Find the {@link ServiceLoader}-registered {@code TlsProvider} whose class has the given
     * fully-qualified name and is available.
     *
     * <p>Discovery is by {@link ServiceLoader} only (never reflective {@code Class.forName}), so it is
     * GraalVM native-image safe: registered providers are matched by their already-loaded class name.
     * Both this interface's class loader and the thread-context loader (when distinct and non-null) are
     * searched, so the lookup works whether or not a context loader is set.
     *
     * @param fqcn fully-qualified class name of the desired provider
     * @param classLoader class loader to discover services with; when null, this interface's loader is
     *     used
     * @return the matching, available provider
     * @throws IllegalStateException if no such provider is registered, or it is unavailable
     */
    static TlsProvider byClassName(String fqcn, ClassLoader classLoader) {
        ClassLoader primary = classLoader != null ? classLoader : TlsProvider.class.getClassLoader();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

        List<String> discovered = new ArrayList<>();
        TlsProvider match = findIn(primary, fqcn, discovered);
        if (match == null && contextLoader != null && contextLoader != primary) {
            match = findIn(contextLoader, fqcn, discovered);
        }
        if (match == null) {
            throw new IllegalStateException(
                    "No TLS provider registered with class name '" + fqcn + "' (from " + PROVIDER_PROPERTY
                            + "). Discovered providers: " + discovered);
        }
        if (!match.isAvailable()) {
            throw new IllegalStateException(
                    "TLS provider '" + fqcn + "' (from " + PROVIDER_PROPERTY + ") is registered but reports "
                            + "unavailable in this runtime (e.g. its native library failed to load).");
        }
        return match;
    }

    private static TlsProvider findIn(ClassLoader loader, String fqcn, List<String> discovered) {
        for (TlsProvider provider : ServiceLoader.load(TlsProvider.class, loader)) {
            String name = provider.getClass().getName();
            discovered.add(name);
            if (name.equals(fqcn)) {
                return provider;
            }
        }
        return null;
    }
}
