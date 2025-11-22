/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Interceptor that automatically follows HTTP redirects (3xx status codes).
 *
 * <p>Only works with {@link HttpClient#send(HttpRequest)} (buffered requests).
 * Does NOT support {@link HttpClient#exchange(HttpRequest)} (streaming requests)
 * because the request/response bodies may have been consumed.
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable maximum redirect count (default: 10)</li>
 *   <li>Redirect loop detection</li>
 *   <li>SSRF protection (blocks redirects to private/loopback IPs)</li>
 *   <li>Protocol downgrade protection (blocks HTTPS → HTTP)</li>
 *   <li>Proper handling of different redirect status codes (301, 302, 303, 307, 308)</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * HttpClient client = HttpClient.builder()
 *     .addInterceptor(new RedirectInterceptor())
 *     .build();
 *
 * // Redirects followed automatically
 * HttpResponse response = client.send(request);
 * }</pre>
 *
 * <p>Custom configuration:
 * <pre>{@code
 * HttpClient client = HttpClient.builder()
 *     .addInterceptor(RedirectInterceptor.builder()
 *         .maxRedirects(5)
 *         .allowCrossProtocol(false)
 *         .enableSsrfProtection(true)
 *         .build())
 *     .build();
 * }</pre>
 */
public final class RedirectInterceptor implements HttpInterceptor {

    private final int maxRedirects;
    private final boolean allowCrossProtocol;
    private final boolean enableSsrfProtection;

    public RedirectInterceptor() {
        this(10, false, true);
    }

    private RedirectInterceptor(int maxRedirects, boolean allowCrossProtocol, boolean enableSsrfProtection) {
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("maxRedirects must be non-negative: " + maxRedirects);
        }
        this.maxRedirects = maxRedirects;
        this.allowCrossProtocol = allowCrossProtocol;
        this.enableSsrfProtection = enableSsrfProtection;
    }

    /**
     * Create a builder for custom configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public HttpResponse interceptResponse(
            HttpClient client,
            HttpRequest request,
            Context context,
            HttpResponse response
    ) throws IOException {

        if (!isRedirect(response.statusCode())) {
            return null;
        }

        String location = response.headers().firstValue("Location");
        if (location == null || location.isEmpty()) {
            return null;
        }

        // Get or create redirect chain from context
        List<URI> redirectChain = context.computeIfAbsent(
                HttpContext.REDIRECT_CHAIN,
                k -> new ArrayList<>());

        // Check max redirects
        if (redirectChain.size() >= maxRedirects) {
            throw new IOException("Too many redirects (>" + maxRedirects + ")");
        }

        // Build redirect URI
        URI redirectUri;
        try {
            redirectUri = request.uri().resolve(location);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid redirect location: " + location, e);
        }

        // Normalize for loop detection
        URI normalizedUri = normalizeUri(redirectUri);

        // Check for redirect loop
        for (URI visited : redirectChain) {
            if (normalizeUri(visited).equals(normalizedUri)) {
                throw new IOException(
                        "Redirect loop detected: " + redirectUri +
                                " (already visited in this request chain)");
            }
        }

        // SSRF protection
        if (enableSsrfProtection && redirectUri.getHost() != null) {
            if (!isAllowedRedirectTarget(redirectUri.getHost())) {
                throw new IOException(
                        "Redirect to private/local address is not allowed: " + redirectUri);
            }
        }

        // Protocol downgrade protection
        if (!allowCrossProtocol) {
            String originalScheme = request.uri().getScheme();
            String redirectScheme = redirectUri.getScheme();

            if ("https".equalsIgnoreCase(originalScheme) &&
                    "http".equalsIgnoreCase(redirectScheme)) {
                throw new IOException(
                        "Redirect from HTTPS to HTTP is not allowed: " +
                                request.uri() + " -> " + redirectUri);
            }
        }

        // Add current URI to redirect chain before following
        redirectChain.add(request.uri());

        // Build redirect request
        HttpRequest redirectRequest = buildRedirectRequest(
                request,
                redirectUri,
                response.statusCode());

        // Follow redirect with the same context to preserve redirect chain tracking
        RequestOptions options = RequestOptions.builder().context(context).build();
        return client.send(redirectRequest, options);
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /**
     * Normalize URI for loop detection by removing default ports and normalizing the path.
     */
    private URI normalizeUri(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();

        // Remove default ports
        if ((port == 80 && "http".equalsIgnoreCase(scheme)) ||
                (port == 443 && "https".equalsIgnoreCase(scheme))) {
            port = -1;
        }

        // Normalize empty path to "/"
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        try {
            return new URI(
                    scheme != null ? scheme.toLowerCase() : null,
                    null, // userInfo
                    host != null ? host.toLowerCase() : null,
                    port,
                    path,
                    uri.getQuery(),
                    null // fragment - exclude from comparison
            );
        } catch (Exception e) {
            // If normalization fails, return original URI
            return uri;
        }
    }

    /**
     * Check if a redirect target host is allowed (not a private/local IP address).
     * Blocks SSRF attacks by preventing redirects to internal networks.
     *
     * @param host the hostname or IP address to check
     * @return true if the host is allowed, false if it's a private/local address
     */
    private boolean isAllowedRedirectTarget(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);

            // Block loopback addresses (127.0.0.0/8, ::1)
            if (addr.isLoopbackAddress()) {
                return false;
            }

            // Block link-local addresses (169.254.0.0/16, fe80::/10)
            if (addr.isLinkLocalAddress()) {
                return false;
            }

            // Block site-local/private addresses (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7)
            if (addr.isSiteLocalAddress()) {
                return false;
            }

            // Additional check for private IPv4 ranges
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4) { // IPv4
                int firstOctet = bytes[0] & 0xFF;
                int secondOctet = bytes[1] & 0xFF;

                // 10.0.0.0/8
                if (firstOctet == 10) {
                    return false;
                }

                // 172.16.0.0/12
                if (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) {
                    return false;
                }

                // 192.168.0.0/16
                if (firstOctet == 192 && secondOctet == 168) {
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException e) {
            // If we can't resolve the host, allow it to fail later during connection
            // (DNS resolution failure should not be treated as SSRF)
            return true;
        }
    }

    private HttpRequest buildRedirectRequest(HttpRequest original, URI newUri, int status) {
        HttpRequest.Builder builder = HttpRequest.builder().uri(newUri);

        // 303 always becomes GET
        if (status == 303) {
            builder.method("GET");
        } else if ((status == 301 || status == 302)
                && !original.method().equals("GET")
                && !original.method().equals("HEAD")) {
            // 301, 302 historically became GET for non-GET methods
            builder.method("GET");
        } else {
            // 307, 308 preserve method (but body already consumed for buffered requests)
            builder.method(original.method());
        }

        // Copy headers (excluding host-specific ones)
        for (String name : original.headers().map().keySet()) {
            if (name.equalsIgnoreCase("host")
                    || name.equalsIgnoreCase("content-length")
                    || name.equalsIgnoreCase("transfer-encoding")) {
                continue;
            }

            // Don't leak Authorization to different host
            if (name.equalsIgnoreCase("authorization")) {
                if (isSameOrigin(original.uri(), newUri)) {
                    for (String value : original.headers().allValues(name)) {
                        builder.withAddedHeader(name, value);
                    }
                }
                continue;
            }

            for (String value : original.headers().allValues(name)) {
                builder.withAddedHeader(name, value);
            }
        }

        return builder.build();
    }

    /**
     * Check if two URIs are same origin (scheme + host + port).
     */
    private boolean isSameOrigin(URI uri1, URI uri2) {
        String scheme1 = uri1.getScheme();
        String scheme2 = uri2.getScheme();
        if (scheme1 == null || !scheme1.equalsIgnoreCase(scheme2)) {
            return false;
        }

        String host1 = uri1.getHost();
        String host2 = uri2.getHost();
        if (host1 == null || !host1.equalsIgnoreCase(host2)) {
            return false;
        }

        int port1 = uri1.getPort();
        if (port1 == -1) {
            port1 = "https".equalsIgnoreCase(scheme1) ? 443 : 80;
        }

        int port2 = uri2.getPort();
        if (port2 == -1) {
            port2 = "https".equalsIgnoreCase(scheme2) ? 443 : 80;
        }

        return port1 == port2;
    }

    /**
     * Builder for configuring RedirectInterceptor.
     */
    public static final class Builder {
        private int maxRedirects = 10;
        private boolean allowCrossProtocol = false;
        private boolean enableSsrfProtection = true;

        /**
         * Set maximum number of redirects to follow (default: 10).
         *
         * @param max maximum redirects, must be non-negative
         * @return this builder
         */
        public Builder maxRedirects(int max) {
            if (max < 0) {
                throw new IllegalArgumentException("maxRedirects must be non-negative: " + max);
            }
            this.maxRedirects = max;
            return this;
        }

        /**
         * Allow cross-protocol redirects (HTTPS → HTTP).
         * Default: false (blocks HTTPS → HTTP for security).
         *
         * @param allow true to allow cross-protocol redirects
         * @return this builder
         */
        public Builder allowCrossProtocol(boolean allow) {
            this.allowCrossProtocol = allow;
            return this;
        }

        /**
         * Enable SSRF protection (blocks redirects to private/loopback IPs).
         * Default: true (protection enabled).
         *
         * @param enable true to enable SSRF protection (default is true).
         * @return this builder
         */
        public Builder ssrfProtection(boolean enable) {
            this.enableSsrfProtection = enable;
            return this;
        }

        /**
         * Build the interceptor.
         */
        public RedirectInterceptor build() {
            return new RedirectInterceptor(maxRedirects, allowCrossProtocol, enableSsrfProtection);
        }
    }
}
