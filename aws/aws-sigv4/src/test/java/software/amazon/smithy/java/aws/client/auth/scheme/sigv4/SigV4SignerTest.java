/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.datastream.PooledDataStream;

class SigV4SignerTest {

    @Test
    void overwritesExistingAmzDate() {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://example.amazonaws.com/"))
                .setHeaders(HttpHeaders.of(Map.of(
                        "Host",
                        List.of("example.amazonaws.com"),
                        "X-Amz-Date",
                        List.of("20260602T000001Z"))))
                .toUnmodifiable();
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "service")
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.parse("2026-06-01T23:59:59Z"), ZoneOffset.UTC));
        var identity = AwsCredentialsIdentity.create("AKID", "secret");

        HttpRequest signed;
        try (var signer = SigV4Signer.create()) {
            signed = signer.sign(request, identity, context).signedRequest();
        }

        assertThat(signed.headers().firstValue("x-amz-date"), equalTo("20260601T235959Z"));
        assertThat(signed.headers().firstValue("authorization"),
                containsString("Credential=AKID/20260601/us-east-1/service/aws4_request"));
    }

    @Test
    void ignoresPreexistingAuthorizationHeader() {
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "service")
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.parse("2026-06-01T23:59:59Z"), ZoneOffset.UTC));
        var identity = AwsCredentialsIdentity.create("AKID", "secret");

        var clean = HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://example.amazonaws.com/"))
                .setHeaders(HttpHeaders.of(Map.of("Host", List.of("example.amazonaws.com"))))
                .toUnmodifiable();
        var withStaleAuth = HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://example.amazonaws.com/"))
                .setHeaders(HttpHeaders.of(Map.of(
                        "Host",
                        List.of("example.amazonaws.com"),
                        "Authorization",
                        List.of("AWS4-HMAC-SHA256 Credential=STALE/garbage, SignedHeaders=host, Signature=deadbeef"))))
                .toUnmodifiable();

        String cleanAuth;
        String staleAuth;
        try (var signer = SigV4Signer.create()) {
            cleanAuth = signer.sign(clean, identity, context).signedRequest().headers().firstValue("authorization");
            staleAuth = signer.sign(withStaleAuth, identity, context)
                    .signedRequest()
                    .headers()
                    .firstValue("authorization");
        }

        // A stale Authorization header must not change SignedHeaders or the resulting signature.
        assertThat(staleAuth, not(containsString("authorization")));
        assertThat(staleAuth, equalTo(cleanAuth));
    }

    @Test
    void signingReadsAPooledBodyWithoutAliasingIt() throws Exception {
        byte[] payload = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        var recycled = new AtomicReference<byte[]>();
        PooledDataStream pooled =
                DataStream.ofLeasedBytes(payload, 0, payload.length, "application/json", recycled::set);
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "s3")
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.parse("2026-06-01T23:59:59Z"), ZoneOffset.UTC));
        var identity = AwsCredentialsIdentity.create("AKID", "secret");

        HttpRequest signedPooled;
        HttpRequest signedPlain;
        try (var signer = SigV4Signer.create()) {
            signedPooled = signer.sign(bodyRequest(pooled), identity, context).signedRequest();
            signedPlain = signer.sign(bodyRequest(DataStream.ofBytes(payload, "application/json")), identity, context)
                    .signedRequest();
        }

        assertThat(pooled.escaped(), is(false));
        assertThat(pooled.isReleased(), is(false));
        assertThat(recycled.get(), is((byte[]) null));
        assertThat(signedPooled.body(), sameInstance(pooled));
        var sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        assertThat(signedPooled.headers().firstValue("x-amz-content-sha256"), equalTo(sha256));
        assertThat(signedPooled.headers().firstValue("authorization"),
                equalTo(signedPlain.headers().firstValue("authorization")));
    }

    private static HttpRequest bodyRequest(DataStream body) {
        return HttpRequest.create()
                .setMethod("PUT")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://bucket.s3.amazonaws.com/key"))
                .setHeaders(HttpHeaders.of(Map.of("Host", List.of("bucket.s3.amazonaws.com"))))
                .setBody(body)
                .toUnmodifiable();
    }
}
