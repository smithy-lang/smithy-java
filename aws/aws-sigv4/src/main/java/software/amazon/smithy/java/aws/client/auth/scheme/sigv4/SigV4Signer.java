/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * AWS signature version 4 signing implementation.
 */
final class SigV4Signer implements Signer<HttpRequest, AwsCredentialsIdentity> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SigV4Signer.class);
    private static final List<String> HEADERS_TO_IGNORE_IN_LOWER_CASE = List.of(
            "connection",
            "x-amzn-trace-id",
            "user-agent",
            "expect");

    private static final int POOL_SIZE = 32;
    private static final int BUFFER_SIZE = 512;
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final SigningCache SIGNER_CACHE = new SigningCache(300);

    private static final class SigningResources {
        final StringBuilder sb;
        final MessageDigest sha256Digest;
        final Mac sha256Mac;

        SigningResources() {
            this.sb = new StringBuilder(BUFFER_SIZE);
            try {
                this.sha256Digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Unable to fetch message digest instance for SHA-256", e);
            }
            try {
                this.sha256Mac = Mac.getInstance(HMAC_SHA_256);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Unable to fetch Mac instance for HmacSHA256", e);
            }
        }

        void reset() {
            sb.setLength(0);
            sha256Digest.reset();
            sha256Mac.reset();
        }
    }

    private static final Pool<SigningResources> RESOURCES_POOL = new Pool<>(POOL_SIZE, SigningResources::new);

    private final SigningResources signingResources;

    public static SigV4Signer create() {
        return new SigV4Signer();
    }

    private SigV4Signer() {
        this.signingResources = RESOURCES_POOL.get();
        signingResources.reset();
    }

    @Override
    public void close() {
        RESOURCES_POOL.release(signingResources);
    }

    @Override
    public HttpRequest sign(HttpRequest request, AwsCredentialsIdentity identity, Context properties) {
        var region = properties.expect(SigV4Settings.REGION);
        var name = properties.expect(SigV4Settings.SIGNING_NAME);
        var clock = properties.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());

        // TODO: Add support for query signing?
        // TODO: Support chunk encoding
        // TODO: support UNSIGNED

        var payloadHash = getPayloadHash(request.body());
        var signedHeaders = createSignedHeaders(
                request.method(),
                request.uri(),
                request.headers(),
                payloadHash,
                region,
                name,
                clock.instant(),
                identity.accessKeyId(),
                identity.secretAccessKey(),
                identity.sessionToken(),
                !request.body().hasKnownLength());
        // Don't let the cached buffers grow too large.
        var sb = signingResources.sb;
        if (sb.length() > BUFFER_SIZE) {
            sb.setLength(BUFFER_SIZE);
            sb.trimToSize();
        }
        sb.setLength(0);
        return request.toBuilder().headers(HttpHeaders.of(signedHeaders)).build();
    }

    private String getPayloadHash(DataStream dataStream) {
        return hexHash(dataStream.asByteBuffer());
    }

    private String hexHash(ByteBuffer bytes) {
        return HexFormat.of().formatHex(hash(bytes));
    }

    private Map<String, List<String>> createSignedHeaders(
            String method,
            URI uri,
            HttpHeaders httpHeaders,
            String payloadHash,
            String regionName,
            String serviceName,
            Instant signingTimestamp,
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            boolean isStreaming
    ) {
        var headers = copyHeaders(httpHeaders);

        // AWS4 requires a number of headers to be set before signing including 'Host' and 'X-Amz-Date'
        var hostHeader = uriUsingStandardPort(uri) ? uri.getHost() + ':' + uri.getPort() : uri.getHost();
        headers.put("host", List.of(hostHeader));

        var sb = signingResources.sb;
        var signingDate = signingTimestamp.atOffset(ZoneOffset.UTC).toLocalDateTime();
        var dateStamp = formatDate(signingDate, sb);
        var requestTime = formatRfc3339(signingDate, dateStamp, sb);
        headers.put("x-amz-date", List.of(requestTime));

        if (isStreaming) {
            headers.put("x-amz-content-sha256", List.of(payloadHash));
        }
        if (sessionToken != null) {
            headers.put("x-amz-security-token", List.of(sessionToken));
        }

        // Determine sorted list of headers to sign
        var signedHeaders = getSignedHeaders(headers.keySet(), sb);

        // Build canonicalRequest and compute its signature
        var canonicalRequest = getCanonicalRequest(
                method,
                uri,
                headers,
                headers.keySet(),
                signedHeaders,
                payloadHash,
                sb);

        var signingKey = deriveSigningKey(
                secretAccessKey,
                dateStamp,
                regionName,
                serviceName,
                signingTimestamp);
        var scope = createScope(dateStamp, regionName, serviceName, sb);
        var signature = computeSignature(canonicalRequest, scope, requestTime, signingKey, sb);

        var authorizationHeader = getAuthHeader(accessKeyId, scope, signedHeaders, signature, sb);
        headers.put("authorization", List.of(authorizationHeader));

        return headers;
    }

    private static String createScope(String dateStamp, String regionName, String serviceName, StringBuilder sb) {
        sb.setLength(0);
        sb.append(dateStamp).append('/');
        sb.append(regionName).append('/');
        sb.append(serviceName).append('/');
        sb.append(TERMINATOR);
        return sb.toString();
    }

    private static Map<String, List<String>> copyHeaders(HttpHeaders httpHeaders) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Note: httpHeaders.map _should_ always return lowercase key names.
        for (var entry : httpHeaders.map().entrySet()) {
            headers.put(entry.getKey().toLowerCase(Locale.ENGLISH), entry.getValue());
        }
        return headers;
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

    private static boolean uriUsingStandardPort(URI uri) {
        return switch (uri.getPort()) {
            case 80 -> uri.getScheme().equals("http");
            case 443 -> uri.getScheme().equals("https");
            default -> false;
        };
    }

    private static String getSignedHeaders(Set<String> sortedHeaderKeys, StringBuilder sb) {
        sb.setLength(0);
        for (var header : sortedHeaderKeys) {
            if (!HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(header)) {
                sb.append(header).append(';');
            }
        }
        // Remove the trailing ";".
        sb.setLength(sb.length() - 1);
        return sb.toString();
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

    private byte[] getCanonicalRequest(
            String method,
            URI uri,
            Map<String, List<String>> headers,
            Set<String> sortedHeaderKeys,
            String signedHeaders,
            String payloadHash,
            StringBuilder sb
    ) {
        sb.setLength(0);
        sb.append(method).append('\n');
        addCanonicalizedResourcePath(uri, sb);
        sb.append('\n');
        addCanonicalizedQueryString(uri, sb);
        sb.append('\n');
        addCanonicalizedHeaderString(headers, sortedHeaderKeys, sb);
        sb.append('\n');
        sb.append(signedHeaders)
                .append('\n')
                .append(payloadHash);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void addCanonicalizedResourcePath(URI uri, StringBuilder builder) {
        String path = uri.normalize().getRawPath();
        if (path.isEmpty()) {
            builder.append('/');
            return;
        }
        if (!path.startsWith("/")) {
            path = '/' + path;
        }
        URLEncoding.encodeUnreserved(path, builder, true);
    }

    private static void addCanonicalizedQueryString(URI uri, StringBuilder builder) {
        // Getting the raw query means the keys and values don't need to be encoded again.
        var query = uri.getRawQuery();
        if (query == null) {
            return;
        }

        SortedMap<String, String> sorted = new TreeMap<>();
        var params = query.split("&");

        for (var param : params) {
            var keyVal = param.split("=");
            var key = keyVal[0];
            var encodedKey = URLEncoding.encodeUnreserved(key, false);
            if (keyVal.length == 2) {
                var encodedValue = URLEncoding.encodeUnreserved(keyVal[1], false);
                sorted.put(encodedKey, encodedValue);
            } else {
                sorted.put(key, "");
            }
        }

        for (var entry : sorted.entrySet()) {
            builder.append(entry.getKey());
            builder.append('=');
            builder.append(entry.getValue());
            builder.append('&');
        }

        // Remove the trailing '&'.
        builder.setLength(builder.length() - 1);
    }

    private static void addCanonicalizedHeaderString(
            Map<String, List<String>> headers,
            Set<String> sortedHeaderKeys,
            StringBuilder builder
    ) {
        for (var headerKey : sortedHeaderKeys) {
            if (HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(headerKey)) {
                continue;
            }
            builder.append(headerKey);
            builder.append(':');
            for (String headerValue : headers.get(headerKey)) {
                addAndTrim(builder, headerValue);
                builder.append(',');
            }
            // Remove the trailing comma.
            builder.setLength(builder.length() - 1);
            builder.append('\n');
        }
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
            String scope,
            String requestTime,
            byte[] signingKey,
            StringBuilder sb
    ) {
        sb.setLength(0);
        sb.append(ALGORITHM)
                .append('\n')
                .append(requestTime)
                .append('\n')
                .append(scope)
                .append('\n')
                .append(HexFormat.of().formatHex(hash(canonicalRequest)));
        var toSign = sb.toString();
        return HexFormat.of().formatHex(sign(toSign, signingKey));
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

    private byte[] hash(ByteBuffer data) {
        var sha256Digest = signingResources.sha256Digest;
        sha256Digest.reset();
        sha256Digest.update(data);
        return sha256Digest.digest();
    }

    private byte[] hash(byte[] data) {
        var sha256Digest = signingResources.sha256Digest;
        sha256Digest.reset();
        sha256Digest.update(data);
        return sha256Digest.digest();
    }
}
