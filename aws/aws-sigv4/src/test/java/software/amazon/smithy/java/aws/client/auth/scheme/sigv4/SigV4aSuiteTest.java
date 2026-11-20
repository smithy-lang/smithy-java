/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.utils.IoUtils;

/**
 * Fixtures from the
 * <a href="https://github.com/awslabs/aws-c-auth/tree/main/tests/aws-signing-test-suite/v4a">AWS Common Runtime
 * signing test suite</a> are copied verbatim under {@code signing-v4a/}.
 *
 * <p>ECDSA signatures are randomized, so verify them with the suite's public key.
 *
 * <p>This fixture subset omits these upstream cases:
 * <ul>
 *     <li>{@code post-sts-header-after}, which requires an unsigned session token.</li>
 *     <li>{@code post-x-www-form-urlencoded} and {@code post-x-www-form-urlencoded-parameters},
 *     which sign {@code content-length}. Transports may rewrite this header.</li>
 *     <li>{@code get-space-normalized} and {@code get-space-unnormalized}, which contain literal spaces
 *     in the request target. {@code java.net.URI} rejects these spaces.</li>
 *     <li>{@code get-header-value-multiline}, {@code get-header-value-order}, {@code get-header-key-duplicate},
 *     {@code post-header-key-case}: duplicate or folded header lines the fixture format cannot express
 *     unambiguously.</li>
 * </ul>
 * {@code get-vanilla-query-order-key} and {@code get-vanilla-query-order-value} lack expected headers
 * and are also omitted.
 */
class SigV4aSuiteTest {

    private static final String SUITE = "signing-v4a";
    private static final Set<String> IGNORED_HEADERS = Set.of("content-length");

