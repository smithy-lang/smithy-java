/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.util.Objects;
import software.amazon.smithy.java.http.client.ProxyConfiguration;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * A route represents a unique destination for HTTP connections.
 *
 * <p>Connections to the same route can be pooled and reused. Two routes are equal if they connect to the same
 * destination via the same path (including proxy configuration).
 *
 * <p><b>Important:</b> Routes are compared by value, not identity. Two Route instances with the same scheme, host,
 * port, and proxy configuration are considered equal and will share connections.
 */
public final class Route {
    private final String scheme;
    private final String host;
    private final int port;
    private final ProxyConfiguration proxy;
    private final int cachedHashCode;
    private final String authority;

    private Route(String scheme, String host, int port, ProxyConfiguration proxy) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host cannot be blank or null");
        } else if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Invalid scheme: " + scheme + " (must be 'http' or 'https')");
        }

        int defaultPort = "https".equals(scheme) ? 443 : 80;
        if (port == -1) {
            port = defaultPort;
        } else if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port + " (must be 1-65535)");
        }

        // Normalize host to lowercase for consistent equality
        this.scheme = scheme;
        this.host = host.toLowerCase();
        this.port = port;
        this.proxy = proxy;
        this.authority = (port == defaultPort) ? this.host : this.host + ":" + port;

        // Cache hashCode for fast map lookups in connection pool
        int h = this.scheme.hashCode();
        h = 31 * h + this.host.hashCode();
        h = 31 * h + this.port;
        h = 31 * h + (this.proxy != null ? this.proxy.hashCode() : 0);
        this.cachedHashCode = h;
    }

    /**
     * @return the scheme ("http" or "https")
     */
    public String scheme() {
        return scheme;
    }

    /**
     * @return the target hostname (normalized to lowercase)
     */
    public String host() {
        return host;
    }

    /**
     * @return the target port
     */
    public int port() {
        return port;
    }

    /**
     * Get the authority (host:port or just host for default ports).
     *
     * @return the authority string
     */
    public String authority() {
        return authority;
    }

    /**
     * @return the proxy configuration, or null if direct connection
     */
    public ProxyConfiguration proxy() {
        return proxy;
    }

    /**
     * Check if this is a secure (HTTPS) route.
     *
     * @return true if scheme is "https"
     */
    public boolean isSecure() {
        return "https".equals(scheme);
    }

    /**
     * Check if this route goes through a proxy.
     *
     * @return true if proxy configuration is present
     */
    public boolean usesProxy() {
        return proxy != null;
    }

    /**
     * Create a Route from a URI without proxy.
     *
     * <p>The URI's path, query, and fragment are ignored.
     * Only scheme, host, and port are used.
     *
     * @param uri the URI to extract route from
     * @return a Route for direct connection
     * @throws IllegalArgumentException if URI is invalid
     */
    public static Route from(SmithyUri uri) {
        return from(uri, null);
    }

    /**
     * Create a Route from a URI with optional proxy configuration.
     *
     * <p>The URI's path, query, and fragment are ignored. Only scheme, host, and port are used.
     *
     * @param uri the URI to extract route from
     * @param proxy optional proxy configuration (null for direct connection)
     * @return a Route for the given URI and proxy
     * @throws IllegalArgumentException if URI is invalid
     */
    public static Route from(SmithyUri uri, ProxyConfiguration proxy) {
        return new Route(uri.getScheme(), uri.getHost(), uri.getPort(), proxy);
    }

    /**
     * Create a Route for direct connection (no proxy).
     *
     * @param scheme "http" or "https"
     * @param host target hostname
     * @param port target port
     * @return a Route for direct connection
     */
    public static Route direct(String scheme, String host, int port) {
        return new Route(scheme, host, port, null);
    }

    /**
     * Create a Route through a proxy.
     *
     * @param scheme "http" or "https"
     * @param host target hostname
     * @param port target port
     * @param proxy proxy configuration
     * @return a Route for proxied connection
     */
    public static Route viaProxy(String scheme, String host, int port, ProxyConfiguration proxy) {
        Objects.requireNonNull(proxy, "proxy cannot be null (use direct() for no proxy)");
        return new Route(scheme, host, port, proxy);
    }

    /**
     * Create a new Route with a different proxy configuration.
     *
     * @param proxy new proxy configuration (null for direct connection)
     * @return new Route with updated proxy
     */
    public Route withProxy(ProxyConfiguration proxy) {
        return Objects.equals(this.proxy, proxy) ? this : new Route(scheme, host, port, proxy);
    }

    /**
     * Create a new Route without proxy (direct connection).
     *
     * @return new Route with no proxy
     */
    public Route withoutProxy() {
        return proxy == null ? this : new Route(scheme, host, port, null);
    }

    @Override
    public int hashCode() {
        return cachedHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Route other)) {
            return false;
        }
        return port == other.port
                && scheme.equals(other.scheme)
                && host.equals(other.host)
                && Objects.equals(proxy, other.proxy);
    }

    @Override
    public String toString() {
        return "Route[scheme=" + scheme + ", host=" + host + ", port=" + port + ", proxy=" + proxy + "]";
    }
}
