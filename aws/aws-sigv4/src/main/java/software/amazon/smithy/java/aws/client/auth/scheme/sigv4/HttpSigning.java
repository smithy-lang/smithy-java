/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.io.uri.URLEncoding;

final class HttpSigning {

    static final String TERMINATOR = "aws4_request";
    static final byte[] TERMINATOR_BYTES = TERMINATOR.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGNED_HEADERS_PREFIX = ", SignedHeaders=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGNATURE_PREFIX = ", Signature=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EMPTY_BODY_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".getBytes(StandardCharsets.US_ASCII);

    private HttpSigning() {}

    /**
     * Returns the supplied payload hash, or {@code null} after writing the computed hash to {@link SigningResources#payloadHashHex}.
     */
    static String resolvePayloadHash(HttpRequest request, Context properties, SigningResources r) {
        String payloadHash = properties.get(SigV4Settings.PAYLOAD_HASH_OVERRIDE);
        if (payloadHash != null) {
            return payloadHash;
        }
        payloadHash = request.headers().firstValue(HeaderName.X_AMZ_CONTENT_SHA256);
        if (payloadHash != null) {
            return payloadHash;
        }
        var body = request.body();
        if (body == null || body.contentLength() == 0) {
            System.arraycopy(EMPTY_BODY_HASH, 0, r.payloadHashHex, 0, 64);
        } else if (!body.hasKnownLength()) {
            throw new UnsupportedOperationException(
                    "Cannot sign a body whose length is unknown without UNSIGNED-PAYLOAD "
                            + "or chunked signing, neither of which is implemented yet.");
        } else {
            r.hash(body, r.hashBytes);
            SigningResources.writeHex(r.hashBytes, r.payloadHashHex, 0);
        }
        return null;
    }

    /**
     * Replace signer-owned headers before canonicalization so the signature covers the headers sent on the wire.
     *
     * @param payloadHash the value from {@link #resolvePayloadHash}, or {@code null} to use the hashed body.
     */
    static void canonicalize(
            ModifiableHttpRequest request,
            Context properties,
            SigningResources r,
            String payloadHash,
            String sessionToken,
            boolean isS3
    ) {
        boolean omitSecurityToken = properties.getOrDefault(SigV4Settings.OMIT_SECURITY_TOKEN, false);
        boolean normalizePath = SigningParameters.normalizePath(properties, isS3);
        boolean doubleEncode = SigningParameters.doubleEncodePath(properties, isS3);
        r.timestamp(Math.floorDiv(SigningParameters.clock(properties).millis(), 1000L));

        // S3 requires x-amz-content-sha256 on the wire and in the signature. Other services reject it in the
        // signed-header set, so it is only added for S3.
        if (isS3 && payloadHash == null) {
            payloadHash = new String(r.payloadHashHex, 0, 64, StandardCharsets.ISO_8859_1);
        }
        String requestTime = r.requestTime;
        var headers = request.headers();
        headers.setHeader(HeaderName.X_AMZ_DATE, requestTime);
        if (!omitSecurityToken && sessionToken != null) {
            headers.setHeader(HeaderName.X_AMZ_SECURITY_TOKEN, sessionToken);
        }
        if (isS3) {
            headers.setHeader(HeaderName.X_AMZ_CONTENT_SHA256, payloadHash);
        }

        collectHeaders(request, r, requestTime);
        buildCanonicalRequest(request.method(), request.uri(), r, payloadHash, normalizePath, doubleEncode);
    }

