/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.smithy.java.aws.runtime.client.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

/**
 * AWS signature version 4 signing implementation.
 */
final class SigV4Signer implements Signer<SmithyHttpRequest, AwsCredentialsIdentity> {
    static final SigV4Signer INSTANCE = new SigV4Signer();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(SigV4Signer.class);
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final List<String> HEADERS_TO_IGNORE_IN_LOWER_CASE = List.of(
        "connection",
        "x-amzn-trace-id",
        "user-agent",
        "expect"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMdd")
        .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneId.of("UTC"));
    private static final String HMAC_SHA_256 = "HmacSHA256";

    // TODO: Should this be async?
    @Override
    public SmithyHttpRequest sign(
        SmithyHttpRequest request,
        AwsCredentialsIdentity identity,
        AuthProperties properties
    ) {
        var region = properties.expect(Sigv4Properties.REGION);
        var name = properties.expect(Sigv4Properties.SERVICE);
        var timestamp = properties.expect(Sigv4Properties.CLOCK).instant();
        var requestIs = getBodyDataStream(request);
        // TODO: Handle streaming?
        var signedHeaders = createSignedHeaders(
            region,
            name,
            request.method(),
            request.uri(),
            request.headers(),
            identity.accessKeyId(),
            identity.secretAccessKey(),
            requestIs,
            false,
            timestamp,
            identity.sessionToken().orElse(null)
        );
        return request.withHeaders(HttpHeaders.of(signedHeaders, (a, b) -> true));
    }

    private static DataStream getBodyDataStream(SmithyHttpRequest request) {
        if (request.body() instanceof DataStream ds) {
            return ds;
        }
        var contentType = request.headers().firstValue("content-type").orElse(null);
        var contentLength = request.headers().firstValue("content-length").map(Long::valueOf).orElse(-1L);
        return DataStream.ofPublisher(request.body(), contentType, contentLength);
    }

    private static Map<String, List<String>> createSignedHeaders(
        String regionName,
        String serviceName,
        String method,
        URI uri,
        HttpHeaders httpHeaders,
        String accessKeyId,
        String secretKey,
        DataStream payload,
        boolean isStreaming,
        Instant now,
        String sessionToken
    ) {
        // Create a copy of the existing headers
        var headers = new HashMap<>(httpHeaders.map());

        // AWS4 requires that we sign the Host header, so we have to have it in the request by the time we sign.
        var hostHeader = uri.getHost();
        if (uri.getPort() > 0) {
            hostHeader += ":" + uri.getPort();
        }
        headers.put("Host", List.of(hostHeader));
        LOGGER.trace("Host header value: {}", hostHeader);

        // AWS SigV4 requires that we sign the date header, so it must also be added to the request
        var requestTime = TIME_FORMATTER.format(now);
        headers.put("X-Amz-Date", List.of(requestTime));
        LOGGER.trace("X-Amz-Date header value: {}", requestTime);

        // Add the x-amz-content-sha256 header
        var payloadHash = HexFormat.of().formatHex(hash(payload));
        if (isStreaming) {
            headers.put("x-amz-content-sha256", List.of(payloadHash));
        }

        // If the identity has session credentials add the session token
        if (sessionToken != null) {
            headers.put("X-Amz-Security-Token", List.of(sessionToken));
        }

        // Sort header names/keys
        Set<String> sortedHeaderKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedHeaderKeys.addAll(headers.keySet());

        // TODO: allow un-normalized? (this is the same step as:
        //  https://github.com/aws/aws-sdk-java-v2/blob/master/core/auth/src/main/java/software/amazon/awssdk/auth/signer/internal/AbstractAws4Signer.java#L525)
        // Build canonicalRequest
        var signedHeaders = getSignedHeaders(sortedHeaderKeys);
        String canonicalRequest = method + "\n"
            + uri.normalize().getRawPath() + "\n"
            + getCanonicalizedQueryString(uri) + "\n"
            + getCanonicalizedHeaderString(headers, sortedHeaderKeys) + "\n"
            + signedHeaders + "\n"
            + payloadHash;
        LOGGER.trace("AWS SigV4 Canonical Request: '{}'", canonicalRequest);

        var dateStamp = DATE_FORMATTER.format(now);
        var scope = dateStamp + "/" + regionName + "/" + serviceName + "/" + TERMINATOR;
        var stringToSign = getStringToSign(canonicalRequest, scope, requestTime);
        LOGGER.trace("AWS SigV4 String to Sign: '{}'", stringToSign);

        // Derive signing key and use to hash request to get signature.
        var kSigning = deriveSigningKey(secretKey, dateStamp, regionName, serviceName);
        var signature = sign(stringToSign.getBytes(StandardCharsets.UTF_8), kSigning);
        LOGGER.trace("AWS SigV4 Signature: '{}'", signature);

        // Set signing header values and assemble full Auth header
        var signingCredentials = accessKeyId + '/' + scope;
        var credentialsAuthorizationHeader = "Credential=" + signingCredentials;
        var signedHeadersAuthorizationHeader = "SignedHeaders=" + signedHeaders;
        var signatureAuthorizationHeader = "Signature=" + HexFormat.of().formatHex(signature);
        var authorizationHeader = ALGORITHM + ' ' + credentialsAuthorizationHeader + ", "
            + signedHeadersAuthorizationHeader + ", " + signatureAuthorizationHeader;
        LOGGER.trace("Authorization: {}", authorizationHeader);

        headers.put("Authorization", List.of(authorizationHeader));
        return headers;
    }


