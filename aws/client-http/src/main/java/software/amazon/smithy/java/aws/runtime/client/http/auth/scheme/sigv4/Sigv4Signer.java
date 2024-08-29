/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
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
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.aws.runtime.client.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.utils.StringUtils;

/**
 * AWS signature version 4 signing implementation.
 */
final class Sigv4Signer extends AbstractSigv4Signer implements Signer<SmithyHttpRequest, AwsCredentialsIdentity> {
    static final Sigv4Signer INSTANCE = new Sigv4Signer();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMdd")
        .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneId.of("UTC"));

    @Override
    public CompletableFuture<SmithyHttpRequest> sign(
        SmithyHttpRequest request,
        AwsCredentialsIdentity identity,
        AuthProperties properties
    ) {
        var region = properties.expect(Sigv4Properties.REGION);
        var name = properties.expect(Sigv4Properties.SERVICE);
        var clock = properties.getOrDefault(Sigv4Properties.CLOCK, Clock.systemUTC());
        var timestamp = clock.instant();
        var bodyDataStream = getBodyDataStream(request);

        // TODO: Add support for query signing
        // TODO: support streaming
        // TODO: support UNSIGNED

        return getPayloadHash(bodyDataStream)
            .thenApply(payloadHash -> {
                var signedHeaders = createSignedHeaders(
                    request.method(),
                    request.uri(),
                    request.headers(),
                    payloadHash,
                    region,
                    name,
                    timestamp,
                    StringUtils.trim(identity.accessKeyId()),
                    StringUtils.trim(identity.secretAccessKey()),
                    identity.sessionToken().map(StringUtils::trim).orElse(null),
                    false
                );
                return request.withHeaders(HttpHeaders.of(signedHeaders, (a, b) -> true));
            });
    }

    private static DataStream getBodyDataStream(SmithyHttpRequest request) {
        if (request.body() instanceof DataStream ds) {
            return ds;
        }
        var contentType = request.headers().firstValue("content-type").orElse(null);
        var contentLength = request.headers().firstValue("content-length").map(Long::valueOf).orElse(-1L);
        return DataStream.ofPublisher(request.body(), contentType, contentLength);
    }

    private static CompletableFuture<String> getPayloadHash(DataStream dataStream) {
        var subscriber = new HashingSubscriber();
        dataStream.subscribe(subscriber);
        return subscriber.result.thenApply(HexFormat.of()::formatHex);
    }

    private static Map<String, List<String>> createSignedHeaders(
        String method,
        URI uri,
        HttpHeaders HttpHeaders,
        String payloadHash,
        String regionName,
        String serviceName,
        Instant signingTimestamp,
        String sanitizedAccessKeyId,
        String sanitizedSecretAccessKey,
        String sanitizedSessionToken,
        boolean isStreaming
    ) {
        // TODO: Use mutable header container when available
        var headers = new HashMap<>(HttpHeaders.map());

        // AWS4 requires a number of headers to be set before signing.
        var hostHeader = uri.getHost();
        if (uriUsingNonStandardPort(uri)) {
            hostHeader += ":" + uri.getPort();
        }
        headers.put("Host", List.of(hostHeader));

        var requestTime = TIME_FORMATTER.format(signingTimestamp);
        headers.put("X-Amz-Date", List.of(requestTime));

        if (isStreaming) {
            headers.put("x-amz-content-sha256", List.of(payloadHash));
        }
        if (sanitizedSessionToken != null) {
            headers.put("X-Amz-Security-Token", List.of(sanitizedSessionToken));
        }

        // Determine sorted list of headers to sign
        Set<String> sortedHeaderKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedHeaderKeys.addAll(headers.keySet());
        var signedHeaders = getSignedHeaders(sortedHeaderKeys);

        // Build canonicalRequest and compute its signature
        var canonicalRequest = getCanonicalRequest(method, uri, headers, sortedHeaderKeys, signedHeaders, payloadHash);
        var dateStamp = DATE_FORMATTER.format(signingTimestamp);
        var scope = dateStamp + "/" + regionName + "/" + serviceName + "/" + TERMINATOR;
        var signingKey = deriveSigningKey(sanitizedSecretAccessKey, dateStamp, regionName, serviceName);
        var signature = computeSignature(canonicalRequest, scope, requestTime, signingKey);

        var authorizationHeader = getAuthHeader(sanitizedAccessKeyId, scope, signedHeaders, signature);
        headers.put("Authorization", List.of(authorizationHeader));

        return headers;
    }

    private static boolean uriUsingNonStandardPort(URI uri) {
        if (uri.getPort() == -1) {
            return false;
        }
        return switch (uri.getScheme()) {
            case "http" -> uri.getPort() == 80;
            case "https" -> uri.getPort() == 443;
            default -> throw new IllegalStateException("Unexpected value for URI scheme: " + uri.getScheme());
        };
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

    private static String getAuthHeader(
        String accessKeyId,
        String scope,
        String signedHeaders,
        String signature
    ) {
        // Set signing header values and assemble full Auth header
        var signingCredentials = accessKeyId + '/' + scope;
        var credentialsAuthorizationHeader = "Credential=" + signingCredentials;
        var signedHeadersAuthorizationHeader = "SignedHeaders=" + signedHeaders;
        var signatureAuthorizationHeader = "Signature=" + signature;
        return ALGORITHM + ' ' + credentialsAuthorizationHeader + ", "
            + signedHeadersAuthorizationHeader + ", " + signatureAuthorizationHeader;
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
    }
}
