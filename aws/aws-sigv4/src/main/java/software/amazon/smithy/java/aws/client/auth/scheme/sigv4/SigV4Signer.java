/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.InvalidKeyException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.spec.SecretKeySpec;
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
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final SigningCache SIGNER_CACHE = new SigningCache(300);
    private static final String EMPTY_BODY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final byte[] HEX_DIGITS = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    private final SigningResources signingResources;

    public static SigV4Signer create() {
        return new SigV4Signer();
    }

    private SigV4Signer() {
        this.signingResources = SigningResources.get();
        signingResources.reset();
    }

    @Override
    public void close() {
        SigningResources.release(signingResources);
    }

    @Override
    public SignResult<HttpRequest> sign(HttpRequest request, AwsCredentialsIdentity identity, Context properties) {
        // Endpoint-emitted authSchemes property overrides the scheme-level signingName/signingRegion
        // when present (per the Endpoints 2.0 implementation SEP §"Endpoint Properties (authSchemes)").
        var name = properties.getOrDefault(EndpointAuthSchemeSettings.SIGNING_NAME,
                properties.expect(SigV4Settings.SIGNING_NAME));
        var region = properties.getOrDefault(EndpointAuthSchemeSettings.SIGNING_REGION,
                properties.expect(RegionSetting.REGION));
        var clock = properties.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());

        // TODO: Add support for query signing?

        // Caller can short-circuit the body SHA-256 by either:
        //   (a) stamping a sentinel string on SigV4Settings.PAYLOAD_HASH_OVERRIDE in the signer
        //       properties, or
        //   (b) pre-setting the x-amz-content-sha256 request header.
        // Both are used by aws-chunked + trailer-checksum requests where the body SHA-256 is
        // unnecessary. The expected sentinel values are defined by the AWS sigv4-streaming spec:
        // "STREAMING-UNSIGNED-PAYLOAD-TRAILER", "UNSIGNED-PAYLOAD", etc.
        String payloadHash;
        var hashOverride = properties.get(SigV4Settings.PAYLOAD_HASH_OVERRIDE);
        if (hashOverride != null) {
            payloadHash = hashOverride;
        } else {
            String existing = request.headers().firstValue(HeaderName.X_AMZ_CONTENT_SHA256);
            payloadHash = existing != null ? existing : getPayloadHash(request.body());
        }
        var mod = request.toModifiable();
        var signature = signInPlace(
                mod,
                payloadHash,
                region,
                name,
                clock.instant(),
                identity.accessKeyId(),
                identity.secretAccessKey(),
                identity.sessionToken());

        return new SignResult<>(mod, signature);
    }

    private String getPayloadHash(DataStream dataStream) {
        if (dataStream == null || dataStream.contentLength() == 0) {
            return EMPTY_BODY_HASH;
        }

        if (!dataStream.hasKnownLength()) {
            throw new UnsupportedOperationException(
                    "Cannot SigV4-sign a body whose length is unknown without UNSIGNED-PAYLOAD "
                            + "or chunked signing, neither of which is implemented yet.");
        }

        return hexHash(dataStream.asByteBuffer());
    }

    private String hexHash(ByteBuffer bytes) {
        return HexFormat.of().formatHex(hash(bytes));
    }

    private String signInPlace(
            ModifiableHttpRequest request,
            String payloadHash,
            String regionName,
            String serviceName,
            Instant signingTimestamp,
            String accessKeyId,
            String secretAccessKey,
            String sessionToken
    ) {
        var uri = request.uri();
        var method = request.method();

        // Collect every header into the reusable strided buffer.
        signingResources.headerCount = 0;
        request.headers().forEachEntry(signingResources, SigningResources::addHeader);

        // Append the port only for non-default, explicitly-set ports.
        var existingHeaders = request.headers();
        if (!existingHeaders.hasHeader(HeaderName.HOST)) {
            var hostHeader = uriUsesDefaultPort(uri) ? uri.getHost() : uri.getHost() + ':' + uri.getPort();
            signingResources.addHeader(HeaderName.HOST.name(), hostHeader);
        }

        var sb = signingResources.sb;
        var signingDate = signingTimestamp.atOffset(ZoneOffset.UTC).toLocalDateTime();
        var dateStamp = formatDate(signingDate, sb);
        var requestTime = formatRfc3339(signingDate, dateStamp, sb);
        // Use the request's existing X-Amz-Date if present so the canonical-request value
        // matches what's on the wire. Otherwise add the freshly-computed timestamp.
        String existingDate = existingHeaders.firstValue(HeaderName.X_AMZ_DATE);
        if (existingDate != null) {
            requestTime = existingDate;
        } else {
            signingResources.addHeader(HeaderName.X_AMZ_DATE.name(), requestTime);
        }

        if (sessionToken != null && !existingHeaders.hasHeader(HeaderName.X_AMZ_SECURITY_TOKEN)) {
            signingResources.addHeader(HeaderName.X_AMZ_SECURITY_TOKEN.name(), sessionToken);
        }

        // S3 requires x-amz-content-sha256 on the wire (and in the signature) but other services don't accept it as
        // part of the signed-header set, so add it conditionally. The header is signature-relevant: if it's in the
        // canonical request, it must also be on the wire (and vice-versa).
        boolean isS3 = "s3".equals(serviceName) || "s3express".equals(serviceName);
        if (isS3 && !existingHeaders.hasHeader(HeaderName.X_AMZ_CONTENT_SHA256)) {
            signingResources.addHeader(HeaderName.X_AMZ_CONTENT_SHA256.name(), payloadHash);
        }

        signingResources.sortHeadersByName();

        var signedHeaders = buildSignedHeadersString(signingResources, sb);
        int canonicalLen = buildCanonicalRequest(
                method,
                uri,
                signingResources,
                signedHeaders,
                payloadHash,
                sb);

        var signingKey = deriveSigningKey(secretAccessKey, dateStamp, regionName, serviceName, signingTimestamp);
        var scope = createScope(dateStamp, regionName, serviceName, sb);
        var signature = computeSignature(signingResources.canonicalRequestBytes,
                canonicalLen,
                scope,
                requestTime,
                signingKey);
        var authorizationHeader = getAuthHeader(accessKeyId, scope, signedHeaders, signature, sb);

        // Now mutate the actual request. setHeader is a single-key operation per header; no wholesale map replacement.
        var headers = request.headers();
        headers.setHeader(HeaderName.X_AMZ_DATE, requestTime);
        if (sessionToken != null) {
            headers.setHeader(HeaderName.X_AMZ_SECURITY_TOKEN, sessionToken);
        }

        if (isS3) {
            headers.setHeader(HeaderName.X_AMZ_CONTENT_SHA256, payloadHash);
        }

        headers.setHeader("authorization", authorizationHeader);
        return signature;
    }

    /**
     * Emit the SignedHeaders semicolon-joined list directly from the sorted strided buffer.
     * Skips ignored headers and dedupes consecutive duplicates produced by multi-valued headers.
     */
    private static String buildSignedHeadersString(SigningResources r, StringBuilder sb) {
        sb.setLength(0);
        String previous = null;

        for (int i = 0; i < r.headerCount; i++) {
            String name = r.headers[i * 2];
            if (name.equals(previous) || isIgnoredHeader(name)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(name);
            previous = name;
        }

        return sb.toString();
    }

    /**
     * Build the canonical request into the reusable byte buffer in {@link SigningResources}.
     * Returns the number of bytes written. The canonical request is ASCII, so each char in
     * the StringBuilder maps to one byte without UTF-8 encoding.
     */
    private static int buildCanonicalRequest(
            String method,
            SmithyUri uri,
            SigningResources r,
            String signedHeaders,
            String payloadHash,
            StringBuilder sb
    ) {
        sb.setLength(0);
        sb.append(method).append('\n');
        addCanonicalizedResourcePath(uri, sb);
        sb.append('\n');
        addCanonicalizedQueryString(uri, r, sb);
        sb.append('\n');
        addCanonicalizedHeaderString(r, sb);
        sb.append('\n');
        sb.append(signedHeaders).append('\n').append(payloadHash);

        int len = sb.length();
        byte[] dst = r.ensureCanonicalRequestCapacity(len);
        for (int i = 0; i < len; i++) {
            // Canonical request chars are ASCII (0x00-0x7F) by construction. Asserted-safe
            // narrowing cast skips the UTF-8 encoder allocations String.getBytes would do.
            dst[i] = (byte) sb.charAt(i);
        }
        return len;
    }

    /**
     * Walk the sorted strided buffer, emitting one canonical-header line per distinct name,
     * comma-joining the values of multi-valued headers (which the sort placed consecutively).
     */
    private static void addCanonicalizedHeaderString(SigningResources r, StringBuilder builder) {
        int i = 0;
        while (i < r.headerCount) {
            String name = r.headers[i * 2];
            int next = i + 1;
            while (next < r.headerCount && name.equals(r.headers[next * 2])) {
                next++;
            }
            if (isIgnoredHeader(name)) {
                i = next;
                continue;
            }
            builder.append(name).append(':');
            for (int j = i; j < next; j++) {
                addAndTrim(builder, r.headers[j * 2 + 1]);
                builder.append(',');
            }
            // Trim trailing comma.
            builder.setLength(builder.length() - 1);
            builder.append('\n');
            i = next;
        }
    }

    private static String createScope(String dateStamp, String regionName, String serviceName, StringBuilder sb) {
        sb.setLength(0);
        sb.append(dateStamp).append('/');
        sb.append(regionName).append('/');
        sb.append(serviceName).append('/');
        sb.append(TERMINATOR);
        return sb.toString();
    }

    // Formats the equivalent of "yyyyMMdd".
    private static String formatDate(LocalDateTime date, StringBuilder sb) {
        sb.setLength(0);
        sb.append(date.getYear());
        appendTwoDigits(date.getMonthValue(), sb);
        appendTwoDigits(date.getDayOfMonth(), sb);
        return sb.toString();
    }

    // Formats the equivalent of "yyyyMMdd'T'HHmmss'Z'".
    private static String formatRfc3339(LocalDateTime localDate, String dateString, StringBuilder sb) {
        sb.setLength(0);
        sb.append(dateString);
        sb.append('T');
        appendTwoDigits(localDate.getHour(), sb);
        appendTwoDigits(localDate.getMinute(), sb);
        appendTwoDigits(localDate.getSecond(), sb);
        sb.append('Z');
        return sb.toString();
    }

    private static void appendTwoDigits(int value, StringBuilder sb) {
        if (value < 10) {
            sb.append('0');
        }
        sb.append(value);
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

    private static String getAuthHeader(
            String accessKeyId,
            String scope,
            String signedHeaderBuilder,
            String signature,
            StringBuilder sb
    ) {
        sb.setLength(0);
        sb.append(ALGORITHM)
                .append(" Credential=")
                .append(accessKeyId)
                .append('/')
                .append(scope)
                .append(", SignedHeaders=")
                .append(signedHeaderBuilder)
                .append(", Signature=")
                .append(signature);
        return sb.toString();
    }

    private static void addCanonicalizedResourcePath(SmithyUri uri, StringBuilder builder) {
        String path = uri.getNormalizedPath();
        if (path == null || path.isEmpty()) {
            builder.append('/');
            return;
        }
        if (!path.startsWith("/")) {
            builder.append('/');
        }
        URLEncoding.encodeUnreserved(path, builder, true);
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
    private static void addCanonicalizedQueryString(SmithyUri uri, SigningResources r, StringBuilder builder) {
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
                appendSingleCanonicalQueryPair(query, builder);
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
                builder.append('&');
            }
            builder.append(r.queryPairs[i * 2]).append('=').append(r.queryPairs[i * 2 + 1]);
        }
    }

    private static void appendSingleCanonicalQueryPair(String query, StringBuilder builder) {
        int eq = query.indexOf('=');
        if (eq == 0) {
            return;
        }

        builder.append(query);
        if (eq == -1) {
            builder.append('=');
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

    private static void addAndTrim(StringBuilder result, String value) {
        for (int position = 0; position < value.length(); position++) {
            if (isWhiteSpace(value.charAt(position))) {
                addAndTrimSlow(result, value);
                return;
            }
        }
        result.append(value);
    }

    // Used when a header has any whitespace.
    private static void addAndTrimSlow(StringBuilder result, String value) {
        result.ensureCapacity(result.length() + value.length());

        // Trim leading whitespace.
        int position;
        for (position = 0; position < value.length(); position++) {
            if (!isWhiteSpace(value.charAt(position))) {
                break;
            }
        }

        // Convert "<WS><WS>" to "<SP>".
        boolean previousIsWhiteSpace = false;
        for (; position < value.length(); position++) {
            char ch = value.charAt(position);
            if (isWhiteSpace(ch)) {
                if (previousIsWhiteSpace) {
                    continue;
                }
                result.append(' ');
                previousIsWhiteSpace = true;
            } else {
                result.append(ch);
                previousIsWhiteSpace = false;
            }
        }

        // Trim trailing WS.
        if (!result.isEmpty() && result.charAt(result.length() - 1) == ' ') {
            result.setLength(result.length() - 1);
        }
    }

    // ws: ' ' | '\t' | '\n' | \u000b | \r | \f
    private static boolean isWhiteSpace(char ch) {
        return ch == ' ' || (ch >= '\t' && ch <= '\f');
    }

    private static boolean isIgnoredHeader(String name) {
        return switch (name) {
            case "connection", "content-length", "x-amzn-trace-id", "user-agent", "expect" -> true;
            default -> false;
        };
    }

    /**
     * AWS4 uses a series of derived keys, formed by hashing different pieces of data
     */
    private byte[] deriveSigningKey(
            String secretKey,
            String dateStamp,
            String regionName,
            String serviceName,
            Instant signingDate
    ) {
        var cacheKey = new SigningCache.CacheKey(secretKey, regionName, serviceName);
        SigningKey signingKey = SIGNER_CACHE.get(cacheKey);
        if (signingKey != null && signingKey.isValidFor(signingDate)) {
            return signingKey.signingKey();
        }
        LOGGER.trace("Generating new key as signing key could not be found in cache.");
        byte[] key = newSigningKey(secretKey, dateStamp, regionName, serviceName);
        SIGNER_CACHE.put(cacheKey, new SigningKey(key, signingDate));
        return key;
    }

    private byte[] newSigningKey(
            String secretKey,
            String dateStamp,
            String regionName,
            String serviceName
    ) {
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = sign(dateStamp, kSecret);
        var kRegion = sign(regionName, kDate);
        var kService = sign(serviceName, kRegion);
        return sign(TERMINATOR, kService);
    }

    private String computeSignature(
            byte[] canonicalRequest,
            int canonicalRequestLength,
            String scope,
            String requestTime,
            byte[] signingKey
    ) {
        byte[] canonicalRequestHash = hash(canonicalRequest, 0, canonicalRequestLength, signingResources.hashBytes);
        int stringToSignLength = ALGORITHM.length() + requestTime.length() + scope.length() + 67;
        byte[] stringToSign = signingResources.ensureStringToSignCapacity(stringToSignLength);

        int pos = writeAscii(ALGORITHM, stringToSign, 0);
        stringToSign[pos++] = '\n';
        pos = writeAscii(requestTime, stringToSign, pos);
        stringToSign[pos++] = '\n';
        pos = writeAscii(scope, stringToSign, pos);
        stringToSign[pos++] = '\n';
        pos = writeHex(canonicalRequestHash, stringToSign, pos);

        byte[] signatureBytes = sign(stringToSign, 0, pos, signingKey, signingResources.signatureBytes);
        byte[] signatureHex = signingResources.signatureHexBytes;
        writeHex(signatureBytes, signatureHex, 0);
        return new String(signatureHex, 0, signatureHex.length, StandardCharsets.US_ASCII);
    }

    private byte[] sign(String data, byte[] key) {
        try {
            var sha256Mac = signingResources.sha256Mac;
            sha256Mac.reset();
            sha256Mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return sha256Mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] sign(byte[] data, int offset, int length, byte[] key, byte[] output) {
        try {
            var sha256Mac = signingResources.sha256Mac;
            sha256Mac.reset();
            sha256Mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            sha256Mac.update(data, offset, length);
            sha256Mac.doFinal(output, 0);
            return output;
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (javax.crypto.ShortBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private static int writeAscii(String value, byte[] dst, int offset) {
        for (int i = 0; i < value.length(); i++) {
            dst[offset++] = (byte) value.charAt(i);
        }
        return offset;
    }

    private static int writeHex(byte[] bytes, byte[] dst, int offset) {
        for (byte b : bytes) {
            dst[offset++] = HEX_DIGITS[(b >>> 4) & 0x0F];
            dst[offset++] = HEX_DIGITS[b & 0x0F];
        }
        return offset;
    }

    private byte[] hash(ByteBuffer data) {
        var sha256Digest = signingResources.sha256Digest;
        sha256Digest.reset();
        sha256Digest.update(data);
        return sha256Digest.digest();
    }

    private byte[] hash(byte[] data, int offset, int length) {
        var sha256Digest = signingResources.sha256Digest;
        sha256Digest.reset();
        sha256Digest.update(data, offset, length);
        return sha256Digest.digest();
    }

    private byte[] hash(byte[] data, int offset, int length, byte[] output) {
        try {
            var sha256Digest = signingResources.sha256Digest;
            sha256Digest.reset();
            sha256Digest.update(data, offset, length);
            sha256Digest.digest(output, 0, output.length);
            return output;
        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
    }
}