    /**
     * @param region the scope's region, or {@code null} for SigV4a, whose scope is {@code date/service/aws4_request}.
     */
    static int stringToSign(SigningResources r, byte[] algorithm, String region, String service) {
        r.hash(r.out, 0, r.outLength, r.hashBytes);
        int needed = algorithm.length + 1 + 16 + 1 + scopeLength(region, service) + 1 + 64;
        if (r.stringToSignBytes.length < needed) {
            r.stringToSignBytes = new byte[Integer.highestOneBit(needed) << 1];
        }
        byte[] dst = r.stringToSignBytes;
        System.arraycopy(algorithm, 0, dst, 0, algorithm.length);
        int pos = algorithm.length;
        dst[pos++] = '\n';
        pos = SigningResources.writeAscii(r.requestTime, dst, pos);
        dst[pos++] = '\n';
        pos = writeScope(r, region, service, dst, pos);
        dst[pos++] = '\n';
        pos = SigningResources.writeHex(r.hashBytes, dst, pos);
        r.stringToSignLength = pos;
        return pos;
    }

    static SignResult<HttpRequest> authorize(
            ModifiableHttpRequest request,
            SigningResources r,
            byte[] credentialPrefix,
            String accessKeyId,
            String region,
            String service,
            int signatureLength
    ) {
        int needed = credentialPrefix.length + accessKeyId.length()
                + 1
                + scopeLength(region, service)
                + SIGNED_HEADERS_PREFIX.length
                + r.signedHeadersLength
                + SIGNATURE_PREFIX.length
                + signatureLength;
        if (r.authorizationBytes.length < needed) {
            r.authorizationBytes = new byte[Integer.highestOneBit(needed) << 1];
        }
        byte[] dst = r.authorizationBytes;
        System.arraycopy(credentialPrefix, 0, dst, 0, credentialPrefix.length);
        int pos = credentialPrefix.length;
        pos = SigningResources.writeAscii(accessKeyId, dst, pos);
        dst[pos++] = '/';
        pos = writeScope(r, region, service, dst, pos);
        System.arraycopy(SIGNED_HEADERS_PREFIX, 0, dst, pos, SIGNED_HEADERS_PREFIX.length);
        pos += SIGNED_HEADERS_PREFIX.length;
        System.arraycopy(r.signedHeadersBytes, 0, dst, pos, r.signedHeadersLength);
        pos += r.signedHeadersLength;
        System.arraycopy(SIGNATURE_PREFIX, 0, dst, pos, SIGNATURE_PREFIX.length);
        pos += SIGNATURE_PREFIX.length;
        System.arraycopy(r.signatureHex, 0, dst, pos, signatureLength);
        pos += signatureLength;
        request.headers().setHeader(HeaderName.AUTHORIZATION, new String(dst, 0, pos, StandardCharsets.ISO_8859_1));
        return new SignResult<>(request, new String(r.signatureHex, 0, signatureLength, StandardCharsets.ISO_8859_1));
    }

    private static int scopeLength(String region, String service) {
        int regionLength = region == null ? 0 : region.length() + 1;
        return 8 + 1 + regionLength + service.length() + 1 + TERMINATOR_BYTES.length;
    }

    private static int writeScope(SigningResources r, String region, String service, byte[] dst, int pos) {
        pos = SigningResources.writeAscii(r.dateStamp, dst, pos);
        dst[pos++] = '/';
        if (region != null) {
            pos = SigningResources.writeAscii(region, dst, pos);
            dst[pos++] = '/';
        }
        pos = SigningResources.writeAscii(service, dst, pos);
        dst[pos++] = '/';
        System.arraycopy(TERMINATOR_BYTES, 0, dst, pos, TERMINATOR_BYTES.length);
        return pos + TERMINATOR_BYTES.length;
    }

    private static void collectHeaders(ModifiableHttpRequest request, SigningResources r, String requestTime) {
        r.headerCount = 0;
        var headers = request.headers();
        headers.forEachEntry(r, HttpSigning::addExistingHeader);
        if (!headers.hasHeader(HeaderName.HOST)) {
            r.addHeaderCanonical(HeaderName.HOST.name(), hostHeaderValue(request.uri()));
        }
        // addHeaderForSigning excludes x-amz-date, so add it separately.
        r.addHeaderCanonical(HeaderName.X_AMZ_DATE.name(), requestTime);
        r.sortHeadersByName();
        buildSignedHeaders(r);
    }

