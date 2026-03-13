/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.net.URI;
import java.util.Objects;

/**
 * Proxy configuration for HTTP connections.
 *
 * <p>Currently supports HTTP proxies (CONNECT tunnel for HTTPS targets).
 * SOCKS proxy types are defined but not yet implemented.
 *
 * @param proxyUri Proxy server URI.
 * @param type Type of proxy.
 * @param credentials Optional credentials for proxy authentication.
 */
public record ProxyConfiguration(URI proxyUri, ProxyType type, HttpCredentials credentials) {
    /**
     * Create a proxy configuration without authentication.
     *
     * @param proxyUri proxy server URI
     * @param type proxy type
     */
    public ProxyConfiguration(URI proxyUri, ProxyType type) {
        this(proxyUri, type, null);
    }

    public ProxyConfiguration {
        Objects.requireNonNull(proxyUri, "proxyUri cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }

    /**
     * Create a proxy configuration with Basic authentication.
     *
     * @param proxyUri proxy server URI
     * @param type proxy type
     * @param username authentication username
     * @param password authentication password
     * @return proxy configuration with Basic auth credentials
     */
    public static ProxyConfiguration withBasicAuth(URI proxyUri, ProxyType type, String username, String password) {
        return new ProxyConfiguration(proxyUri, type, new HttpCredentials.Basic(username, password, true));
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
        return switch (type) {
            case HTTP -> 8080;
            case SOCKS4, SOCKS5 -> 1080;
        };
    }

    /**
     * Proxy protocol type.
     */
    public enum ProxyType {
        /** HTTP proxy (CONNECT tunnel for HTTPS targets) */
        HTTP,

        /** SOCKS4 proxy */
        SOCKS4,

        /** SOCKS5 proxy */
        SOCKS5
    }
}
