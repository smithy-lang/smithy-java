/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * LRU cache for URI parsing.
 *
 * <p>Maintains a hot slot for the most recently accessed URI to avoid hash computation and LinkedHashMap traversal.
 * On cache miss, uses a fast inline parser instead of {@code URI.create()} since endpoint URIs are always
 * well-formed {@code scheme://host[:port][/path]} strings.
 */
final class UriFactory extends LinkedHashMap<String, SmithyUri> {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_MAX_SIZE = 32;
    private final int maxSize;

    private String hotKey;
    private transient SmithyUri hotValue;

    UriFactory() {
        this(DEFAULT_MAX_SIZE);
    }

    UriFactory(int maxSize) {
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, SmithyUri> eldest) {
        return size() > maxSize;
    }

    SmithyUri createUri(String uri) {
        if (uri == null) {
            return null;
        }

        if (uri.equals(hotKey)) {
            return hotValue;
        }

        // Fall back to full LRU cache
        SmithyUri result = get(uri);
        if (result == null) {
            try {
                result = fastParseUri(uri);
                put(uri, result);
            } catch (IllegalArgumentException ignored) {
                // Don't cache invalid URIs in hot slot
                return null;
            }
        }

        // Update hot-key cache
        hotKey = uri;
        hotValue = result;

        return result;
    }

    /**
     * Fast URI parser for endpoint URIs. Handles {@code scheme://host[:port][/path]} format
     * without going through {@code java.net.URI.create()}.
     *
     * <p>Falls back to {@code SmithyUri.of(uri)} for URIs that don't match the expected format.
     */
    private static SmithyUri fastParseUri(String uri) {
        // Find "://"
        int colonSlashSlash = uri.indexOf("://");
        if (colonSlashSlash <= 0) {
            return SmithyUri.of(uri); // fallback
        }

        String scheme = uri.substring(0, colonSlashSlash);
        int authorityStart = colonSlashSlash + 3;
        int len = uri.length();

        // Find end of authority (first '/' after "://")
        int pathStart = -1;
        if (authorityStart < len && uri.charAt(authorityStart) == '[') {
            // IPv6: find closing ']' first
            int bracket = uri.indexOf(']', authorityStart);
            if (bracket < 0) {
                return SmithyUri.of(uri); // malformed IPv6, fallback
            }
            // After ']', look for ':port' or '/'
            for (int i = bracket + 1; i < len; i++) {
                if (uri.charAt(i) == '/') {
                    pathStart = i;
                    break;
                }
            }
        } else {
            for (int i = authorityStart; i < len; i++) {
                if (uri.charAt(i) == '/') {
                    pathStart = i;
                    break;
                }
            }
        }

        int authorityEnd = pathStart > 0 ? pathStart : len;
        String path = pathStart > 0 ? uri.substring(pathStart) : "";

        // Split authority into host[:port]
        String authority = uri.substring(authorityStart, authorityEnd);
        String host;
        int port = -1;

        if (!authority.isEmpty() && authority.charAt(0) == '[') {
            // IPv6: host is [...]  , optional :port after ]
            int bracket = authority.indexOf(']');
            host = authority.substring(1, bracket); // strip brackets
            if (bracket + 1 < authority.length() && authority.charAt(bracket + 1) == ':') {
                port = parsePort(authority, bracket + 2);
            }
        } else {
            int lastColon = authority.lastIndexOf(':');
            if (lastColon > 0) {
                // Could be host:port — verify the part after ':' is numeric
                if (isNumeric(authority, lastColon + 1)) {
                    host = authority.substring(0, lastColon);
                    port = parsePort(authority, lastColon + 1);
                } else {
                    host = authority;
                }
            } else {
                host = authority;
            }
        }

        return SmithyUri.of(scheme, host, port, path, null, SmithyUri.VALIDATE_HOST);
    }

    private static boolean isNumeric(String s, int from) {
        if (from >= s.length()) {
            return false;
        }
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static int parsePort(String s, int from) {
        int port = 0;
        for (int i = from; i < s.length(); i++) {
            port = port * 10 + (s.charAt(i) - '0');
        }
        return port;
    }
}
