/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_AUTHORITY;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_METHOD;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_PATH;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_SCHEME;

import java.io.IOException;
import java.util.Set;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.hpack.HpackEncoder;
import software.amazon.smithy.java.io.ByteBufferOutputStream;

/**
 * Encodes HTTP/2 request headers and trailers using HPACK compression.
 *
 * <p>This class handles the encoding of request HEADERS frames per RFC 9113,
 * including pseudo-header construction, connection header filtering, and
 * sensitive header marking.
 *
 * <h2>RFC 9113 Compliance</h2>
 * <ul>
 *   <li>Section 8.3.1: Request pseudo-headers (:method, :scheme, :authority, :path)</li>
 *   <li>Section 8.2.2: CONNECT method handling (no :scheme or :path)</li>
 *   <li>Section 8.2.1: Connection-specific headers must not be sent</li>
 *   <li>Section 10.5.1: Header list size validation</li>
 * </ul>
 *
 * <h2>Zero-Copy Design</h2>
 * <p>The encoder reuses an internal buffer across requests. After encoding, callers access
 * the encoded data via {@link #buffer()} and {@link #size()} to avoid copying. The buffer
 * is only valid until the next encode call.
 *
 * <h2>Threading</h2>
 * <p>This class is NOT thread-safe. It must only be used from the writer thread
 * to maintain HPACK encoder state consistency.
 */
final class H2RequestHeaderEncoder {

    /** Headers that must not be sent over HTTP/2 (connection-specific per RFC 9113 Section 8.2.1). */
    private static final Set<String> CONNECTION_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-connection",
            "transfer-encoding",
            "upgrade",
            "host");

    /** Headers that should not be indexed in HPACK (contain sensitive data). */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "proxy-authorization",
            "set-cookie");

    private final HpackEncoder hpackEncoder;
    private final ByteBufferOutputStream encodeBuffer;

    /**
     * Create a new request header encoder.
     *
     * @param hpackEncoder the HPACK encoder to use
     * @param encodeBuffer the buffer to encode headers into
     */
    H2RequestHeaderEncoder(HpackEncoder hpackEncoder, ByteBufferOutputStream encodeBuffer) {
        this.hpackEncoder = hpackEncoder;
        this.encodeBuffer = encodeBuffer;
    }

    /**
     * Set the maximum HPACK dynamic table size.
     *
     * @param maxSize the new maximum size
     */
    void setMaxTableSize(int maxSize) {
        hpackEncoder.setMaxTableSize(maxSize);
    }

    /**
     * Encode request headers into the internal buffer.
     *
     * <p>After calling this method, use {@link #buffer()} and {@link #size()} to access the encoded data.
     * The data is valid until the next encode call.
     *
     * @param request the HTTP request
     * @param maxHeaderListSize maximum header list size allowed by peer (Integer.MAX_VALUE if unlimited)
     * @throws IOException if encoding fails or header list size exceeds limit
     */
    void encodeHeaders(HttpRequest request, int maxHeaderListSize) throws IOException {
        encodeBuffer.reset();
        hpackEncoder.beginHeaderBlock(encodeBuffer);

        long headerListSize = 0;
        String method = request.method();
        boolean isConnect = "CONNECT".equalsIgnoreCase(method);

        String authority = getAuthority(request);
        String scheme = isConnect ? null : request.uri().getScheme();
        String path = isConnect ? null : getPath(request);

        // Encode pseudo-headers (must come first per RFC 9113 Section 8.3)
        hpackEncoder.encodeHeader(encodeBuffer, PSEUDO_METHOD, method, false);
        headerListSize += PSEUDO_METHOD.length() + method.length() + 32;

        if (!isConnect) {
            hpackEncoder.encodeHeader(encodeBuffer, PSEUDO_SCHEME, scheme, false);
            headerListSize += PSEUDO_SCHEME.length() + (scheme != null ? scheme.length() : 0) + 32;
        }

        hpackEncoder.encodeHeader(encodeBuffer, PSEUDO_AUTHORITY, authority, false);
        headerListSize += PSEUDO_AUTHORITY.length() + authority.length() + 32;

        if (!isConnect) {
            hpackEncoder.encodeHeader(encodeBuffer, PSEUDO_PATH, path, false);
            headerListSize += PSEUDO_PATH.length() + path.length() + 32;
        }

        // Encode regular headers
        for (var entry : request.headers()) {
            String name = entry.getKey();
            if (CONNECTION_HEADERS.contains(name)) {
                continue;
            }
            boolean isTe = "te".equals(name);
            boolean sensitive = SENSITIVE_HEADERS.contains(name);
            for (String value : entry.getValue()) {
                // RFC 9113 Section 8.2.1: TE header may only contain "trailers"
                if (isTe && !"trailers".equalsIgnoreCase(value)) {
                    continue;
                }
                hpackEncoder.encodeHeader(encodeBuffer, name, value, sensitive);
                headerListSize += name.length() + value.length() + 32;
            }
        }

        // Validate header list size per RFC 9113 Section 10.5.1
        if (maxHeaderListSize != Integer.MAX_VALUE && headerListSize > maxHeaderListSize) {
            throw new IOException(
                    "Header list size (" + headerListSize + ") exceeds limit (" + maxHeaderListSize + ")");
        }
    }

    /**
     * Encode trailer headers into the internal buffer.
     *
     * <p>After calling this method, use {@link #buffer()} and {@link #size()} to access the encoded data.
     *
     * @param trailers the trailer headers
     * @throws IOException if encoding fails or trailers contain pseudo-headers
     */
    void encodeTrailers(HttpHeaders trailers) throws IOException {
        encodeBuffer.reset();
        hpackEncoder.beginHeaderBlock(encodeBuffer);

        for (var entry : trailers) {
            String name = entry.getKey();
            // RFC 9113 Section 8.1: Trailers MUST NOT contain pseudo-headers
            if (name.startsWith(":")) {
                throw new IOException("Trailers must not contain pseudo-header: " + name);
            }
            boolean sensitive = SENSITIVE_HEADERS.contains(name);
            for (String value : entry.getValue()) {
                hpackEncoder.encodeHeader(encodeBuffer, name, value, sensitive);
            }
        }
    }

    /**
     * Get the internal buffer containing encoded data.
     * Valid from index 0 to {@link #size()} - 1.
     *
     * @return the internal buffer array
     */
    byte[] buffer() {
        return encodeBuffer.array();
    }

    /**
     * Get the size of the encoded data in the buffer.
     *
     * @return number of valid bytes in {@link #buffer()}
     */
    int size() {
        return encodeBuffer.size();
    }

    /**
     * Build the :authority pseudo-header value.
     */
    private static String getAuthority(HttpRequest request) {
        String host = request.uri().getHost();
        int port = request.uri().getPort();
        String scheme = request.uri().getScheme();
        if (port == -1 || (port == 443 && "https".equalsIgnoreCase(scheme))
                || (port == 80 && "http".equalsIgnoreCase(scheme))) {
            return host;
        }
        return host + ":" + port;
    }

    /**
     * Build the :path pseudo-header value.
     */
    private static String getPath(HttpRequest request) {
        String path = request.uri().getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = request.uri().getRawQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }
        return path;
    }
}