    private static void addExistingHeader(SigningResources r, String name, String value) {
        r.addHeaderForSigning(name, value);
    }

    private static String hostHeaderValue(SmithyUri uri) {
        return uriUsesDefaultPort(uri) ? uri.getHost() : uri.getHost() + ':' + uri.getPort();
    }

    private static boolean uriUsesDefaultPort(SmithyUri uri) {
        int port = uri.getPort();
        if (port == -1) {
            return true;
        }

        return switch (port) {
            case 80 -> "http".equals(uri.getScheme());
            case 443 -> "https".equals(uri.getScheme());
            default -> false;
        };
    }

    private static void buildSignedHeaders(SigningResources r) {
        int len = 0;
        for (int i = 0; i < r.headerCount; i++) {
            len += r.headers[i * 2].length() + 1;
        }
        if (r.signedHeadersBytes.length < len) {
            r.signedHeadersBytes = new byte[Integer.highestOneBit(len) << 1];
        }
        byte[] dst = r.signedHeadersBytes;
        int pos = 0;
        String previous = null;
        for (int i = 0; i < r.headerCount; i++) {
            String name = r.headers[i * 2];
            if (name.equals(previous)) {
                continue;
            }
            if (pos > 0) {
                dst[pos++] = ';';
            }
            pos = SigningResources.writeAscii(name, dst, pos);
            previous = name;
        }
        r.signedHeadersLength = pos;
    }

    private static void buildCanonicalRequest(
            String method,
            SmithyUri uri,
            SigningResources r,
            String payloadHash,
            boolean normalizePath,
            boolean doubleEncode
    ) {
        r.beginOutput();
        r.putAscii(method);
        r.put((byte) '\n');
        addCanonicalizedResourcePath(uri, r, normalizePath, doubleEncode);
        r.put((byte) '\n');
        addCanonicalizedQueryString(uri, r);
        r.put((byte) '\n');
        addCanonicalizedHeaders(r);
        r.put((byte) '\n');
        r.put(r.signedHeadersBytes, 0, r.signedHeadersLength);
        r.put((byte) '\n');
        if (payloadHash != null) {
            r.putAscii(payloadHash);
        } else {
            r.put(r.payloadHashHex, 0, 64);
        }
    }

    /**
     * Walk the sorted strided buffer, emitting one canonical-header line per distinct name,
     * comma-joining the values of multi-valued headers (which the sort placed consecutively).
     */
    private static void addCanonicalizedHeaders(SigningResources r) {
        int i = 0;
        while (i < r.headerCount) {
            String name = r.headers[i * 2];
            int next = i + 1;
            while (next < r.headerCount && name.equals(r.headers[next * 2])) {
                next++;
            }
            r.putAscii(name);
            r.put((byte) ':');
            for (int j = i; j < next; j++) {
                if (j > i) {
                    r.put((byte) ',');
                }
                r.putHeaderValue(r.headers[j * 2 + 1]);
            }
            r.put((byte) '\n');
            i = next;
        }
    }

    private static void addCanonicalizedResourcePath(
            SmithyUri uri,
            SigningResources r,
            boolean normalizePath,
            boolean doubleEncode
    ) {
        String raw = uri.getPath();
        if (raw == null || raw.isEmpty()) {
            r.put((byte) '/');
            return;
        }
        if (isCanonicalPath(raw)) {
            if (raw.charAt(0) != '/') {
                r.put((byte) '/');
            }
            r.putAscii(raw);
            return;
        }
        String path = normalizePath ? uri.getNormalizedPath() : raw;
        if (path == null || path.isEmpty()) {
            r.put((byte) '/');
            return;
        }
        if (path.charAt(0) != '/') {
            r.put((byte) '/');
        }
        if (doubleEncode) {
            var sb = r.sb;
            sb.setLength(0);
            URLEncoding.encodeUnreserved(path, sb, true);
            r.putAscii(sb);
        } else {
            putPath(path, r);
        }
    }

