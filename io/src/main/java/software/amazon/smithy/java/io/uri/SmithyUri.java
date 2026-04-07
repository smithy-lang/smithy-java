/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * A lightweight, pre-decomposed URI representation optimized for endpoint-style URIs.
 *
 * <p>This type stores scheme, userInfo, host, port, path, and query. It does not support fragments,
 * opaque URIs, or IPv6 zone identifiers.
 *
 * <p>Scheme and host must be lowercase. IPv6 host literals are stored without brackets; brackets are
 * added in {@link #toString()} and {@link #toURI()} output. When constructed from a {@link URI},
 * scheme and host are normalized to lowercase automatically. When constructed from components,
 * uppercase scheme or host is rejected.
 *
 * <p>Host validation is intentionally lightweight. It rejects obviously invalid characters but does not
 * enforce IPv4/IPv6 syntax, reg-name grammar, or other RFC 3986 host production rules.
 *
 * <p>Derived values ({@link #toString()}, {@link #hashCode()}, {@link #toURI()}) are computed lazily
 * and always derived from the normalized internal fields.
 */
public final class SmithyUri {

    // Lookup tables for fast character validation (ASCII range only).
    private static final boolean[] SCHEME_CONT = new boolean[128];
    private static final boolean[] INVALID_HOST = new boolean[128];
    private static final boolean[] VALID_PATH;
    private static final boolean[] VALID_QUERY;
    private static final boolean[] VALID_USERINFO;
    private static final boolean[] IS_HEX = new boolean[128];

    static {
        // Scheme continuation: lowercase alpha, digits, +, -, .
        SCHEME_CONT['+'] = true;
        SCHEME_CONT['-'] = true;
        SCHEME_CONT['.'] = true;

        INVALID_HOST['/'] = true;
        INVALID_HOST['?'] = true;
        INVALID_HOST['#'] = true;
        INVALID_HOST['%'] = true;
        INVALID_HOST['['] = true;
        INVALID_HOST[']'] = true;
        INVALID_HOST[' '] = true;
        INVALID_HOST['\t'] = true;
        INVALID_HOST['\n'] = true;
        INVALID_HOST['\r'] = true;

        // RFC 3986 shared base: unreserved / pct-encoded / sub-delims
        // unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"
        // sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        VALID_USERINFO = new boolean[128];
        VALID_PATH = new boolean[128];
        VALID_QUERY = new boolean[128];
        for (char c = 'a'; c <= 'z'; c++) {
            SCHEME_CONT[c] = true;
            VALID_USERINFO[c] = true;
            VALID_PATH[c] = true;
            VALID_QUERY[c] = true;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            VALID_USERINFO[c] = true;
            VALID_PATH[c] = true;
            VALID_QUERY[c] = true;
        }
        for (char c = '0'; c <= '9'; c++) {
            VALID_USERINFO[c] = true;
            VALID_PATH[c] = true;
            VALID_QUERY[c] = true;
            SCHEME_CONT[c] = true;
        }
        for (char c : "-._~!$&'()*+,;=%".toCharArray()) {
            VALID_USERINFO[c] = true;
            VALID_PATH[c] = true;
            VALID_QUERY[c] = true;
        }

        // userinfo adds ":"
        VALID_USERINFO[':'] = true;

        // path adds ":" / "@" / "/"
        VALID_PATH[':'] = true;
        VALID_PATH['@'] = true;
        VALID_PATH['/'] = true;

        // query adds everything path has plus "?"
        VALID_QUERY[':'] = true;
        VALID_QUERY['@'] = true;
        VALID_QUERY['/'] = true;
        VALID_QUERY['?'] = true;

        // Hex setup
        for (char c = '0'; c <= '9'; c++) {
            IS_HEX[c] = true;
        }
        for (char c = 'a'; c <= 'f'; c++) {
            IS_HEX[c] = true;
        }
        for (char c = 'A'; c <= 'F'; c++) {
            IS_HEX[c] = true;
        }
    }

    private final String scheme;
    private final String userInfo;
    private final String host;
    private final int port;
    private final String path;
    private final String query;

    // Lazy cached derived values
    private String cachedString;
    private URI cachedUri;
    private String cachedNormalizedPath;
    private int cachedHashcode;
    private boolean hashComputed;

    // Assumes all inputs are already validated/normalized.
    SmithyUri(String scheme, String userInfo, String host, int port, String path, String query) {
        this.scheme = scheme;
        this.userInfo = userInfo;
        this.host = host;
        this.port = port;
        this.path = path == null ? "" : path;
        this.query = query;
    }

    /**
     * Strip IPv6 brackets if the host is a bracketed IPv6 literal (contains ':' inside brackets).
     */
    private static String stripBrackets(String host) {
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            String inner = host.substring(1, host.length() - 1);
            if (inner.indexOf(':') >= 0) {
                return inner;
            }
        }
        return host;
    }

    /**
     * Parse a URI string into a {@code SmithyUri}.
     *
     * <p>Delegates to {@link URI} for parsing.
     *
     * @param uri URI string to parse.
     * @return the parsed SmithyUri.
     * @throws IllegalArgumentException if the string is not a valid URI.
     */
    public static SmithyUri of(String uri) {
        return of(URI.create(Objects.requireNonNull(uri, "uri")));
    }

    /**
     * Create a {@code SmithyUri} from a {@link URI}.
     *
     * <p>Scheme and host are normalized to lowercase. IPv6 brackets are stripped from the host.
     *
     * @param uri URI to convert.
     * @return the SmithyUri.
     */
    public static SmithyUri of(URI uri) {
        Objects.requireNonNull(uri, "uri");
        if (uri.isOpaque()) {
            throw new IllegalArgumentException("Opaque URIs are not supported: " + uri);
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Fragments are not supported: " + uri);
        }
        if (uri.getRawAuthority() != null && uri.getHost() == null) {
            throw new IllegalArgumentException("Registry-based authorities are not supported: " + uri);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        return new SmithyUri(
                scheme != null ? scheme.toLowerCase(Locale.ROOT) : null,
                uri.getRawUserInfo(),
                host != null ? stripBrackets(host).toLowerCase(Locale.ROOT) : null,
                uri.getPort(),
                uri.getRawPath(),
                uri.getRawQuery());
    }

    /**
     * Create a {@code SmithyUri} from individual components (without userInfo).
     *
     * <p>Scheme and host must be lowercase. IPv6 brackets on host are stripped.
     *
     * @param scheme URI scheme (e.g. "https"). May be null. Must be lowercase.
     * @param host   Host component. May be null. Must be lowercase; IPv6 brackets are stripped.
     * @param port   Port number, or -1 for no port.
     * @param path   Raw percent-encoded path. Null defaults to empty string.
     * @param query  Raw percent-encoded query string. May be null.
     * @return the SmithyUri.
     * @throws IllegalArgumentException if any component fails validation.
     */
    public static SmithyUri of(String scheme, String host, int port, String path, String query) {
        return of(scheme, null, host, port, path, query);
    }

    /**
     * Create a {@code SmithyUri} from individual components including userInfo.
     *
     * <p>Scheme and host must be lowercase. IPv6 brackets on host are stripped.
     *
     * @param scheme   URI scheme (e.g. "https"). May be null. Must be lowercase.
     * @param userInfo Raw percent-encoded userInfo (e.g. "user:pass"). May be null.
     * @param host     Host component. May be null. Must be lowercase; IPv6 brackets are stripped.
     * @param port     Port number, or -1 for no port.
     * @param path     Raw percent-encoded path. Null defaults to empty string.
     * @param query    Raw percent-encoded query string. May be null.
     * @return the SmithyUri.
     * @throws IllegalArgumentException if any component fails validation.
     */
    public static SmithyUri of(String scheme, String userInfo, String host, int port, String path, String query) {
        if (scheme != null) {
            validateScheme(scheme);
        }
        if (userInfo != null) {
            validateUserInfo(userInfo);
        }
        // Strip IPv6 brackets before validation — brackets are not valid in the stored form.
        host = stripBrackets(host);
        if (host != null) {
            validateHost(host);
        }
        validatePort(port);
        if (path != null) {
            validatePath(path);
        }
        if (query != null) {
            validateQuery(query);
        }
        return new SmithyUri(scheme, userInfo, host, port, path, query);
    }

    /**
     * @return the URI scheme (e.g. "https"), or null. Always lowercase.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * @return the raw percent-encoded userInfo component (e.g. "user:pass"), or null.
     */
    public String getUserInfo() {
        return userInfo;
    }

    /**
     * @return the host component (lowercase, unbracketed for IPv6), or null.
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the port number, or -1 if not set.
     */
    public int getPort() {
        return port;
    }

    /**
     * @return the raw percent-encoded path. Never null; may be empty for authority-only URIs.
     */
    public String getPath() {
        return path;
    }

    /**
     * @return the raw percent-encoded query string, or null.
     */
    public String getQuery() {
        return query;
    }

    /**
     * Return a new {@code SmithyUri} with the scheme replaced.
     *
     * @param scheme new scheme. Must be lowercase.
     * @return new SmithyUri.
     */
    public SmithyUri withScheme(String scheme) {
        if (Objects.equals(this.scheme, scheme)) {
            return this;
        }
        if (scheme != null) {
            validateScheme(scheme);
        }
        return new SmithyUri(scheme, userInfo, host, port, path, query);
    }

    /**
     * Return a new {@code SmithyUri} with the userInfo replaced.
     *
     * @param userInfo new raw percent-encoded userInfo, or null.
     * @return new SmithyUri.
     */
    public SmithyUri withUserInfo(String userInfo) {
        if (Objects.equals(this.userInfo, userInfo)) {
            return this;
        }
        if (userInfo != null) {
            validateUserInfo(userInfo);
        }
        return new SmithyUri(scheme, userInfo, host, port, path, query);
    }

    /**
     * Return a new {@code SmithyUri} with the host replaced.
     *
     * @param host new host. Must be lowercase; IPv6 brackets are stripped.
     * @return new SmithyUri.
     */
    public SmithyUri withHost(String host) {
        host = stripBrackets(host);
        if (Objects.equals(this.host, host)) {
            return this;
        }
        if (host != null) {
            validateHost(host);
        }
        return new SmithyUri(scheme, userInfo, host, port, path, query);
    }

    /**
     * Return a new {@code SmithyUri} with the port replaced.
     *
     * @param port new port, or -1 for none.
     * @return new SmithyUri.
     */
    public SmithyUri withPort(int port) {
        if (this.port == port) {
            return this;
        }
        validatePort(port);
        return new SmithyUri(scheme, userInfo, host, port, path, query);
    }

    /**
     * Return a new {@code SmithyUri} with the path replaced.
     *
     * @param path new raw percent-encoded path.
     * @return new SmithyUri.
     */
    public SmithyUri withPath(String path) {
        String normalized = path == null ? "" : path;
        if (this.path.equals(normalized)) {
            return this;
        }
        if (path != null) {
            validatePath(path);
        }
        return new SmithyUri(scheme, userInfo, host, port, path, query);
    }

    /**
     * Return a new {@code SmithyUri} with the query replaced.
     *
     * @param query new raw percent-encoded query, or null.
     * @return new SmithyUri.
     */
    public SmithyUri withQuery(String query) {
        if (Objects.equals(this.query, query)) {
            return this;
        }
        if (query != null) {
            validateQuery(query);
        }
        return new SmithyUri(scheme, userInfo, host, port, path, query);
    }

    /**
     * Return a new {@code SmithyUri} with the given path concatenated onto the existing path.
     *
     * @param suffix raw percent-encoded path to append.
     * @return new SmithyUri.
     */
    public SmithyUri withConcatPath(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return this;
        }
        return new SmithyUri(scheme, userInfo, host, port, concatPaths(path, suffix), query);
    }

    /**
     * Return a new {@code SmithyUri} that applies the given endpoint as the base.
     *
     * <p>Takes scheme, userInfo, host, and port from the endpoint. Concatenates the endpoint's path
     * with this URI's path. Keeps this URI's query string.
     *
     * @param endpoint the base endpoint URI.
     * @return new SmithyUri.
     */
    public SmithyUri withEndpoint(SmithyUri endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        return new SmithyUri(
                endpoint.scheme,
                endpoint.userInfo,
                endpoint.host,
                endpoint.port,
                concatPaths(endpoint.path, this.path),
                this.query);
    }

    /**
     * Convert to a {@link URI}. The result is cached.
     *
     * @return the equivalent {@link URI}.
     */
    public URI toURI() {
        URI u = cachedUri;
        if (u == null) {
            cachedUri = u = URI.create(toString());
        }
        return u;
    }

    @Override
    public String toString() {
        String s = cachedString;
        if (s == null) {
            cachedString = s = buildString();
        }
        return s;
    }

    @Override
    public int hashCode() {
        if (!hashComputed) {
            cachedHashcode = Objects.hash(scheme, userInfo, host, port, path, query);
            hashComputed = true;
        }
        return cachedHashcode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SmithyUri smithyUri)) {
            return false;
        }
        return port == smithyUri.port
                && Objects.equals(scheme, smithyUri.scheme)
                && Objects.equals(userInfo, smithyUri.userInfo)
                && Objects.equals(host, smithyUri.host)
                && path.equals(smithyUri.path)
                && Objects.equals(query, smithyUri.query);
    }

    private String buildString() {
        var sb = new StringBuilder();
        if (scheme != null) {
            sb.append(scheme).append(':');
        }
        if (host != null) {
            sb.append('/').append('/');
            if (userInfo != null) {
                sb.append(userInfo).append('@');
            }
            if (host.indexOf(':') >= 0) {
                sb.append('[').append(host).append(']');
            } else {
                sb.append(host);
            }
            if (port >= 0) {
                sb.append(':').append(port);
            }
        }
        if (!path.isEmpty()) {
            if (host != null && path.charAt(0) != '/') {
                sb.append('/');
            }
            sb.append(path);
        }
        if (query != null) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }

    static String concatPaths(String base, String suffix) {
        if (base == null || base.isEmpty() || base.equals("/")) {
            return (suffix == null || suffix.isEmpty()) ? "" : suffix.startsWith("/") ? suffix : "/" + suffix;
        }
        if (suffix == null || suffix.isEmpty()) {
            return base;
        }

        // Don't collapse a bare "/" suffix — it represents a trailing slash.
        boolean baseEnds = base.charAt(base.length() - 1) == '/';
        boolean suffixStarts = suffix.charAt(0) == '/';
        if (baseEnds && suffixStarts) {
            return base + suffix.substring(1);
        } else if (!baseEnds && !suffixStarts) {
            return base + '/' + suffix;
        } else {
            return base + suffix;
        }
    }

    /**
     * Get the normalized path, resolving ".", "..", and empty segments. The result is cached.
     *
     * @return the normalized raw path.
     */
    public String getNormalizedPath() {
        String n = cachedNormalizedPath;
        if (n == null) {
            cachedNormalizedPath = n = normalizePath(path);
        }
        return n;
    }

    /**
     * Resolves ".", "..", and consecutive slash segments in a path string.
     */
    private static String normalizePath(String path) {
        if (path.isEmpty()) {
            return path;
        }

        // Fast path: no dots or double slashes means nothing to normalize.
        boolean hasDoubleSlash = path.contains("//");
        if (path.indexOf('.') == -1 && !hasDoubleSlash) {
            return path;
        }

        boolean absolute = path.charAt(0) == '/';
        boolean trailingSlash = path.length() > 1 && path.charAt(path.length() - 1) == '/';

        int len = path.length();
        StringBuilder sb = new StringBuilder(len);
        if (absolute) {
            sb.append('/');
        }

        boolean changed = hasDoubleSlash;
        boolean lastWasDot = false;

        int i = 0;
        while (i < len) {
            // Skip consecutive slashes.
            while (i < len && path.charAt(i) == '/') {
                i++;
            }
            if (i >= len) {
                break;
            }

            int start = i;
            while (i < len && path.charAt(i) != '/') {
                i++;
            }
            int end = i;
            int segLen = end - start;

            // "." — current directory, skip.
            if (segLen == 1 && path.charAt(start) == '.') {
                changed = true;
                lastWasDot = true;
                continue;
            }

            // ".." — parent directory, pop last segment or preserve as underflow.
            if (segLen == 2 && path.charAt(start) == '.' && path.charAt(start + 1) == '.') {
                changed = true;
                lastWasDot = true;
                int popTo = findPoppablePathSegment(sb, absolute);
                if (popTo >= 0) {
                    sb.setLength(popTo == 0 ? 0 : popTo - 1);
                    if (absolute && sb.isEmpty()) {
                        sb.append('/');
                    }
                } else {
                    // Can't pop: preserve ".." as a segment (above root or relative leading ..).
                    appendPathSegment(sb, path, start, end);
                }
                continue;
            }

            // Normal segment.
            lastWasDot = false;
            appendPathSegment(sb, path, start, end);
        }

        // A trailing dot segment implies a trailing slash (e.g., /foo/. → /foo/).
        boolean effectiveTrailingSlash = trailingSlash || lastWasDot;
        if (effectiveTrailingSlash && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '/') {
            sb.append('/');
        }

        if (!changed) {
            return path;
        }

        return sb.isEmpty() ? (absolute ? "/" : "") : sb.toString();
    }

    /**
     * Returns the start position of the last segment in sb that can be popped, or -1 if none.
     */
    private static int findPoppablePathSegment(StringBuilder sb, boolean absolute) {
        if (absolute) {
            if (sb.length() == 1) {
                return -1;
            }
        } else if (sb.isEmpty()) {
            return -1;
        }

        int segStart = lastPathSegmentStart(sb);
        int segLen = sb.length() - segStart;

        // Relative leading ".." cannot be popped by another "..".
        if (segLen == 2 && sb.charAt(segStart) == '.' && sb.charAt(segStart + 1) == '.') {
            return -1;
        }

        return segStart;
    }

    private static int lastPathSegmentStart(StringBuilder sb) {
        int i = sb.lastIndexOf("/");
        return i < 0 ? 0 : i + 1;
    }

    private static void appendPathSegment(StringBuilder sb, String path, int start, int end) {
        int len = sb.length();
        if (len != 0 && sb.charAt(len - 1) != '/') {
            sb.append('/');
        }
        sb.append(path, start, end);
    }

    private static void validateScheme(String scheme) {
        if (scheme.isEmpty()) {
            throw new IllegalArgumentException("Scheme must not be empty");
        }

        char first = scheme.charAt(0);
        if (first < 'a' || first > 'z') {
            throw new IllegalArgumentException("Scheme must start with a lowercase letter: " + scheme);
        }

        for (int i = 1; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            if (c >= 128 || !SCHEME_CONT[c]) {
                throw new IllegalArgumentException("Invalid character in scheme: '" + c + "' in " + scheme);
            }
        }
    }

    private static void validateUserInfo(String userInfo) {
        validateComponent(userInfo, VALID_USERINFO, "userInfo");
    }

    private static void validateHost(String host) {
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Host must not be empty");
        }

        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                throw new IllegalArgumentException("Host must be lowercase: " + host);
            }
            if (c < 128 && INVALID_HOST[c]) {
                throw new IllegalArgumentException("Invalid character in host: '" + c + "' in " + host);
            }
        }
    }

    private static void validatePort(int port) {
        if (port < -1 || port > 65535) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
    }

    private static void validatePath(String path) {
        validateComponent(path, VALID_PATH, "path");
    }

    private static void validateQuery(String query) {
        validateComponent(query, VALID_QUERY, "query");
    }

    private static void validateComponent(String value, boolean[] allowed, String component) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 128 || !allowed[c]) {
                throw new IllegalArgumentException("Invalid character in " + component + ": '" + c + "'");
            }
            if (c == '%') {
                validatePercentEncoding(value, i, component);
            }
        }
    }

    private static void validatePercentEncoding(String value, int i, String component) {
        if (i + 2 >= value.length()) {
            throw new IllegalArgumentException("Incomplete percent-encoding in " + component + " at index " + i);
        } else if (!isHex(value.charAt(i + 1)) || !isHex(value.charAt(i + 2))) {
            throw new IllegalArgumentException("Invalid percent-encoding in " + component + " at index " + i + ": %"
                    + value.charAt(i + 1) + value.charAt(i + 2));
        }
    }

    private static boolean isHex(char c) {
        return c < 128 && IS_HEX[c];
    }
}