    private static String getStringToSign(String canonicalRequest, String scope, String formattedDateTime) {
        return ALGORITHM + "\n"
            + formattedDateTime + "\n"
            + scope + "\n"
            + HexFormat.of().formatHex(hash(canonicalRequest));
    }

    /**
     * AWS4 uses a series of derived keys, formed by hashing different pieces of data
     */
    private static byte[] deriveSigningKey(String secretKey, String dateStamp, String regionName, String serviceName) {
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = sign(dateStamp, kSecret);
        var kRegion = sign(regionName, kDate);
        var kService = sign(serviceName, kRegion);
        return sign(TERMINATOR, kService);
    }

    private static byte[] sign(String data, byte[] key) {
        try {
            return sign(data.getBytes(StandardCharsets.UTF_8), key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] sign(byte[] data, byte[] key) {
        try {
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hash(DataStream dataStream) {
        try {
            var subscriber = new HashingSubscriber();
            dataStream.subscribe(subscriber);
            return subscriber.result.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hash(String str) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(str.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getSignedHeaders(Set<String> sortedHeaderKeys) {
        // 512 matches the JavaSdkV2 settings
        var builder = new StringBuilder(512);
        for (var header : sortedHeaderKeys) {
            String lowerCaseHeader = header.toLowerCase(Locale.ENGLISH);
            if (HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(lowerCaseHeader)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(";");
            }
            builder.append(lowerCaseHeader);
        }
        return builder.toString();
    }

    private static String getCanonicalizedHeaderString(
        Map<String, List<String>> headers,
        Set<String> sortedHeaderKeys
    ) {
        // 2048 was determined experimentally by the JavaSdkV2 to minimize resizing.
        var buffer = new StringBuilder(2048);
        for (var headerKey : sortedHeaderKeys) {
            var lowerCaseHeader = headerKey.toLowerCase(Locale.ENGLISH);
            if (HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(lowerCaseHeader)) {
                continue;
            }
            buffer.append(lowerCaseHeader);
            buffer.append(":");
            for (String headerValue : headers.get(headerKey)) {
                addAndTrim(buffer, headerValue);
                buffer.append(",");
            }
            buffer.setLength(buffer.length() - 1);
            buffer.append("\n");
        }
        return buffer.toString();
    }


    /**
     * From JAVA V2 sdk: https://github.com/aws/aws-sdk-java-v2/blob/master/core/auth/src/main/java/software/amazon/awssdk/auth/signer/internal/AbstractAws4Signer.java#L651
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

    private static String getCanonicalizedQueryString(URI uri) {
        SortedMap<String, String> sorted = new TreeMap<>();

        // Getting the raw query means the keys and values don't need to be encoded again.
        var query = uri.getRawQuery();
        if (query == null) {
            return "";
        }

        var params = query.split("&");

        for (var param : params) {
            var keyVal = param.split("=");
            if (keyVal.length == 2) {
                sorted.put(keyVal[0], keyVal[1]);
            } else {
                sorted.put(keyVal[0], "");
            }
        }

        var builder = new StringBuilder();
        var pairs = sorted.entrySet().iterator();

        while (pairs.hasNext()) {
            var pair = pairs.next();
            var key = pair.getKey();
            var value = pair.getValue();
            builder.append(key);
            builder.append("=");
            builder.append(value);
            if (pairs.hasNext()) {
                builder.append("&");
            }
        }

        return builder.toString();
    }

    /**
     * Subscriber that computes the hash of a given byte buffer flow.
     */
    private static final class HashingSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final MessageDigest md;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        private HashingSubscriber() {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            item.rewind();
            while (item.hasRemaining()) {
                md.update(item.get());
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(md.digest());
        }

        public CompletionStage<byte[]> result() {
            return result;
        }
    }
}
