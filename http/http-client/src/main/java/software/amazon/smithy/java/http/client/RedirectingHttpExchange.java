/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;

final class RedirectingHttpExchange implements HttpExchange {
    private final HttpRequest originalRequest;
    private final int maxRedirects;
    private final ConnectionPool pool;

    private HttpExchange currentExchange;
    private HttpConnection currentConnection;
    private boolean redirectsResolved = false;
    private final Set<URI> visitedUris = new HashSet<>();

    RedirectingHttpExchange(
            HttpExchange initialExchange,
            HttpConnection initialConnection,
            HttpRequest request,
            int maxRedirects,
            ConnectionPool pool
    ) {
        this.currentExchange = initialExchange;
        this.currentConnection = initialConnection;
        this.originalRequest = request;
        this.maxRedirects = maxRedirects;
        this.pool = pool;
    }

    @Override
    public OutputStream requestBody() {
        // Just delegate - no redirects yet
        return currentExchange.requestBody();
    }

    @Override
    public InputStream responseBody() throws IOException {
        try {
            ensureRedirectsResolved();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return currentExchange.responseBody();
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        ensureRedirectsResolved();
        return currentExchange.responseHeaders();
    }

    @Override
    public int statusCode() throws IOException {
        ensureRedirectsResolved();
        return currentExchange.statusCode();
    }

    private void ensureRedirectsResolved() throws IOException {
        if (redirectsResolved) {
            return;
        }

        redirectsResolved = true;

        HttpRequest currentRequest = originalRequest;
        // Track the original URI to detect loops
        visitedUris.add(normalizeUri(currentRequest.uri()));

        for (int i = 0; i < maxRedirects; i++) {
            int status = currentExchange.statusCode();

            if (!isRedirect(status)) {
                // Not a redirect, we're done
                return;
            }

            HttpHeaders headers = currentExchange.responseHeaders();
            String location = headers.firstValue("Location");

            if (location == null) {
                // No location header, treat as final response
                return;
            }

            // Consume and close current exchange
            try (InputStream body = currentExchange.responseBody()) {
                body.transferTo(OutputStream.nullOutputStream());
            }
            currentExchange.close();

            // Release current connection
            pool.release(currentConnection, currentRequest.uri());

            // Build redirect request
            URI redirectUri = currentRequest.uri().resolve(location);

            // Detect redirect loops
            URI normalizedRedirectUri = normalizeUri(redirectUri);
            if (visitedUris.contains(normalizedRedirectUri)) {
                throw new IOException("Redirect loop detected: " + redirectUri
                        + " (already visited in this request chain)");
            }
            visitedUris.add(normalizedRedirectUri);

            // SSRF protection: Block redirects to private IP ranges
            if (redirectUri.getHost() != null && !isAllowedRedirectTarget(redirectUri.getHost())) {
                throw new IOException("Redirect to private/local address is not allowed: " + redirectUri);
            }

            // Block HTTPS -> HTTP downgrades to prevent protocol downgrade attacks
            if ("https".equalsIgnoreCase(currentRequest.uri().getScheme())
                    && "http".equalsIgnoreCase(redirectUri.getScheme())) {
                throw new IOException("Redirect from HTTPS to HTTP is not allowed: "
                        + currentRequest.uri() + " -> " + redirectUri);
            }

            HttpRequest redirectRequest = buildRedirectRequest(currentRequest, redirectUri, status);

            // Get new connection and exchange
            currentConnection = pool.acquire(redirectUri);
            currentExchange = currentConnection.newExchange(redirectRequest);

            // Close request body for redirect (GET/HEAD have no body)
            currentExchange.requestBody().close();

            currentRequest = redirectRequest;
        }

        // Hit max redirects
        throw new IOException("Too many redirects (>" + maxRedirects + ")");
    }

    @Override
    public void close() throws IOException {
        currentExchange.close();
        if (currentConnection != null) {
            pool.release(currentConnection, originalRequest.uri());
        }
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        return currentExchange.supportsBidirectionalStreaming();
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302
                || status == 303
                ||
                status == 307
                || status == 308;
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
        if ((port == 80 && "http".equalsIgnoreCase(scheme))
                || (port == 443 && "https".equalsIgnoreCase(scheme))) {
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
        } else if ((status == 301 || status == 302) &&
                !original.method().equals("GET")
                &&
                !original.method().equals("HEAD")) {
            // 301, 302 historically became GET for non-GET methods
            builder.method("GET");
        } else { // 307, 308 preserve method
            builder.method(original.method());
            // Note: Can't preserve body for streaming - already consumed
        }

        // Copy headers (excluding host-specific ones)
        for (String name : original.headers().map().keySet()) {
            if (name.equalsIgnoreCase("host") ||
                    name.equalsIgnoreCase("content-length")
                    ||
                    name.equalsIgnoreCase("transfer-encoding")) {
                continue;
            }
            for (String value : original.headers().allValues(name)) {
                builder.withAddedHeader(name, value);
            }
        }

        return builder.build();
    }
}
