/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.auth.scheme.sigv4;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.smithy.java.aws.runtime.client.core.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.io.uri.URLEncoding;

/**
 * AWS signature version 4 signing implementation.
 *
 * <p>TODO: Code still needs profiling and optimization
 */
final class SigV4Signer implements Signer<SmithyHttpRequest, AwsCredentialsIdentity> {
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
    private static final String SHA_256 = "SHA-256";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";

    private static final SigningCache SIGNER_CACHE = new SigningCache(300);

    private SigV4Signer() {}

    @Override
    public CompletableFuture<SmithyHttpRequest> sign(
        SmithyHttpRequest request,
        AwsCredentialsIdentity identity,
        AuthProperties properties
    ) {
        var region = properties.expect(SigV4Settings.REGION);
        var name = properties.expect(SigV4Settings.SIGNING_NAME);
        var clock = properties.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());

        // TODO: Add support for query signing?
        // TODO: Support chunk encoding
        // TODO: support UNSIGNED

        return getPayloadHash(request.body())
            .thenApply(payloadHash -> {
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
                    !request.body().hasKnownLength()
                );
                return request.withHeaders(HttpHeaders.of(signedHeaders));
            });
    }

    private static CompletableFuture<String> getPayloadHash(DataStream dataStream) {
        return dataStream.asByteBuffer()
            .thenApply(SigV4Signer::hash)
            .thenApply(HexFormat.of()::formatHex);
    }

    private static Map<String, List<String>> createSignedHeaders(
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
        var headers = new HashMap<>(httpHeaders.map());

        // AWS4 requires a number of headers to be set before signing including 'Host' and 'X-Amz-Date'
        var hostHeader = uri.getHost();
        if (uriUsingNonStandardPort(uri)) {
            hostHeader += ":" + uri.getPort();
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
        Set<String> sortedHeaderKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedHeaderKeys.addAll(headers.keySet());
        var signedHeaders = getSignedHeaders(sortedHeaderKeys);

        // Build canonicalRequest and compute its signature
        var canonicalRequest = getCanonicalRequest(method, uri, headers, sortedHeaderKeys, signedHeaders, payloadHash);
        var dateStamp = DATE_FORMATTER.format(signingTimestamp);
        var scope = dateStamp + "/" + regionName + "/" + serviceName + "/" + TERMINATOR;
        var signingKey = deriveSigningKey(
            secretAccessKey,
            dateStamp,
            regionName,
            serviceName,
            signingTimestamp
        );
        var signature = computeSignature(canonicalRequest, scope, requestTime, signingKey);

        var authorizationHeader = getAuthHeader(accessKeyId, scope, signedHeaders, signature);
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

    private static StringBuilder getSignedHeaders(Set<String> sortedHeaderKeys) {
        // 512 matches the JavaSdkV2 settings
        var builder = new StringBuilder(512);
        for (var header : sortedHeaderKeys) {
            String lowerCaseHeader = header.toLowerCase(Locale.ENGLISH);
            if (HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(lowerCaseHeader)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(lowerCaseHeader);
        }
        return builder;
    }

    private static String getAuthHeader(
        String accessKeyId,
        String scope,
        StringBuilder signedHeaderBuilder,
        String signature
    ) {
        var builder = new StringBuilder();
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
        String method,
        URI uri,
        Map<String, List<String>> headers,
        Set<String> sortedHeaderKeys,
        StringBuilder signedHeaders,
        String payloadHash
    ) {
        var builder = new StringBuilder();
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
            path = "/" + path;
        }
        URLEncoding.encodeUnreserved(path, builder, true);
    }

    private static void addCanonicalizedQueryString(URI uri, StringBuilder builder) {
        SortedMap<String, String> sorted = new TreeMap<>();

        // Getting the raw query means the keys and values don't need to be encoded again.
        var query = uri.getRawQuery();
        if (query == null) {
            return;
        }

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

        var pairs = sorted.entrySet().iterator();
        while (pairs.hasNext()) {
            var pair = pairs.next();
            var key = pair.getKey();
            var value = pair.getValue();
            builder.append(key);
            builder.append('=');
            builder.append(value);
            if (pairs.hasNext()) {
                builder.append('&');
            }
        }
    }

    private static void addCanonicalizedHeaderString(
        Map<String, List<String>> headers,
        Set<String> sortedHeaderKeys,
        StringBuilder builder
    ) {
        for (var headerKey : sortedHeaderKeys) {
            var lowerCaseHeader = headerKey.toLowerCase(Locale.ENGLISH);
            if (HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(lowerCaseHeader)) {
                continue;
            }
            builder.append(lowerCaseHeader);
            builder.append(':');
            for (String headerValue : headers.get(headerKey)) {
                addAndTrim(builder, headerValue);
                builder.append(',');
            }
            builder.setLength(builder.length() - 1);
            builder.append('\n');
        }
    }

    /**
     * From JAVA V2 sdk: <a href="https://github.com/aws/aws-sdk-java-v2/blob/master/core/auth/src/main/java/software/amazon/awssdk/auth/signer/internal/AbstractAws4Signer.java#L651">JavaV2 Implementation</a>.
     * "The addAndTrim function removes excess white space before and after values,
     * and converts sequential spaces to a single space."
     * <p>
     * <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html">...</a>
     * <p>
     * The collapse-whitespace logic is equivalent to:
     * <pre>
     *     value.replaceAll("\\s+", " ")
     * </pre>
     * but does not create a Pattern object that needs to compile the match
     * string; it also prevents us from having to make a Matcher object as well.
     */
    private static void addAndTrim(StringBuilder result, String value) {
        int lengthBefore = result.length();
        boolean isStart = true;
        boolean previousIsWhiteSpace = false;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isWhiteSpace(ch)) {
                if (previousIsWhiteSpace || isStart) {
                    continue;
                }
                result.append(' ');
                previousIsWhiteSpace = true;
            } else {
                result.append(ch);
                isStart = false;
                previousIsWhiteSpace = false;
            }
        }

        if (lengthBefore == result.length()) {
            return;
        }

        int lastNonWhitespaceChar = result.length() - 1;
        while (isWhiteSpace(result.charAt(lastNonWhitespaceChar))) {
            --lastNonWhitespaceChar;
        }

        result.setLength(lastNonWhitespaceChar + 1);
    }

    /**
     * Tests a char to see if is it whitespace.
     * This method considers the same characters to be white
     * space as the Pattern class does when matching {@code \s}
     *
     * @param ch the character to be tested
     * @return true if the character is white  space, false otherwise.
     */
    private static boolean isWhiteSpace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\u000b' || ch == '\r' || ch == '\f';
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
        byte[] canonicalRequest,
        String scope,
        String requestTime,
        byte[] signingKey
    ) {
        var builder = new StringBuilder();
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
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hash(ByteBuffer data) {
        try {
            var md = MessageDigest.getInstance(SHA_256);
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hash(byte[] data) {
        try {
            var md = MessageDigest.getInstance(SHA_256);
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
