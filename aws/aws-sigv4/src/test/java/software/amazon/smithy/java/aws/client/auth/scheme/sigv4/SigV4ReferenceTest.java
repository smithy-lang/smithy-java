/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.URLEncoding;

class SigV4ReferenceTest {

    private static final Set<String> IGNORED = Set.of(
            "authorization",
            "connection",
            "content-length",
            "transfer-encoding",
            "x-amzn-trace-id",
            "user-agent",
            "expect");
    private static final Instant TIME = Instant.parse("2026-06-01T23:59:59Z");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    record Case(
            String name,
            String method,
            String uri,
            Map<String, List<String>> headers,
            String body,
            String service,
            String region,
            String sessionToken) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                new Case("plain",
                        "GET",
                        "https://example.amazonaws.com/",
                        Map.of(),
                        null,
                        "service",
                        "us-east-1",
                        null),
                new Case("latin1 header value",
                        "POST",
                        "https://example.amazonaws.com/path",
                        Map.of("x-note", List.of("café à la crème")),
                        "body",
                        "service",
                        "us-east-1",
                        null),
                new Case("whitespace in values",
                        "PUT",
                        "https://example.amazonaws.com/x",
                        Map.of("x-a", List.of("  leading and   internal \t\ttabs  "), "x-b", List.of("\t\t")),
                        "b",
                        "service",
                        "eu-west-1",
                        null),
                new Case("repeated header",
                        "GET",
                        "https://example.amazonaws.com/",
                        Map.of("x-dup", List.of("second", "first", " third ")),
                        null,
                        "service",
                        "us-east-1",
                        null),
                new Case("query needs sorting",
                        "GET",
                        "https://example.amazonaws.com/q?b=2&a=1&a=0&c",
                        Map.of(),
                        null,
                        "service",
                        "us-east-1",
                        null),
                new Case("query needs encoding",
                        "GET",
                        // Re-encode lowercase %7e as ~. Preserve %20 and %2F through decoding and encoding.
                        "https://example.amazonaws.com/q?key=a%20b&other=%7e~&x=1%2F2",
                        Map.of(),
                        null,
                        "service",
                        "us-east-1",
                        null),
                new Case("path needs encoding",
                        "GET",
                        // SigV4 canonical paths encode existing percent escapes again as %25.
                        "https://example.amazonaws.com/a%20b/c%2Fd/e+f",
                        Map.of(),
                        null,
                        "service",
                        "us-east-1",
                        null),
                new Case("s3 adds content sha256",
                        "PUT",
                        "https://bucket.s3.amazonaws.com/key",
                        Map.of("content-type", List.of("application/octet-stream")),
                        "object bytes",
                        "s3",
                        "us-west-2",
                        null),
                new Case("session token",
                        "GET",
                        "https://example.amazonaws.com/",
                        Map.of(),
                        null,
                        "service",
                        "us-east-1",
                        "the-session-token"),
                new Case("non-default port and ignored headers",
                        "GET",
                        "http://example.amazonaws.com:8080/",
                        Map.of("user-agent",
                                List.of("ua/1"),
                                "Content-Length",
                                List.of("0"),
                                "X-Mixed-Case",
                                List.of("v")),
                        null,
                        "service",
                        "us-east-1",
                        null))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void matchesReferenceImplementation(Case c) throws Exception {
        var request = request(c);
        var identity = c.sessionToken == null
                ? AwsCredentialsIdentity.create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                : AwsCredentialsIdentity
                        .create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", c.sessionToken);

        HttpRequest signed;
        try (var signer = SigV4Signer.create()) {
            signed = signer.sign(request, identity, properties(c)).signedRequest();
        }

        var expected = reference(c, identity);
        assertThat(signed.headers().firstValue("x-amz-date")).isEqualTo(STAMP.format(TIME));
        assertThat(signed.headers().firstValue("authorization")).isEqualTo(expected);
        if (c.service.equals("s3")) {
            assertThat(signed.headers().firstValue("x-amz-content-sha256")).isEqualTo(sha256Hex(bodyBytes(c)));
        }
    }

    @Test
    void timestampFormattingMatchesJavaTime() {
        var r = new SigningResources();
        var random = new Random(42);
        long[] seconds = new long[2_000];
        for (int i = 0; i < seconds.length; i++) {
            // Epoch seconds before 2200-01-01.
            seconds[i] = (long) (random.nextDouble() * 7_258_118_400L);
        }
        seconds[0] = 0;
        seconds[1] = 951_782_399; // 2000-02-28T23:59:59Z
        seconds[2] = 951_782_400; // 2000-02-29T00:00:00Z (leap day)
        seconds[3] = 4_107_542_399L; // 2100-02-28T23:59:59Z (not a leap year)
        for (long s : seconds) {
            r.timestamp(s);
            var expected = STAMP.format(Instant.ofEpochSecond(s));
            assertThat(r.requestTime).as("second %d", s).isEqualTo(expected);
            assertThat(r.dateStamp).isEqualTo(expected.substring(0, 8));
            assertThat(r.cachedDays).isEqualTo(Math.floorDiv(s, 86_400L));
        }
    }

    @Test
    void concurrentSignersOnPlatformAndVirtualThreadsAgree() throws Exception {
        var c = new Case("concurrent",
                "POST",
                "https://example.amazonaws.com/op?x=1",
                Map.of("x-note", List.of("naïve  value")),
                "payload",
                "service",
                "us-east-1",
                null);
        var identity = AwsCredentialsIdentity.create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
        var expected = reference(c, identity);
        var request = request(c);
        var props = properties(c);

        List<Future<String>> results = new ArrayList<>();
        try (ExecutorService platform = Executors.newFixedThreadPool(8);
                ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 64; i++) {
                var pool = i % 2 == 0 ? platform : virtual;
                results.add(pool.submit(() -> {
                    String last = null;
                    for (int n = 0; n < 50; n++) {
                        try (var signer = SigV4Signer.create()) {
                            last = signer.sign(request, identity, props)
                                    .signedRequest()
                                    .headers()
                                    .firstValue("authorization");
                        }
                    }
                    return last;
                }));
            }
            for (var f : results) {
                assertThat(f.get()).isEqualTo(expected);
            }
        }
    }

    private static String reference(Case c, AwsCredentialsIdentity identity) throws Exception {
        var uri = URI.create(c.uri);
        var dateTime = STAMP.format(TIME);
        var dateStamp = dateTime.substring(0, 8);
        var payloadHash = sha256Hex(bodyBytes(c));

        var canonical = new TreeMap<String, List<String>>();
        for (var entry : c.headers.entrySet()) {
            var name = entry.getKey().toLowerCase();
            for (var v : entry.getValue()) {
                canonical.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(v.trim().replaceAll("[ \\t\\n\\u000b\\f\\r]+", " "));
            }
        }
        boolean defaultPort = uri.getPort() == -1
                || (uri.getPort() == 80 && uri.getScheme().equals("http"))
                || (uri.getPort() == 443 && uri.getScheme().equals("https"));
        canonical.putIfAbsent("host", List.of(defaultPort ? uri.getHost() : uri.getHost() + ":" + uri.getPort()));
        canonical.put("x-amz-date", List.of(dateTime));
        if (c.sessionToken != null) {
            canonical.put("x-amz-security-token", List.of(c.sessionToken));
        }
        if (c.service.equals("s3")) {
            canonical.put("x-amz-content-sha256", List.of(payloadHash));
        }
        canonical.keySet().removeIf(IGNORED::contains);

        var headerLines = new StringBuilder();
        var signedHeaders = new StringBuilder();
        for (var e : canonical.entrySet()) {
            headerLines.append(e.getKey()).append(':').append(String.join(",", e.getValue())).append('\n');
            if (!signedHeaders.isEmpty()) {
                signedHeaders.append(';');
            }
            signedHeaders.append(e.getKey());
        }

        var canonicalRequest = c.method + '\n'
                + canonicalPath(uri)
                + '\n'
                + canonicalQuery(uri)
                + '\n'
                + headerLines
                + '\n'
                + signedHeaders
                + '\n'
                + payloadHash;
        var scope = dateStamp + '/' + c.region + '/' + c.service + "/aws4_request";
        var stringToSign = "AWS4-HMAC-SHA256\n" + dateTime + '\n' + scope + '\n'
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] key = hmac(("AWS4" + identity.secretAccessKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
        key = hmac(key, c.region);
        key = hmac(key, c.service);
        key = hmac(key, "aws4_request");
        var signature = HexFormat.of().formatHex(hmac(key, stringToSign));
        return "AWS4-HMAC-SHA256 Credential=" + identity.accessKeyId() + '/' + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private static String canonicalPath(URI uri) {
        var raw = uri.getRawPath();
        if (raw == null || raw.isEmpty()) {
            return "/";
        }
        return URLEncoding.encodeUnreserved(raw, true);
    }

    private static String canonicalQuery(URI uri) {
        var raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        var pairs = new ArrayList<String[]>();
        for (var part : raw.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            var k = eq == -1 ? part : part.substring(0, eq);
            var v = eq == -1 ? "" : part.substring(eq + 1);
            pairs.add(new String[] {
                    URLEncoding.encodeUnreserved(URLEncoding.urlDecode(k), false),
                    URLEncoding.encodeUnreserved(URLEncoding.urlDecode(v), false)});
        }
        pairs.sort((a, b) -> a[0].equals(b[0]) ? a[1].compareTo(b[1]) : a[0].compareTo(b[0]));
        var sb = new StringBuilder();
        for (var p : pairs) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(p[0]).append('=').append(p[1]);
        }
        return sb.toString();
    }

    private static byte[] bodyBytes(Case c) {
        return c.body == null ? new byte[0] : c.body.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpRequest request(Case c) {
        var headers = new LinkedHashMap<String, List<String>>(c.headers);
        var builder = HttpRequest.create()
                .setMethod(c.method)
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI.create(c.uri))
                .setHeaders(HttpHeaders.of(headers));
        if (c.body != null) {
            builder.setBody(DataStream.ofBytes(bodyBytes(c)));
        }
        return builder.toUnmodifiable();
    }

    private static Context properties(Case c) {
        return Context.create()
                .put(SigV4Settings.REGION, c.region)
                .put(SigV4Settings.SIGNING_NAME, c.service)
                .put(SigV4Settings.CLOCK, Clock.fixed(TIME, ZoneOffset.UTC));
    }
}
