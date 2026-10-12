/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * AWS signature version 4 signing implementation.
 */
final class SigV4Signer implements Signer<HttpRequest, AwsCredentialsIdentity> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SigV4Signer.class);
    private static final String TERMINATOR = "aws4_request";
    private static final byte[] ALGORITHM = "AWS4-HMAC-SHA256".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TERMINATOR_BYTES = TERMINATOR.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CREDENTIAL_PREFIX = "AWS4-HMAC-SHA256 Credential=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGNED_HEADERS_PREFIX = ", SignedHeaders=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIGNATURE_PREFIX = ", Signature=".getBytes(StandardCharsets.US_ASCII);
    private static final SigningCache SIGNER_CACHE = new SigningCache(300);
    private static final byte[] EMPTY_BODY_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".getBytes(StandardCharsets.US_ASCII);

    private final SigningResources r;

    public static SigV4Signer create() {
        return new SigV4Signer();
    }

    private SigV4Signer() {
        this.r = SigningResources.get();
    }

    @Override
    public void close() {
        SigningResources.release(r);
    }

    @Override
    public SignResult<HttpRequest> sign(HttpRequest request, AwsCredentialsIdentity identity, Context properties) {
        // Endpoint-emitted authSchemes property overrides the scheme-level signingName/signingRegion
        // when present (per the Endpoints 2.0 implementation SEP §"Endpoint Properties (authSchemes)").
        var name = properties.get(EndpointAuthSchemeSettings.SIGNING_NAME);
        if (name == null) {
            name = properties.expect(SigV4Settings.SIGNING_NAME);
        }
        var region = properties.get(EndpointAuthSchemeSettings.SIGNING_REGION);
        if (region == null) {
            region = properties.expect(RegionSetting.REGION);
        }
        var clock = properties.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());
        boolean omitSecurityToken = properties.getOrDefault(SigV4Settings.OMIT_SECURITY_TOKEN, false);

        // TODO: Add support for query signing?

        r.timestamp(Math.floorDiv(clock.millis(), 1000L));

        // Caller can short-circuit the body SHA-256 by either:
        //   (a) stamping a sentinel string on SigV4Settings.PAYLOAD_HASH_OVERRIDE in the signer
        //       properties, or
        //   (b) pre-setting the x-amz-content-sha256 request header.
        // Both are used by aws-chunked + trailer-checksum requests where the body SHA-256 is
        // unnecessary. The expected sentinel values are defined by the AWS sigv4-streaming spec:
        // "STREAMING-UNSIGNED-PAYLOAD-TRAILER", "UNSIGNED-PAYLOAD", etc.
        String payloadHash = properties.get(SigV4Settings.PAYLOAD_HASH_OVERRIDE);
        if (payloadHash == null) {
            payloadHash = request.headers().firstValue(HeaderName.X_AMZ_CONTENT_SHA256);
            if (payloadHash == null) {
                computePayloadHash(request.body());
            }
        }

        var mod = request.toModifiable();
        var signature = signInPlace(
                mod,
                payloadHash,
                region,
                name,
                identity.accessKeyId(),
                identity.secretAccessKey(),
                identity.sessionToken(),
                omitSecurityToken);

        return new SignResult<>(mod, signature);
    }

    private void computePayloadHash(DataStream dataStream) {
        if (dataStream == null || dataStream.contentLength() == 0) {
            System.arraycopy(EMPTY_BODY_HASH, 0, r.payloadHashHex, 0, 64);
            return;
        }
        if (!dataStream.hasKnownLength()) {
            throw new UnsupportedOperationException(
                    "Cannot SigV4-sign a body whose length is unknown without UNSIGNED-PAYLOAD "
                            + "or chunked signing, neither of which is implemented yet.");
        }
        r.hash(dataStream.asByteBuffer(), r.hashBytes);
        SigningResources.writeHex(r.hashBytes, r.payloadHashHex, 0);
    }

    private String signInPlace(
            ModifiableHttpRequest request,
            String payloadHash,
            String regionName,
            String serviceName,
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            boolean omitSecurityToken
    ) {
        var uri = request.uri();
        var method = request.method();

        // Collect every header into the reusable strided buffer.
        r.headerCount = 0;
        request.headers().forEachEntry(r, SigV4Signer::addExistingHeaderForSigning);

        // Append the port only for non-default, explicitly-set ports.
        var existingHeaders = request.headers();
        if (!existingHeaders.hasHeader(HeaderName.HOST)) {
            var hostHeader = uriUsesDefaultPort(uri) ? uri.getHost() : uri.getHost() + ':' + uri.getPort();
            r.addHeaderCanonical(HeaderName.HOST.name(), hostHeader);
        }

        String requestTime = r.requestTime;
        r.addHeaderCanonical(HeaderName.X_AMZ_DATE.name(), requestTime);

        if (!omitSecurityToken
                && sessionToken != null
                && !existingHeaders.hasHeader(HeaderName.X_AMZ_SECURITY_TOKEN)) {
            r.addHeaderCanonical(HeaderName.X_AMZ_SECURITY_TOKEN.name(), sessionToken);
        }

        // S3 requires x-amz-content-sha256 on the wire (and in the signature) but other services don't accept it as
        // part of the signed-header set, so add it conditionally. The header is signature-relevant: if it's in the
        // canonical request, it must also be on the wire (and vice-versa).
        boolean isS3 = "s3".equals(serviceName) || "s3express".equals(serviceName);
        if (isS3) {
            if (payloadHash == null) {
                payloadHash = new String(r.payloadHashHex, 0, 64, StandardCharsets.ISO_8859_1);
            }
            if (!existingHeaders.hasHeader(HeaderName.X_AMZ_CONTENT_SHA256)) {
                r.addHeaderCanonical(HeaderName.X_AMZ_CONTENT_SHA256.name(), payloadHash);
            }
        }

        r.sortHeadersByName();

        buildSignedHeaders(r);
        buildCanonicalRequest(method, uri, r, payloadHash);

        var signingKey = deriveSigningKey(secretAccessKey, regionName, serviceName);
        int stringToSignLength = buildStringToSign(r, regionName, serviceName);
        r.hmac(signingKey, r.stringToSignBytes, 0, stringToSignLength, r.signatureBytes);
        SigningResources.writeHex(r.signatureBytes, r.signatureHexBytes, 0);
        var signature = new String(r.signatureHexBytes, 0, 64, StandardCharsets.ISO_8859_1);
        var authorizationHeader = buildAuthorization(r, accessKeyId, regionName, serviceName);

        // Now mutate the actual request. setHeader is a single-key operation per header; no wholesale map replacement.
        var headers = request.headers();
        headers.setHeader(HeaderName.X_AMZ_DATE, requestTime);
        if (!omitSecurityToken && sessionToken != null) {
            headers.setHeader(HeaderName.X_AMZ_SECURITY_TOKEN, sessionToken);
        }
        if (isS3) {
            headers.setHeader(HeaderName.X_AMZ_CONTENT_SHA256, payloadHash);
        }
        headers.setHeader("authorization", authorizationHeader);
        return signature;
    }

    private static void addExistingHeaderForSigning(SigningResources r, String name, String value) {
        r.addHeaderForSigning(name, value);
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

    private static void buildCanonicalRequest(String method, SmithyUri uri, SigningResources r, String payloadHash) {
        r.beginOutput();
        r.putAscii(method);
        r.put((byte) '\n');
        addCanonicalizedResourcePath(uri, r);
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

    private static int buildStringToSign(SigningResources r, String regionName, String serviceName) {
        r.hash(r.out, 0, r.outLength, r.hashBytes);
        int scopeLength = scopeLength(regionName, serviceName);
        int needed = ALGORITHM.length + 1 + 16 + 1 + scopeLength + 1 + 64;
        if (r.stringToSignBytes.length < needed) {
            r.stringToSignBytes = new byte[Integer.highestOneBit(needed) << 1];
        }
        byte[] dst = r.stringToSignBytes;
        System.arraycopy(ALGORITHM, 0, dst, 0, ALGORITHM.length);
        int pos = ALGORITHM.length;
        dst[pos++] = '\n';
        pos = SigningResources.writeAscii(r.requestTime, dst, pos);
        dst[pos++] = '\n';
        pos = writeScope(r, regionName, serviceName, dst, pos);
        dst[pos++] = '\n';
        return SigningResources.writeHex(r.hashBytes, dst, pos);
    }

    private static int scopeLength(String regionName, String serviceName) {
        return 8 + 1 + regionName.length() + 1 + serviceName.length() + 1 + TERMINATOR_BYTES.length;
    }

    private static int writeScope(SigningResources r, String regionName, String serviceName, byte[] dst, int pos) {
        pos = SigningResources.writeAscii(r.dateStamp, dst, pos);
        dst[pos++] = '/';
        pos = SigningResources.writeAscii(regionName, dst, pos);
        dst[pos++] = '/';
        pos = SigningResources.writeAscii(serviceName, dst, pos);
        dst[pos++] = '/';
        System.arraycopy(TERMINATOR_BYTES, 0, dst, pos, TERMINATOR_BYTES.length);
        return pos + TERMINATOR_BYTES.length;
    }

    private static String buildAuthorization(
            SigningResources r,
            String accessKeyId,
            String regionName,
            String serviceName
    ) {
        int needed = CREDENTIAL_PREFIX.length + accessKeyId.length()
                + 1
                + scopeLength(regionName, serviceName)
                + SIGNED_HEADERS_PREFIX.length
                + r.signedHeadersLength
                + SIGNATURE_PREFIX.length
                + 64;
        if (r.authorizationBytes.length < needed) {
            r.authorizationBytes = new byte[Integer.highestOneBit(needed) << 1];
        }
        byte[] dst = r.authorizationBytes;
        System.arraycopy(CREDENTIAL_PREFIX, 0, dst, 0, CREDENTIAL_PREFIX.length);
        int pos = CREDENTIAL_PREFIX.length;
        pos = SigningResources.writeAscii(accessKeyId, dst, pos);
        dst[pos++] = '/';
        pos = writeScope(r, regionName, serviceName, dst, pos);
        System.arraycopy(SIGNED_HEADERS_PREFIX, 0, dst, pos, SIGNED_HEADERS_PREFIX.length);
        pos += SIGNED_HEADERS_PREFIX.length;
        System.arraycopy(r.signedHeadersBytes, 0, dst, pos, r.signedHeadersLength);
        pos += r.signedHeadersLength;
        System.arraycopy(SIGNATURE_PREFIX, 0, dst, pos, SIGNATURE_PREFIX.length);
        pos += SIGNATURE_PREFIX.length;
        System.arraycopy(r.signatureHexBytes, 0, dst, pos, 64);
        pos += 64;
        return new String(dst, 0, pos, StandardCharsets.ISO_8859_1);
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

    private static void addCanonicalizedResourcePath(SmithyUri uri, SigningResources r) {
        String raw = uri.getPath();
        if (raw != null && !raw.isEmpty() && isCanonicalPath(raw)) {
            if (raw.charAt(0) != '/') {
                r.put((byte) '/');
            }
            r.putAscii(raw);
            return;
        }
        String path = uri.getNormalizedPath();
        if (path == null || path.isEmpty()) {
            r.put((byte) '/');
            return;
        }
        if (path.charAt(0) != '/') {
            r.put((byte) '/');
        }
        var sb = r.sb;
        sb.setLength(0);
        URLEncoding.encodeUnreserved(path, sb, true);
        r.putAscii(sb);
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

    private byte[] deriveSigningKey(String secretKey, String regionName, String serviceName) {
        byte[] memoized = r.memoizedSigningKey(secretKey, regionName, serviceName);
        if (memoized != null) {
            return memoized;
        }
        var cacheKey = new SigningCache.CacheKey(secretKey, regionName, serviceName);
        SigningKey signingKey = SIGNER_CACHE.get(cacheKey);
        byte[] key;
        if (signingKey != null && signingKey.isValidFor(r.cachedDays)) {
            key = signingKey.signingKey();
        } else {
            LOGGER.trace("Generating new key as signing key could not be found in cache.");
            key = newSigningKey(secretKey, regionName, serviceName);
            SIGNER_CACHE.put(cacheKey, new SigningKey(key, r.cachedDays));
        }
        r.memoizeSigningKey(secretKey, regionName, serviceName, key);
        return key;
    }

    private byte[] newSigningKey(String secretKey, String regionName, String serviceName) {
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = r.hmac(kSecret, r.dateStamp.getBytes(StandardCharsets.UTF_8));
        var kRegion = r.hmac(kDate, regionName.getBytes(StandardCharsets.UTF_8));
        var kService = r.hmac(kRegion, serviceName.getBytes(StandardCharsets.UTF_8));
        return r.hmac(kService, TERMINATOR_BYTES);
    }
}
