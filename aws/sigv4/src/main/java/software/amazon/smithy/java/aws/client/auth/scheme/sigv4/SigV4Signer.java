/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.client.core.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * AWS signature version 4 signing implementation.
 *
 * <p>TODO: Code still needs profiling and optimization
 */
final class SigV4Signer implements Signer<HttpRequest, AwsCredentialsIdentity> {
    static final SigV4Signer INSTANCE = new SigV4Signer();

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SigV4Signer.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMdd")
        .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneId.of("UTC"));
    private static final List<String> HEADERS_TO_IGNORE_IN_LOWER_CASE = List.of(
        "connection",
        "x-amzn-trace-id",
        "user-agent",
        "expect"
    );

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";

    private static final SigningCache SIGNER_CACHE = new SigningCache(300);

    // 512 matches the JavaSdkV2 settings
    private static final ThreadLocal<StringBuilder> STRING_BUILDER = ThreadLocal.withInitial(
        () -> new StringBuilder(512)
    );

    private SigV4Signer() {}

    @Override
    public CompletableFuture<HttpRequest> sign(
        HttpRequest request,
        AwsCredentialsIdentity identity,
        AuthProperties properties
    ) {
        var region = properties.expect(SigV4Settings.REGION);
        var name = properties.expect(SigV4Settings.SIGNING_NAME);
        var clock = properties.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());

        // TODO: Add support for query signing?
        // TODO: Support chunk encoding
        // TODO: support UNSIGNED

        return getPayloadHash(request.body()).thenApply(payloadHash -> {
            var sb = STRING_BUILDER.get();
            var signedHeaders = createSignedHeaders(
                sb,
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
                !request.body().hasKnownLength()
            );
            if (sb.length() > 1024) {
                sb.setLength(1024);
                sb.trimToSize();
            }
            sb.setLength(0);
            return request.toBuilder().headers(HttpHeaders.of(signedHeaders)).build();
        });
    }

    private static CompletableFuture<String> getPayloadHash(DataStream dataStream) {
        return dataStream.asByteBuffer().thenApply(SigV4Signer::hexHash);
    }

    private static String hexHash(ByteBuffer bytes) {
        return HexFormat.of().formatHex(hash(bytes));
    }

    private static Map<String, List<String>> createSignedHeaders(
        StringBuilder stringBuffer,
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
        // httpHeaders.map() must return all lowercase header names.
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(httpHeaders.map());

        // AWS4 requires a number of headers to be set before signing including 'Host' and 'X-Amz-Date'
        var hostHeader = uri.getHost();
        if (uriUsingNonStandardPort(uri)) {
            hostHeader += ':' + uri.getPort();
        }
        headers.put("host", List.of(hostHeader));

        var requestTime = TIME_FORMATTER.format(signingTimestamp);
        headers.put("x-amz-date", List.of(requestTime));

        if (isStreaming) {
            headers.put("x-amz-content-sha256", List.of(payloadHash));
        }
        if (sessionToken != null) {
            headers.put("x-amz-security-token", List.of(sessionToken));
        }

        // Determine sorted list of headers to sign
        var signedHeaders = getSignedHeaders(stringBuffer, headers.keySet());

        // Build canonicalRequest and compute its signature
        var canonicalRequest = getCanonicalRequest(
            stringBuffer,
            method,
            uri,
            headers,
            headers.keySet(),
            signedHeaders,
            payloadHash
        );
        var dateStamp = DATE_FORMATTER.format(signingTimestamp);
        var scope = dateStamp + '/' + regionName + '/' + serviceName + '/' + TERMINATOR;
        var signingKey = deriveSigningKey(
            secretAccessKey,
            dateStamp,
            regionName,
            serviceName,
            signingTimestamp
        );
        var signature = computeSignature(stringBuffer, canonicalRequest, scope, requestTime, signingKey);

        var authorizationHeader = getAuthHeader(stringBuffer, accessKeyId, scope, signedHeaders, signature);
        headers.put("authorization", List.of(authorizationHeader));

        return headers;
    }

    private static boolean uriUsingNonStandardPort(URI uri) {
        if (uri.getPort() == -1) {
            return false;
        }
        return switch (uri.getScheme()) {
            case "http" -> uri.getPort() != 80;
            case "https" -> uri.getPort() != 443;
            default -> throw new IllegalStateException("Unexpected value for URI scheme: " + uri.getScheme());
        };
    }

    private static String getSignedHeaders(StringBuilder builder, Set<String> sortedHeaderKeys) {
        builder.setLength(0);
        for (var header : sortedHeaderKeys) {
            if (HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(header)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(header);
        }
        return builder.toString();
    }

    private static String getAuthHeader(
        StringBuilder builder,
        String accessKeyId,
        String scope,
        String signedHeaderBuilder,
        String signature
    ) {
        builder.setLength(0);
        builder.append(ALGORITHM)
            .append(' ')
            .append("Credential=")
            .append(accessKeyId)
            .append('/')
            .append(scope)
            .append(", ")
            .append("SignedHeaders=")
            .append(signedHeaderBuilder)
            .append(", ")
            .append("Signature=")
            .append(signature);
        return builder.toString();
    }

    private static byte[] getCanonicalRequest(
        StringBuilder builder,
        String method,
        URI uri,
        Map<String, List<String>> headers,
        Set<String> sortedHeaderKeys,
        String signedHeaders,
        String payloadHash
    ) {
        builder.setLength(0);
        builder.append(method).append('\n');
        addCanonicalizedResourcePath(uri, builder);
        builder.append('\n');
        addCanonicalizedQueryString(uri, builder);
        builder.append('\n');
        addCanonicalizedHeaderString(headers, sortedHeaderKeys, builder);
        builder.append('\n');
        builder.append(signedHeaders)
            .append('\n')
            .append(payloadHash);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
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
    private static byte[] deriveSigningKey(
        String secretKey,
        String dateStamp,
        String regionName,
        String serviceName,
        Instant instant
    ) {
        var cacheKey = new SigningCache.CacheKey(secretKey, regionName, serviceName);
        SigningKey signingKey = SIGNER_CACHE.get(cacheKey);
        if (signingKey != null && signingKey.isValidFor(instant)) {
            return signingKey.signingKey();
        }
        LOGGER.trace("Generating new key as signing key could not be found in cache.");
        byte[] key = newSigningKey(secretKey, dateStamp, regionName, serviceName);
        SIGNER_CACHE.put(cacheKey, new SigningKey(key, instant));
        return key;
    }

    private static byte[] newSigningKey(
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

    private static String computeSignature(
        StringBuilder builder,
        byte[] canonicalRequest,
        String scope,
        String requestTime,
        byte[] signingKey
    ) {
        builder.setLength(0);
        builder.append(ALGORITHM)
            .append('\n')
            .append(requestTime)
            .append('\n')
            .append(scope)
            .append('\n')
            .append(HexFormat.of().formatHex(hash(canonicalRequest)));
        return HexFormat.of().formatHex(sign(builder.toString(), signingKey));
    }

    private static byte[] sign(String data, byte[] key) {
        try {
            var mac = SigningAlgorithm.HMAC_SHA256.getMac();
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hash(ByteBuffer data) {
        var md = DigestAlgorithm.SHA256.getDigest();
        md.update(data);
        return md.digest();
    }

    private static byte[] hash(byte[] data) {
        var md = DigestAlgorithm.SHA256.getDigest();
        md.update(data);
        return md.digest();
    }
}
