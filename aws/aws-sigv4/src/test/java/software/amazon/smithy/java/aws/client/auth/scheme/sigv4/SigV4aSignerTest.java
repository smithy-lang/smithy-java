/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.datastream.PooledDataStream;

class SigV4aSignerTest {

    private static final Instant TIME = Instant.parse("2026-06-01T23:59:59Z");
    private static final AwsCredentialsIdentity IDENTITY = SigV4aTestSupport.testIdentity();

    @Test
    void signsWithRegionSetHeaderAndVerifiableSignature() {
        var request = request("GET", "https://example.amazonaws.com/", Map.of(), null);

        String stringToSign;
        SignResult<HttpRequest> result;
        try (var signer = SigV4aSigner.create()) {
            result = signer.sign(request, IDENTITY, properties());
            stringToSign = signer.lastStringToSign();
            assertThat(signer.lastCanonicalRequest()).isEqualTo("""
                    GET
                    /

                    host:example.amazonaws.com
                    x-amz-date:20260601T235959Z
                    x-amz-region-set:us-east-1

                    host;x-amz-date;x-amz-region-set
                    e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""");
        }

        var headers = result.signedRequest().headers();
        assertThat(headers.firstValue("x-amz-date")).isEqualTo("20260601T235959Z");
        assertThat(headers.firstValue("x-amz-region-set")).isEqualTo("us-east-1");
        var authorization = headers.firstValue("authorization");
        assertThat(authorization)
                .startsWith("AWS4-ECDSA-P256-SHA256 Credential=AKIDEXAMPLE/20260601/service/aws4_request, "
                        + "SignedHeaders=host;x-amz-date;x-amz-region-set, Signature=");
        assertThat(SigV4aTestSupport.signatureOf(authorization)).isEqualTo(result.signature());
        assertThat(stringToSign)
                .startsWith("AWS4-ECDSA-P256-SHA256\n20260601T235959Z\n20260601/service/aws4_request\n");
        assertThat(SigV4aTestSupport.verifies(SigV4aTestSupport.testPublicKey(), stringToSign, result.signature()))
                .isTrue();
    }

    @Test
    void endpointRegionSetTakesPrecedenceOverSettingsAndRegion() {
        var request = request("GET", "https://example.amazonaws.com/", Map.of(), null);
        var properties = properties()
                .put(SigV4aSettings.SIGNING_REGION_SET, List.of("us-east-1", "us-west-2"))
                .put(EndpointAuthSchemeSettings.SIGNING_REGION, "eu-west-1")
                .put(EndpointAuthSchemeSettings.SIGNING_REGION_SET, List.of("*"));

        assertThat(sign(request, properties).headers().firstValue("x-amz-region-set")).isEqualTo("*");

        properties.put(EndpointAuthSchemeSettings.SIGNING_REGION_SET, List.of());
        assertThat(sign(request, properties).headers().firstValue("x-amz-region-set")).isEqualTo("us-east-1,us-west-2");

        properties.put(SigV4aSettings.SIGNING_REGION_SET, List.of());
        assertThat(sign(request, properties).headers().firstValue("x-amz-region-set")).isEqualTo("eu-west-1");
    }

    @Test
    void regionSetIsTrimmedAndDeduplicatedInOrder() {
        var request = request("GET", "https://example.amazonaws.com/", Map.of(), null);
        var properties = properties()
                .put(SigV4aSettings.SIGNING_REGION_SET, List.of(" us-west-2 ", "us-east-1", "us-west-2", ""));

        var signed = sign(request, properties);

        assertThat(signed.headers().firstValue("x-amz-region-set")).isEqualTo("us-west-2,us-east-1");
        assertThat(SigV4aSigner.normalizeRegionSet(List.of("*"))).containsExactly("*");
    }

