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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
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

    @Test
    void disableDoubleEncodingSignsTheRawPathWithoutNormalizing() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://bucket.s3.amazonaws.com/a%20b//c./d%2Fe"))
                .setHeaders(HttpHeaders.of(Map.of("Host", List.of("bucket.s3.amazonaws.com"))))
                .toUnmodifiable();
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "s3")
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.parse("2026-06-01T23:59:59Z"), ZoneOffset.UTC))
                .put(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING, true);
        var identity = AwsCredentialsIdentity.create("AKID", "secret");

        HttpRequest signed;
        try (var signer = SigV4Signer.create()) {
            signed = signer.sign(request, identity, context).signedRequest();
        }

        var emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        var canonicalRequest = "GET\n/a%20b//c./d%2Fe\n\n"
                + "host:bucket.s3.amazonaws.com\n"
                + "x-amz-content-sha256:" + emptyHash + "\n"
                + "x-amz-date:20260601T235959Z\n\n"
                + "host;x-amz-content-sha256;x-amz-date\n"
                + emptyHash;
        var stringToSign = "AWS4-HMAC-SHA256\n20260601T235959Z\n20260601/us-east-1/s3/aws4_request\n"
                + HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        byte[] key = hmac("AWS4secret".getBytes(StandardCharsets.UTF_8), "20260601");
        key = hmac(key, "us-east-1");
        key = hmac(key, "s3");
        key = hmac(key, "aws4_request");
        var signature = HexFormat.of().formatHex(hmac(key, stringToSign));

        assertThat(signed.headers().firstValue("authorization"),
                equalTo("AWS4-HMAC-SHA256 Credential=AKID/20260601/us-east-1/s3/aws4_request, "
                        + "SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=" + signature));
    }

    @Test
    void callerSuppliedSessionTokenIsReplacedBySignedIdentityToken() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://example.amazonaws.com/"))
                .setHeaders(HttpHeaders.of(Map.of(
                        "Host",
                        List.of("example.amazonaws.com"),
                        "X-Amz-Security-Token",
                        List.of("stale-token"))))
                .toUnmodifiable();
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "service")
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.parse("2026-06-01T23:59:59Z"), ZoneOffset.UTC));
        var identity = AwsCredentialsIdentity.create("AKID", "secret", "fresh-token");

        HttpRequest signed;
        String canonical;
        try (var signer = SigV4Signer.create()) {
            signed = signer.sign(request, identity, context).signedRequest();
            canonical = signer.lastCanonicalRequest();
        }

        assertThat(signed.headers().allValues("x-amz-security-token"), equalTo(List.of("fresh-token")));
        assertThat(canonical, containsString("x-amz-security-token:fresh-token\n"));
        assertThat(canonical, not(containsString("stale")));
        assertThat(signed.headers().firstValue("authorization"),
                equalTo(authorizationOverSentHeaders(signed, identity, "us-east-1", "service")));
    }

    @Test
    void s3PayloadHashOverrideReplacesCallerSuppliedContentSha256() throws Exception {
        var request = HttpRequest.create()
                .setMethod("PUT")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://bucket.s3.amazonaws.com/key"))
                .setHeaders(HttpHeaders.of(Map.of(
                        "Host",
                        List.of("bucket.s3.amazonaws.com"),
                        "x-amz-content-sha256",
                        List.of("UNSIGNED-PAYLOAD"))))
                .setBody(DataStream.ofBytes("body".getBytes(StandardCharsets.UTF_8)))
                .toUnmodifiable();
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "s3")
                .put(SigV4Settings.PAYLOAD_HASH_OVERRIDE, "STREAMING-UNSIGNED-PAYLOAD-TRAILER")
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.parse("2026-06-01T23:59:59Z"), ZoneOffset.UTC));
        var identity = AwsCredentialsIdentity.create("AKID", "secret");

        HttpRequest signed;
        String canonical;
        try (var signer = SigV4Signer.create()) {
            signed = signer.sign(request, identity, context).signedRequest();
            canonical = signer.lastCanonicalRequest();
        }

        assertThat(signed.headers().allValues("x-amz-content-sha256"),
                equalTo(List.of("STREAMING-UNSIGNED-PAYLOAD-TRAILER")));
        assertThat(canonical, containsString("x-amz-content-sha256:STREAMING-UNSIGNED-PAYLOAD-TRAILER\n"));
        assertThat(canonical, not(containsString("UNSIGNED-PAYLOAD\n\n")));
        assertThat(signed.headers().firstValue("authorization"),
                equalTo(authorizationOverSentHeaders(signed, identity, "us-east-1", "s3")));
    }

    @Test
    void pathNormalizationAndDoubleEncodingAreIndependent() {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://example.amazonaws.com/a/./b//c%20d"))
                .setHeaders(HttpHeaders.of(Map.of("Host", List.of("example.amazonaws.com"))))
                .toUnmodifiable();
        var identity = AwsCredentialsIdentity.create("AKID", "secret");

        assertThat(canonicalPath(request, identity, "service", null, null), equalTo("/a/b/c%2520d"));
        assertThat(canonicalPath(request, identity, "service", true, false), equalTo("/a/b/c%20d"));
        assertThat(canonicalPath(request, identity, "service", false, true), equalTo("/a/./b//c%2520d"));
        assertThat(canonicalPath(request, identity, "service", false, false), equalTo("/a/./b//c%20d"));
        assertThat(canonicalPath(request, identity, "s3", null, null), equalTo("/a/./b//c%20d"));
        assertThat(canonicalPath(request, identity, "s3", true, null), equalTo("/a/b/c%20d"));
    }

    private static String canonicalPath(
            HttpRequest request,
            AwsCredentialsIdentity identity,
            String service,
            Boolean normalize,
            Boolean doubleEncode
    ) {
        var context = Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, service)
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.parse("2026-06-01T23:59:59Z"), ZoneOffset.UTC));
        if (normalize != null) {
            context.put(SigV4Settings.NORMALIZE_PATH, normalize);
        }
        if (doubleEncode != null) {
            context.put(SigV4Settings.DOUBLE_URL_ENCODE, doubleEncode);
        }
        try (var signer = SigV4Signer.create()) {
            signer.sign(request, identity, context);
            return signer.lastCanonicalRequest().split("\n")[1];
        }
    }

    private static String authorizationOverSentHeaders(
            HttpRequest signed,
            AwsCredentialsIdentity identity,
            String region,
            String service
    ) throws Exception {
        var ignored = Set.of("authorization",
                "connection",
                "content-length",
                "transfer-encoding",
                "x-amzn-trace-id",
                "user-agent",
                "expect");
        var canonical = new TreeMap<String, List<String>>();
        signed.headers().map().forEach((name, values) -> {
            if (!ignored.contains(name)) {
                canonical.put(name, values);
            }
        });
        var dateTime = signed.headers().firstValue("x-amz-date");
        var payloadHash = signed.headers().firstValue("x-amz-content-sha256");
        if (payloadHash == null) {
            payloadHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        }
        var lines = new StringBuilder();
        var signedHeaders = new StringBuilder();
        for (var e : canonical.entrySet()) {
            lines.append(e.getKey()).append(':').append(String.join(",", e.getValue())).append('\n');
            if (!signedHeaders.isEmpty()) {
                signedHeaders.append(';');
            }
            signedHeaders.append(e.getKey());
        }
        var canonicalRequest = signed.method() + "\n" + signed.uri().getPath() + "\n\n" + lines + "\n"
                + signedHeaders + "\n" + payloadHash;
        var scope = dateTime.substring(0, 8) + "/" + region + "/" + service + "/aws4_request";
        var stringToSign = "AWS4-HMAC-SHA256\n" + dateTime + "\n" + scope + "\n"
                + HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        byte[] key = hmac(("AWS4" + identity.secretAccessKey()).getBytes(StandardCharsets.UTF_8),
                dateTime.substring(0, 8));
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        return "AWS4-HMAC-SHA256 Credential=" + identity.accessKeyId() + "/" + scope + ", SignedHeaders="
                + signedHeaders + ", Signature=" + HexFormat.of().formatHex(hmac(key, stringToSign));
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
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