    private static void putPath(String path, SigningResources r) {
        byte[] latin1 = SigningResources.latin1Bytes(path);
        if (latin1 != null) {
            for (byte b : latin1) {
                if (b < 0) {
                    byte[] utf8 = path.getBytes(StandardCharsets.UTF_8);
                    r.put(utf8, 0, utf8.length);
                    return;
                }
            }
            r.put(latin1, 0, latin1.length);
            return;
        }
        byte[] utf8 = path.getBytes(StandardCharsets.UTF_8);
        r.put(utf8, 0, utf8.length);
    }

    private static boolean isCanonicalPath(String path) {
        char previous = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/') {
                if (previous == '/') {
                    return false;
                }
            } else if (c == '.' || !URLEncoding.isUnreserved(c)) {
                return false;
            }
            previous = c;
        }
        return true;
    }

    /**
     * Emit the canonical query-string component per the SigV4 spec.
     *
     * <p>Splits the raw query on {@code &}, optionally re-encodes each name/value pair,
     * sorts by encoded name (then encoded value), and concatenates. The strided pair buffer
     * is pooled in {@link SigningResources} to avoid per-call ArrayList allocations.
     *
     * <p>Fast path: if the entire query string is already canonically encoded (every char is
     * unreserved or part of an uppercase {@code %XX} escape), the substring values are used
     * directly without decode+re-encode. smithy-java's own {@code QueryStringBuilder} produces
     * canonical output, so this is the typical case for codegen-generated requests.
     */
    private static void addCanonicalizedQueryString(SmithyUri uri, SigningResources r) {
        r.queryPairCount = 0;

        var query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return;
        }

        boolean canonical = isAlreadyCanonical(query);
        int len = query.length();
        if (canonical) {
            int amp = query.indexOf('&');
            if (amp == -1) {
                appendSingleCanonicalQueryPair(query, r);
                return;
            }
        }

        int start = 0;
        while (start <= len) {
            int amp = query.indexOf('&', start);
            int end = amp == -1 ? len : amp;
            int eq = query.indexOf('=', start, end);
            String rawKey;
            String rawValue;
            if (eq == -1) {
                rawKey = query.substring(start, end);
                rawValue = "";
            } else {
                rawKey = query.substring(start, eq);
                rawValue = query.substring(eq + 1, end);
            }
            if (!rawKey.isEmpty()) {
                if (canonical) {
                    r.addQueryPair(rawKey, rawValue);
                } else {
                    r.addQueryPair(
                            URLEncoding.encodeUnreserved(URLEncoding.urlDecode(rawKey), false),
                            URLEncoding.encodeUnreserved(URLEncoding.urlDecode(rawValue), false));
                }
            }
            if (amp == -1) {
                break;
            }
            start = amp + 1;
        }

        if (r.queryPairCount == 0) {
            return;
        }

        r.sortQueryPairs();

        for (int i = 0; i < r.queryPairCount; i++) {
            if (i > 0) {
                r.put((byte) '&');
            }
            r.putAscii(r.queryPairs[i * 2]);
            r.put((byte) '=');
            r.putAscii(r.queryPairs[i * 2 + 1]);
        }
    }

    private static void appendSingleCanonicalQueryPair(String query, SigningResources r) {
        int eq = query.indexOf('=');
        if (eq == 0) {
            return;
        }

        r.putAscii(query);
        if (eq == -1) {
            r.put((byte) '=');
        }
    }

    // Returns true if every character in the query string is either an RFC 3986 unreserved
    // char or part of an already-uppercase {@code %XX} escape
    private static boolean isAlreadyCanonical(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '&' || c == '=') {
                continue;
            }
            if (URLEncoding.isUnreserved(c)) {
                continue;
            }
            if (c == '%' && i + 2 < len && isUppercaseHex(s.charAt(i + 1)) && isUppercaseHex(s.charAt(i + 2))) {
                i += 2;
                continue;
            }
            return false;
        }

        return true;
    }

    private static boolean isUppercaseHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
    }
}