    @Test
    void rejectsUnusableRegionSets() {
        assertThatThrownBy(() -> SigV4aSigner.normalizeRegionSet(List.of(" ", "")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SigV4aSigner.normalizeRegionSet(List.of("us-east-1,us-west-2")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SigV4aSigner.normalizeRegionSet(List.of("us east")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SigV4aSigner.normalizeRegionSet(null))
                .isInstanceOf(IllegalArgumentException.class);
        var withNull = new ArrayList<String>();
        withNull.add("us-east-1");
        withNull.add(null);
        assertThatThrownBy(() -> SigV4aSigner.normalizeRegionSet(withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        var request = request("GET", "https://example.amazonaws.com/", Map.of(), null);
        try (var signer = SigV4aSigner.create()) {
            assertThatThrownBy(() -> signer.sign(request,
                    IDENTITY,
                    properties().put(SigV4aSettings.SIGNING_REGION_SET, withNull)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void replacesPreexistingRegionSetDateAndAuthorizationHeaders() {
        var request = request("GET",
                "https://example.amazonaws.com/",
                Map.of("X-Amz-Region-Set",
                        List.of("stale"),
                        "X-Amz-Date",
                        List.of("20200101T000000Z"),
                        "Authorization",
                        List.of("stale")),
                null);

        String canonical;
        HttpRequest signed;
        try (var signer = SigV4aSigner.create()) {
            signed = signer.sign(request, IDENTITY, properties()).signedRequest();
            canonical = signer.lastCanonicalRequest();
        }

        assertThat(signed.headers().allValues("x-amz-region-set")).containsExactly("us-east-1");
        assertThat(signed.headers().allValues("x-amz-date")).containsExactly("20260601T235959Z");
        assertThat(signed.headers().firstValue("authorization")).startsWith("AWS4-ECDSA-P256-SHA256 ");
        assertThat(canonical).contains("\nhost;x-amz-date;x-amz-region-set\n").doesNotContain("stale");
    }

    @Test
    void s3AddsAndSignsContentSha256() throws Exception {
        byte[] body = "object bytes".getBytes(StandardCharsets.UTF_8);
        var request = request("PUT", "https://bucket.s3.amazonaws.com/key", Map.of(), body);
        var properties = properties().put(SigV4Settings.SIGNING_NAME, "s3");

        var signed = sign(request, properties);

        var sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        assertThat(signed.headers().firstValue("x-amz-content-sha256")).isEqualTo(sha256);
        assertThat(signed.headers().firstValue("authorization"))
                .contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-region-set,")
                .contains("/20260601/s3/aws4_request");
    }

    @Test
    void honorsPayloadHashOverrideAndSessionTokenSettings() {
        var identity = AwsCredentialsIdentity.create("AKID", "secret", "the-token");
        var request = request("PUT", "https://bucket.s3.amazonaws.com/key", Map.of(), "x".getBytes());

        var properties = properties()
                .put(SigV4Settings.SIGNING_NAME, "s3")
                .put(SigV4Settings.PAYLOAD_HASH_OVERRIDE, "UNSIGNED-PAYLOAD");
        HttpRequest signed;
        String canonical;
        try (var signer = SigV4aSigner.create()) {
            signed = signer.sign(request, identity, properties).signedRequest();
            canonical = signer.lastCanonicalRequest();
        }
        assertThat(signed.headers().firstValue("x-amz-content-sha256")).isEqualTo("UNSIGNED-PAYLOAD");
        assertThat(signed.headers().firstValue("x-amz-security-token")).isEqualTo("the-token");
        assertThat(canonical).endsWith("\nUNSIGNED-PAYLOAD").contains("x-amz-security-token:the-token\n");

        properties.put(SigV4Settings.OMIT_SECURITY_TOKEN, true);
        try (var signer = SigV4aSigner.create()) {
            signed = signer.sign(request, identity, properties).signedRequest();
            canonical = signer.lastCanonicalRequest();
        }
        assertThat(signed.headers().hasHeader("x-amz-security-token")).isFalse();
        assertThat(canonical).doesNotContain("x-amz-security-token");
    }

    @Test
    void pathNormalizationAndDoubleEncodingAreIndependent() {
        var request = request("GET", "https://example.amazonaws.com/a/./b//c%20d", Map.of(), null);

        assertThat(canonicalPath(request, "service", null, null, null)).isEqualTo("/a/b/c%2520d");
        assertThat(canonicalPath(request, "service", true, false, null)).isEqualTo("/a/b/c%20d");
        assertThat(canonicalPath(request, "service", false, true, null)).isEqualTo("/a/./b//c%2520d");
        assertThat(canonicalPath(request, "service", false, false, null)).isEqualTo("/a/./b//c%20d");
        assertThat(canonicalPath(request, "service", null, true, true)).isEqualTo("/a/b/c%20d");
        assertThat(canonicalPath(request, "s3", null, null, null)).isEqualTo("/a/./b//c%20d");
        assertThat(canonicalPath(request, "s3", true, null, null)).isEqualTo("/a/b/c%20d");
    }

    private static String canonicalPath(
            HttpRequest request,
            String service,
            Boolean normalize,
            Boolean doubleEncode,
            Boolean endpointDisableDoubleEncoding
    ) {
        var context = properties().put(SigV4Settings.SIGNING_NAME, service);
        if (normalize != null) {
            context.put(SigV4Settings.NORMALIZE_PATH, normalize);
        }
        if (doubleEncode != null) {
            context.put(SigV4Settings.DOUBLE_URL_ENCODE, doubleEncode);
        }
        if (endpointDisableDoubleEncoding != null) {
            context.put(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING, endpointDisableDoubleEncoding);
        }
        try (var signer = SigV4aSigner.create()) {
            signer.sign(request, IDENTITY, context);
            return signer.lastCanonicalRequest().split("\n")[1];
        }
    }

    @Test
    void callerSuppliedTokenAndPayloadHashHeadersAreReplacedBeforeSigning() {
        var identity = AwsCredentialsIdentity.create("AKID", "secret", "fresh-token");
        var request = request("PUT",
                "https://bucket.s3.amazonaws.com/key",
                Map.of("X-Amz-Security-Token",
                        List.of("stale-token"),
                        "x-amz-content-sha256",
                        List.of("UNSIGNED-PAYLOAD")),
                "body".getBytes(StandardCharsets.UTF_8));
        var context = properties()
                .put(SigV4Settings.SIGNING_NAME, "s3")
                .put(SigV4Settings.PAYLOAD_HASH_OVERRIDE, "STREAMING-UNSIGNED-PAYLOAD-TRAILER");

        HttpRequest signed;
        String canonical;
        String stringToSign;
        String signature;
        try (var signer = SigV4aSigner.create()) {
            var result = signer.sign(request, identity, context);
            signed = result.signedRequest();
            signature = result.signature();
            canonical = signer.lastCanonicalRequest();
            stringToSign = signer.lastStringToSign();
        }

        assertThat(signed.headers().allValues("x-amz-security-token")).containsExactly("fresh-token");
        assertThat(signed.headers().allValues("x-amz-content-sha256"))
                .containsExactly("STREAMING-UNSIGNED-PAYLOAD-TRAILER");
        assertThat(canonical)
                .contains("x-amz-content-sha256:STREAMING-UNSIGNED-PAYLOAD-TRAILER\n")
                .contains("x-amz-security-token:fresh-token\n")
                .endsWith("\nSTREAMING-UNSIGNED-PAYLOAD-TRAILER")
                .doesNotContain("stale")
                .doesNotContain("UNSIGNED-PAYLOAD\n");
        assertThat(SigV4aTestSupport.verifies(SigV4aTestSupport.publicKeyFor(identity), stringToSign, signature))
                .isTrue();
    }

    @Test
    void mutatingTheConfiguredRegionSetListIsReflectedOnTheNextRequest() {
        var regions = new ArrayList<>(List.of("us-east-1"));
        var request = request("GET", "https://example.amazonaws.com/", Map.of(), null);
        var context = properties().put(SigV4aSettings.SIGNING_REGION_SET, regions);

        try (var signer = SigV4aSigner.create()) {
            assertThat(signer.sign(request, IDENTITY, context).signedRequest().headers().firstValue("x-amz-region-set"))
                    .isEqualTo("us-east-1");
            regions.set(0, "us-west-2");
            assertThat(signer.sign(request, IDENTITY, context).signedRequest().headers().firstValue("x-amz-region-set"))
                    .isEqualTo("us-west-2");
        }
    }

    @Test
    void reusedAndFreshSignersProduceVerifiableSignaturesOverTheSameStringToSign() {
        var request = request("POST",
                "https://example.amazonaws.com/op?x=1",
                Map.of("x-note", List.of("naïve  value")),
                "payload".getBytes(StandardCharsets.UTF_8));
        var publicKey = SigV4aTestSupport.testPublicKey();

        String expectedStringToSign;
        try (var signer = SigV4aSigner.create()) {
            var first = signer.sign(request, IDENTITY, properties());
            expectedStringToSign = signer.lastStringToSign();
            var second = signer.sign(request, IDENTITY, properties());
            assertThat(signer.lastStringToSign()).isEqualTo(expectedStringToSign);
            assertThat(SigV4aTestSupport.verifies(publicKey, expectedStringToSign, first.signature())).isTrue();
            assertThat(SigV4aTestSupport.verifies(publicKey, expectedStringToSign, second.signature())).isTrue();
        }
        try (var signer = SigV4aSigner.create()) {
            var third = signer.sign(request, IDENTITY, properties());
            assertThat(signer.lastStringToSign()).isEqualTo(expectedStringToSign);
            assertThat(SigV4aTestSupport.verifies(publicKey, expectedStringToSign, third.signature())).isTrue();
        }
    }

    @Test
    void concurrentSignersOnPlatformAndVirtualThreadsAgree() throws Exception {
        var request = request("POST",
                "https://example.amazonaws.com/op?x=1",
                Map.of("x-note", List.of("value")),
                "payload".getBytes(StandardCharsets.UTF_8));
        var identities = List.of(IDENTITY, AwsCredentialsIdentity.create("AKIDOTHER", "other-secret", "token"));
        var props = Context.unmodifiableCopy(properties());

        record Observation(String stringToSign, String signature, int identity) {}
        List<Future<List<Observation>>> results = new ArrayList<>();
        try (ExecutorService platform = Executors.newFixedThreadPool(8);
                ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 32; i++) {
                var pool = i % 2 == 0 ? platform : virtual;
                int seed = i;
                results.add(pool.submit(() -> {
                    var observations = new ArrayList<Observation>();
                    try (var signer = SigV4aSigner.create()) {
                        for (int n = 0; n < 25; n++) {
                            int which = (seed + n) % identities.size();
                            var result = signer.sign(request, identities.get(which), props);
                            observations.add(new Observation(signer.lastStringToSign(), result.signature(), which));
                        }
                    }
                    return observations;
                }));
            }
            String[] expected = new String[identities.size()];
            for (var future : results) {
                for (var o : future.get()) {
                    if (expected[o.identity] == null) {
                        expected[o.identity] = o.stringToSign;
                    }
                    assertThat(o.stringToSign).isEqualTo(expected[o.identity]);
                    var key = SigV4aTestSupport.publicKeyFor(identities.get(o.identity));
                    assertThat(SigV4aTestSupport.verifies(key, o.stringToSign, o.signature)).isTrue();
                }
            }
        }
    }

    @Test
    void signingReadsAPooledBodyWithoutAliasingIt() throws Exception {
        byte[] payload = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        var recycled = new AtomicReference<byte[]>();
        PooledDataStream pooled =
                DataStream.ofLeasedBytes(payload, 0, payload.length, "application/json", recycled::set);
        var properties = properties().put(SigV4Settings.SIGNING_NAME, "s3");

        HttpRequest signedPooled;
        String pooledStringToSign;
        String plainStringToSign;
        try (var signer = SigV4aSigner.create()) {
            signedPooled = signer.sign(bodyRequest(pooled), IDENTITY, properties).signedRequest();
            pooledStringToSign = signer.lastStringToSign();
            signer.sign(bodyRequest(DataStream.ofBytes(payload, "application/json")), IDENTITY, properties);
            plainStringToSign = signer.lastStringToSign();
        }

        assertThat(pooled.escaped()).isFalse();
        assertThat(pooled.isReleased()).isFalse();
        assertThat(recycled.get()).isNull();
        assertThat(signedPooled.body()).isSameAs(pooled);
        var sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        assertThat(signedPooled.headers().firstValue("x-amz-content-sha256")).isEqualTo(sha256);
        assertThat(pooledStringToSign).isEqualTo(plainStringToSign);
    }

    @Test
    void unknownLengthBodiesAreRejected() {
        var body = DataStream.ofInputStream(new ByteArrayInputStream(new byte[3]));
        var request = HttpRequest.create()
                .setMethod("PUT")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create("https://example.amazonaws.com/"))
                .setBody(body)
                .toUnmodifiable();
        try (var signer = SigV4aSigner.create()) {
            assertThatThrownBy(() -> signer.sign(request, IDENTITY, properties()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private static HttpRequest sign(HttpRequest request, Context properties) {
        try (var signer = SigV4aSigner.create()) {
            return signer.sign(request, IDENTITY, properties).signedRequest();
        }
    }

    private static Context properties() {
        return Context.create()
                .put(SigV4Settings.REGION, "us-east-1")
                .put(SigV4Settings.SIGNING_NAME, "service")
                .put(SigV4Settings.CLOCK, Clock.fixed(TIME, ZoneOffset.UTC));
    }

    private static HttpRequest request(String method, String uri, Map<String, List<String>> headers, byte[] body) {
        var builder = HttpRequest.create()
                .setMethod(method)
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create(uri))
                .setHeaders(HttpHeaders.of(headers));
        if (body != null) {
            builder.setBody(DataStream.ofBytes(body));
        }
        return builder.toUnmodifiable();
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
