/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Proxy configuration for HTTP connections.
 *
 * <p>Supports HTTP and SOCKS proxies with optional authentication.
 *
 * @param proxyUri Proxy server URI.
 * @param type Type of proxy.
 * @param username Optional username for proxy authentication.
 * @param password Optional password for proxy authentication.
 * @param nonProxyHosts Hosts that should bypass the proxy. Supports wildcards: "*.internal.example.com".
 */
public record ProxyConfiguration(
        URI proxyUri,
        ProxyType type,
        String username,
        String password,
        List<String> nonProxyHosts) {
    public ProxyConfiguration {
        Objects.requireNonNull(proxyUri, "proxyUri cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        nonProxyHosts = nonProxyHosts == null
                ? List.of()
                : nonProxyHosts.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * Check if a given host should bypass this proxy.
     *
     * @param host hostname to check
     * @return true if host should bypass proxy
     */
    public boolean shouldBypass(String host) {
        if (host == null || nonProxyHosts.isEmpty()) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String pattern : nonProxyHosts) {
            if (matchesPattern(lowerHost, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String host, String pattern) {
        // Simple wildcard matching (host and pattern are already lowercase)
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1); // Remove '*', keep the dot
            return host.endsWith(suffix);
        }
        return host.equals(pattern);
    }

    /**
     * Returns the proxy hostname.
     *
     * @return the hostname from the proxy URI
     */
    public String hostname() {
        return proxyUri.getHost();
    }

    /**
     * Returns the proxy port.
     *
     * <p>If the port is not specified in the URI, returns the default port
     * for the proxy type: 8080 for HTTP/HTTPS, 1080 for SOCKS.
     *
     * @return the proxy port
     */
    public int port() {
        int port = proxyUri.getPort();
        if (port != -1) {
            return port;
        }
        // Default ports
        return switch (type) {
            case HTTP, HTTPS -> 8080;
            case SOCKS4, SOCKS5 -> 1080;
        };
    }

    /**
     * Returns whether proxy authentication is configured.
     *
     * @return true if username is set
     */
    public boolean requiresAuth() {
        return username != null;
    }

    /**
     * Proxy protocol type.
     */
    public enum ProxyType {
        /** HTTP proxy (CONNECT tunnel for HTTPS) */
        HTTP,

        /** HTTPS proxy */
        HTTPS,

        /** SOCKS4 proxy */
        SOCKS4,

        /** SOCKS5 proxy */
        SOCKS5
    }

    /**
     * Builder for ProxyConfiguration.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private URI proxyUri;
        private ProxyType type = ProxyType.HTTP;
        private String username;
        private String password;
        private List<String> nonProxyHosts = List.of();

        private Builder() {}

        /**
         * Sets the proxy server URI.
         *
         * @param proxyUri the proxy URI (e.g., {@code http://proxy.example.com:8080})
         * @return this builder
         */
        public Builder proxyUri(URI proxyUri) {
            this.proxyUri = proxyUri;
            return this;
        }

        /**
         * Sets the proxy server URI from a string.
         *
         * @param proxyUri the proxy URI string
         * @return this builder
         * @throws IllegalArgumentException if the URI is invalid
         */
        public Builder proxyUri(String proxyUri) {
            return proxyUri(URI.create(proxyUri));
        }

        /**
         * Sets the proxy type (default: HTTP).
         *
         * @param type the proxy protocol type
         * @return this builder
         */
        public Builder type(ProxyType type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the username for proxy authentication.
         *
         * @param username the authentication username
         * @return this builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the password for proxy authentication.
         *
         * @param password the authentication password
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets both username and password for proxy authentication.
         *
         * @param username the authentication username
         * @param password the authentication password
         * @return this builder
         */
        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        /**
         * Sets hosts that should bypass the proxy.
         *
         * <p>Supports wildcard patterns: {@code *.internal.example.com} matches
         * any subdomain of {@code internal.example.com}.
         *
         * @param nonProxyHosts list of hostnames or patterns to bypass
         * @return this builder
         */
        public Builder nonProxyHosts(List<String> nonProxyHosts) {
            this.nonProxyHosts = List.copyOf(nonProxyHosts);
            return this;
        }

        /**
         * Sets hosts that should bypass the proxy.
         *
         * @param nonProxyHosts hostnames or patterns to bypass
         * @return this builder
         * @see #nonProxyHosts(List)
         */
        public Builder nonProxyHosts(String... nonProxyHosts) {
            return nonProxyHosts(List.of(nonProxyHosts));
        }

        /**
         * Builds the proxy configuration.
         *
         * @return the configured ProxyConfiguration
         * @throws NullPointerException if proxyUri is null
         */
        public ProxyConfiguration build() {
            return new ProxyConfiguration(proxyUri, type, username, password, nonProxyHosts);
        }
    }
}