    record Case(
            String name,
            AwsCredentialsIdentity identity,
            Context properties,
            HttpRequest request,
            String canonicalRequest,
            String stringToSign,
            HttpRequest signed,
            String publicX,
            String publicY) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> cases() throws Exception {
        var root = Paths.get(Objects.requireNonNull(SigV4aSuiteTest.class.getResource(SUITE)).toURI());
        try (var dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .sorted()
                    .map(SigV4aSuiteTest::load)
                    .map(Arguments::of)
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void matchesCrtSuite(Case c) {
        String canonicalRequest;
        String stringToSign;
        HttpRequest signed;
        String signature;
        try (var signer = SigV4aSigner.create()) {
            var result = signer.sign(c.request, c.identity, c.properties);
            signed = result.signedRequest();
            signature = result.signature();
            canonicalRequest = signer.lastCanonicalRequest();
            stringToSign = signer.lastStringToSign();
        }

        assertThat(canonicalRequest).as("canonical request").isEqualTo(c.canonicalRequest);
        assertThat(stringToSign).as("string to sign").isEqualTo(c.stringToSign);

        var expectedHeaders = headersWithoutSignature(c.signed.headers());
        var actualHeaders = headersWithoutSignature(signed.headers());
        assertThat(actualHeaders).as("signed headers").isEqualTo(expectedHeaders);
        assertThat(signed.uri()).isEqualTo(c.request.uri());
        assertThat(signed.method()).isEqualTo(c.request.method());

        var authorization = signed.headers().firstValue("authorization");
        assertThat(SigV4aTestSupport.signatureOf(authorization)).isEqualTo(signature);
        // A DER-encoded P-256 signature starts with a SEQUENCE tag and is at most 72 bytes.
        assertThat(signature).matches("30(?:[0-9a-f]{2}){1,71}");
        var publicKey = P256TestMath.publicKey(c.publicX, c.publicY);
        assertThat(SigV4aTestSupport.verifies(publicKey, stringToSign, signature))
                .as("signature verifies under the suite's public key")
                .isTrue();
        var expectedSignature = SigV4aTestSupport.signatureOf(c.signed.headers().firstValue("authorization"));
        assertThat(SigV4aTestSupport.verifies(publicKey, stringToSign, expectedSignature))
                .as("CRT's signature verifies over our string to sign")
                .isTrue();
    }

    private static Map<String, List<String>> headersWithoutSignature(HttpHeaders headers) {
        var map = new LinkedHashMap<String, List<String>>();
        headers.map().forEach((name, values) -> {
            if (IGNORED_HEADERS.contains(name)) {
                return;
            }
            if (name.equals("authorization")) {
                map.put(name, values.stream().map(SigV4aTestSupport::withoutSignature).toList());
            } else {
                map.put(name, values);
            }
        });
        return map;
    }

    private static Case load(Path dir) {
        var name = dir.getFileName().toString();
        var context = Node.parse(read(dir, "context.json")).expectObjectNode();
        var credentials = context.expectObjectMember("credentials");
        var identity = AwsCredentialsIdentity.create(
                credentials.expectStringMember("access_key_id").getValue(),
                credentials.expectStringMember("secret_access_key").getValue(),
                credentials.getStringMemberOrDefault("token", null));
        var properties = Context.create()
                .put(SigV4Settings.SIGNING_NAME, context.expectStringMember("service").getValue())
                .put(SigV4Settings.REGION, context.expectStringMember("region").getValue())
                .put(SigV4Settings.NORMALIZE_PATH, context.expectBooleanMember("normalize").getValue())
                .put(SigV4Settings.CLOCK,
                        Clock.fixed(Instant.parse(context.expectStringMember("timestamp").getValue()), ZoneOffset.UTC));
        if (context.getBooleanMemberOrDefault("sign_body", false)) {
            throw new IllegalStateException("sign_body fixtures are not supported: " + name);
        }
        var request = parseRequest(read(dir, "request.txt"));
        var publicKey = Node.parse(read(dir, "public-key.json")).expectObjectNode();
        return new Case(
                name,
                identity,
                Context.unmodifiableCopy(properties),
                request,
                read(dir, "header-canonical-request.txt"),
                read(dir, "header-string-to-sign.txt"),
                parseRequest(read(dir, "header-signed-request.txt")),
                publicKey.expectStringMember("X").getValue(),
                publicKey.expectStringMember("Y").getValue());
    }

    static HttpRequest parseRequest(String text) {
        var lines = new ArrayList<>(text.lines().toList());
        var requestLine = lines.remove(0).split(" ");
        var method = requestLine[0];
        int end = requestLine.length - 1;
        if (!requestLine[end].equals("HTTP/1.1")) {
            throw new UnsupportedOperationException("Unsupported HTTP version " + requestLine[end]);
        }
        // Spaces inside the target are literal in the fixtures ("GET /example space/ HTTP/1.1").
        var target = String.join("%20", Arrays.copyOfRange(requestLine, 1, end));

        String host = null;
        var headers = new LinkedHashMap<String, List<String>>();
        StringBuilder body = null;
        boolean inBody = false;
        for (var line : lines) {
            if (inBody) {
                if (body == null) {
                    body = new StringBuilder(line);
                } else {
                    body.append('\n').append(line);
                }
                continue;
            }
            if (line.isEmpty()) {
                inBody = true;
                continue;
            }
            int colon = line.indexOf(':');
            var name = line.substring(0, colon);
            var value = line.substring(colon + 1);
            if (name.equalsIgnoreCase("host")) {
                host = value;
            }
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        var builder = HttpRequest.create()
                .setMethod(method)
                .setHttpVersion(HttpVersion.HTTP_1_1)
                // Keep non-ASCII path characters raw so the signer percent-encodes them once.
                .setUri(URI.create("http://" + Objects.requireNonNull(host, "host header") + target))
                .setHeaders(HttpHeaders.of(headers));
        if (body != null) {
            builder.setBody(DataStream.ofBytes(body.toString().getBytes(StandardCharsets.UTF_8)));
        }
        return builder.toUnmodifiable();
    }

    private static String read(Path dir, String file) {
        var path = dir.resolve(file);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Missing fixture file " + path);
        }
        return IoUtils.readUtf8File(path.toString());
    }

}
