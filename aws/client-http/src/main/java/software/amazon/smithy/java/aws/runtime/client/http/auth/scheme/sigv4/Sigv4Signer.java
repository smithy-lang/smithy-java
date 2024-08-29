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
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.aws.runtime.client.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

/**
 * AWS signature version 4 signing implementation.
 */
final class Sigv4Signer implements Signer<SmithyHttpRequest, AwsCredentialsIdentity> {
    static final Sigv4Signer INSTANCE = new Sigv4Signer();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(Sigv4Signer.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd")
            .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneId.of("UTC"));

    // TODO: Should this be async?
    @Override
    public CompletableFuture<SmithyHttpRequest> sign(
        SmithyHttpRequest request,
        AwsCredentialsIdentity identity,
        AuthProperties properties
    ) {
        var region = properties.expect(Sigv4Properties.REGION);
        var name = properties.expect(Sigv4Properties.SERVICE);
        var clock = properties.get(Sigv4Properties.CLOCK);
        if (clock == null) {
            clock = Clock.systemUTC();
        }
        var timestamp = clock.instant();
        var bodyDataStream = getBodyDataStream(request);

        // Create a mutable copy of the existing headers.
        // TODO: Use mutable header container when available
        var headers = new HashMap<>(request.headers().map());

        // TODO: Handle streaming / unsigned?

        return getPayloadHash(bodyDataStream)
                .thenCompose(hash -> {

                });


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

        return CompletableFuture.completedFuture(
                request.withHeaders(HttpHeaders.of(signedHeaders, (a, b) -> true))
        );
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



    static String computeSignature(
        String regionName,
        String serviceName,
        String method,
        URI uri,
        Map<String, List<String>> headers,
        String accessKeyId,
        String secretKey,
        String payloadHash,
        boolean isStreaming,
        Instant signingTimestamp,
        String sessionToken
    ) {
        // AWS4 requires that we sign the Host header, so we have to have it in the request by the time we sign.
        var hostHeader = uri.getHost();
        if (uri.getPort() > 0) {
            hostHeader += ":" + uri.getPort();
        }
        headers.put("Host", List.of(hostHeader));

        // AWS SigV4 requires that we sign the date header, so it must also be added to the request
        var requestTime = TIME_FORMATTER.format(signingTimestamp);
        headers.put("X-Amz-Date", List.of(requestTime));

        // Add the x-amz-content-sha256 header
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




    }




    private static Map<String, List<String>> createSignedHeaders(
        String regionName,
        String serviceName,
        String method,
        URI uri,
        HttpHeaders httpHeaders,
        String accessKeyId,
        String secretKey,
        String payloadHash,
        boolean isStreaming,
        Instant signingTimestamp,
        String sessionToken
    ) {




        headers.put("Authorization", List.of(authorizationHeader));
        return headers;
    }

    public static String getAuthHeader(String accessKeyId,
                                       String scope,
                                       String signingCredentials,
                                       String signedHeaders,
                                       String signature
    ) {
        // Set signing header values and assemble full Auth header
        var signingCredentials = accessKeyId + '/' + scope;
        var credentialsAuthorizationHeader = "Credential=" + signingCredentials;
        var signedHeadersAuthorizationHeader = "SignedHeaders=" + signedHeaders;
        var signatureAuthorizationHeader = "Signature=" + HexFormat.of().formatHex(signature);
        var authorizationHeader = ALGORITHM + ' ' + credentialsAuthorizationHeader + ", "
                + signedHeadersAuthorizationHeader + ", " + signatureAuthorizationHeader;
        LOGGER.trace("Authorization: {}", authorizationHeader);
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



    static String getScope() {
        var dateStamp = DATE_FORMATTER.format(signingTimestamp);
        var scope = dateStamp + "/" + regionName + "/" + serviceName + "/" + TERMINATOR;
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
