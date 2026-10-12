/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * Keep draws bounded so exhausted fuzz input still produces a valid case.
 */
final class SigV4CaseGenerator {

    interface Source {
        /** Returns a uniform value in {@code [0, bound)}. {@code bound} must be positive. */
        int nextInt(int bound);

        boolean nextBoolean();

        /** Returns a uniform value in {@code [0, bound)}. {@code bound} must be positive. */
        long nextLong(long bound);

        /** Up to {@code maxLength} bytes. */
        byte[] nextBytes(int maxLength);

        default <T> T pick(T[] items) {
            return items[nextInt(items.length)];
        }

        default boolean chance(int percent) {
            return nextInt(100) < percent;
        }
    }

    static Source of(Random random) {
        return new Source() {
            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }

            @Override
            public boolean nextBoolean() {
                return random.nextBoolean();
            }

            @Override
            public long nextLong(long bound) {
                return random.nextLong(bound);
            }

            @Override
            public byte[] nextBytes(int maxLength) {
                byte[] bytes = new byte[random.nextInt(maxLength + 1)];
                random.nextBytes(bytes);
                return bytes;
            }
        };
    }

    record SignCase(
            HttpRequest request,
            AwsCredentialsIdentity identity,
            Context properties,
            String description) {
        @Override
        public String toString() {
            return description;
        }
    }

    private record Credentials(String accessKeyId, String secretAccessKey, String sessionToken) {}

    private static final String[] METHODS = {"GET", "POST", "PUT", "DELETE", "HEAD", "PATCH"};
    private static final String[] REGIONS = {"us-east-1", "us-west-2", "eu-west-1", "ap-southeast-2", "cn-north-1"};
    private static final String[] SERVICES = {"service", "s3", "s3express", "execute-api", "lambda", "dynamodb"};
    private static final String[] HOSTS = {
            "example.amazonaws.com",
            "bucket.s3.amazonaws.com",
            "localhost",
            "127.0.0.1",
            "xn--bcher-kva.example"};
    private static final Integer[] PORTS = {-1, -1, -1, 80, 443, 8080, 8443};
    private static final String[] SEGMENTS = {
            "",
            ".",
            "..",
            "a.b",
            "foo",
            "bar",
            "Baz",
            "x-y_z~0",
            "%20",
            "%2F",
            "%7e",
            "%7E",
            "%3A",
            "%E1%88%B4",
            "%41",
            ":",
            "@",
            "+",
            "=",
            "!",
            "$",
            "'",
            "(",
            ")",
            "*",
            ",",
            ";",
            "..."};
    private static final String[] QUERY_KEYS = {
            "a",
            "b",
            "A",
            "z",
            "key",
            "Key",
            "k%20y",
            "%7e",
            "%7E",
            "~",
            "x.y",
            "%E1%88%B4",
            "+",
            "list",
            "%41",
            "a%3Db"};
    private static final String[] QUERY_VALUES = {
            "",
            "1",
            "2",
            "10",
            "v%20w",
            "%2F",
            "%7e",
            "~",
            "a=b",
            "x+y",
            "%41",
            "%E1%88%B4",
            "%FF",
            "%C3%A9",
            "%4"};
    private static final String[] KNOWN_HEADERS = {
            "host",
            "content-type",
            "x-amz-date",
            "authorization",
            "user-agent",
            "content-length",
            "transfer-encoding",
            "connection",
            "expect",
            "x-amzn-trace-id",
            "x-amz-security-token",
            "x-amz-content-sha256",
            "x-amz-target",
            "accept",
            "range",
            "content-md5",
            "x-amz-user-agent",
            "amz-sdk-request",
            "amz-sdk-invocation-id"};
    private static final String[] PAYLOAD_OVERRIDES = {
            "UNSIGNED-PAYLOAD",
            "STREAMING-UNSIGNED-PAYLOAD-TRAILER",
            "STREAMING-AWS4-HMAC-SHA256-PAYLOAD"};
    private static final String[] SHA256_HEADER_VALUES = {
            "UNSIGNED-PAYLOAD",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "STREAMING-UNSIGNED-PAYLOAD-TRAILER"};
    private static final Character[] NON_ASCII_SAMPLES = {'é', 'ü', 'ÿ', '\u00A0', 'A', 'z'};
    private static final Credentials[] CREDENTIALS = {
            new Credentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null),
            new Credentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "AQoDYXdzEJr/session/token"),
            new Credentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", null),
            new Credentials("ASIAEXAMPLE", "short", "tok"),
            // The "AWS4" prefix makes these secrets span the 64-byte HMAC block boundary.
            new Credentials("AKID60", "y".repeat(60), null),
            new Credentials("AKID61", "z".repeat(61), null),
            new Credentials("AKIDLONG", "x".repeat(100), "t".repeat(300)),
    };
    private static final long[] ANCHOR_SECONDS = {
            0L,
            1_440_938_160L, // 2015-08-30T12:36:00Z, the official test vectors
            1_780_358_399L, // 2026-06-01T23:59:59Z
            951_782_400L, // 2000-02-29T00:00:00Z
            4_107_542_399L, // 2100-02-28T23:59:59Z
            1_767_225_600L, // 2026-01-01T00:00:00Z
    };
    private static final int[] BODY_SIZES = {0, 1, 7, 8, 9, 63, 64, 65, 1000, 8191, 8192, 8193, 20_000};

    private SigV4CaseGenerator() {}

    static SignCase next(Source s) {
        var description = new StringBuilder();

        Credentials credentials = s.chance(85) ? s.pick(CREDENTIALS) : freshCredentials(s);
        var identity = credentials.sessionToken == null
                ? AwsCredentialsIdentity.create(credentials.accessKeyId, credentials.secretAccessKey)
                : AwsCredentialsIdentity.create(credentials.accessKeyId,
                        credentials.secretAccessKey,
                        credentials.sessionToken);
        description.append("identity=").append(credentials.accessKeyId);
        if (credentials.sessionToken != null) {
            description.append("+token");
        }

        String region = s.chance(85) ? s.pick(REGIONS) : token(s, 1, 12);
        String service = s.chance(70) ? s.pick(SERVICES) : token(s, 1, 10);
        long second = second(s);
        description.append(" scope=").append(region).append('/').append(service).append(" second=").append(second);

        String method = s.pick(METHODS);
        String scheme = s.nextBoolean() ? "https" : "http";
        String host = s.pick(HOSTS);
        int port = s.pick(PORTS);
        String path = path(s);
        String query = query(s);
        SmithyUri uri;
        try {
            uri = SmithyUri.of(scheme, host, port, path, query);
        } catch (IllegalArgumentException e) {
            uri = SmithyUri.of(scheme, host, port, "/", null);
        }
        description.append(' ').append(method).append(' ').append(uri);

        var headers = new LinkedHashMap<String, List<String>>();
        boolean allowNonAscii = s.chance(50);
        int headerCount = s.nextInt(9);
        for (int i = 0; i < headerCount; i++) {
            String name = headerName(s);
            String lower = name.toLowerCase(Locale.ROOT);
            var values = new ArrayList<String>();
            int valueCount = 1 + (s.chance(25) ? s.nextInt(3) : 0);
            for (int v = 0; v < valueCount; v++) {
                values.add(headerValue(s, lower, allowNonAscii));
            }
            headers.computeIfAbsent(name, k -> new ArrayList<>()).addAll(values);
        }
        description.append(" headers=").append(headers);

        var request = HttpRequest.create()
                .setMethod(method)
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(uri)
                .setHeaders(HttpHeaders.of(headers));
        description.append(" body=").append(body(s, request));

        var properties = Context.create()
                .put(SigV4Settings.REGION, region)
                .put(SigV4Settings.SIGNING_NAME, service)
                .put(SigV4Settings.CLOCK, Clock.fixed(Instant.ofEpochSecond(second), ZoneOffset.UTC));
        if (s.chance(15)) {
            boolean omit = s.nextBoolean();
            properties.put(SigV4Settings.OMIT_SECURITY_TOKEN, omit);
            description.append(" omitToken=").append(omit);
        }
        if (s.chance(15)) {
            String override = s.pick(PAYLOAD_OVERRIDES);
            properties.put(SigV4Settings.PAYLOAD_HASH_OVERRIDE, override);
            description.append(" override=").append(override);
        }
        if (s.chance(15)) {
            String endpointName = s.pick(SERVICES);
            properties.put(EndpointAuthSchemeSettings.SIGNING_NAME, endpointName);
            description.append(" endpointName=").append(endpointName);
        }
        if (s.chance(15)) {
            String endpointRegion = s.pick(REGIONS);
            properties.put(EndpointAuthSchemeSettings.SIGNING_REGION, endpointRegion);
            description.append(" endpointRegion=").append(endpointRegion);
        }

        return new SignCase(request.toUnmodifiable(), identity, properties, description.toString());
    }

    private static Credentials freshCredentials(Source s) {
        return new Credentials(
                "AKID" + token(s, 1, 16).toUpperCase(Locale.ROOT),
                token(s, 1, 80),
                s.nextBoolean() ? null : token(s, 0, 40));
    }

    private static long second(Source s) {
        int k = s.nextInt(100);
        if (k < 40) {
            return s.pick(boxed(ANCHOR_SECONDS));
        }
        if (k < 60) {
            return s.pick(boxed(ANCHOR_SECONDS)) + s.nextInt(3) - 1;
        }
        if (k < 80) {
            return s.nextLong(80_000L) * 86_400L + s.nextInt(3) - 1;
        }
        return s.nextLong(7_258_118_400L); // up to 2200-01-01
    }

    private static Long[] boxed(long[] values) {
        Long[] out = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i];
        }
        return out;
    }

    private static String path(Source s) {
        if (s.chance(5)) {
            return "";
        }
        var sb = new StringBuilder();
        if (s.chance(90)) {
            sb.append('/');
        }
        int segments = s.nextInt(6);
        for (int i = 0; i < segments; i++) {
            if (i > 0) {
                sb.append(s.chance(15) ? "//" : "/");
            }
            sb.append(s.chance(60) ? token(s, 1, 8) : s.pick(SEGMENTS));
        }
        if (segments > 0 && s.chance(20)) {
            sb.append('/');
        }
        return sb.toString();
    }

    private static String query(Source s) {
        int k = s.nextInt(100);
        if (k < 30) {
            return null;
        }
        if (k < 35) {
            return "";
        }
        var sb = new StringBuilder();
        if (s.chance(5)) {
            sb.append('&');
        }
        int pairs = 1 + s.nextInt(6);
        for (int i = 0; i < pairs; i++) {
            if (i > 0) {
                sb.append(s.chance(10) ? "&&" : "&");
            }
            if (s.chance(5)) {
                sb.append('=');
            }
            sb.append(s.chance(50) ? token(s, 1, 6) : s.pick(QUERY_KEYS));
            if (s.chance(80)) {
                sb.append('=').append(s.chance(50) ? token(s, 0, 8) : s.pick(QUERY_VALUES));
            }
        }
        if (s.chance(10)) {
            sb.append('&');
        }
        return sb.toString();
    }

    private static String headerName(Source s) {
        String name = s.chance(70) ? s.pick(KNOWN_HEADERS) : "x-" + token(s, 1, 10);
        if (s.chance(30)) {
            char[] chars = name.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                if (chars[i] >= 'a' && chars[i] <= 'z' && s.chance(40)) {
                    chars[i] = (char) (chars[i] - 32);
                }
            }
            name = new String(chars);
        }
        return name;
    }

    private static String headerValue(Source s, String lowerName, boolean allowNonAscii) {
        switch (lowerName) {
            case "host" -> {
                return s.pick(HOSTS) + (s.chance(30) ? ":" + s.pick(PORTS) : "");
            }
            case "x-amz-content-sha256" -> {
                // Payload hash values must be ASCII.
                return s.chance(70) ? s.pick(SHA256_HEADER_VALUES) : randomValue(s, false);
            }
            case "content-length" -> {
                return Integer.toString(s.nextInt(100_000));
            }
            default -> {
            }
        }
        return randomValue(s, allowNonAscii);
    }

    private static String randomValue(Source s, boolean allowNonAscii) {
        int length = s.nextInt(26);
        if (s.chance(10)) {
            length += 8 * (1 + s.nextInt(4));
        }
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int k = s.nextInt(100);
            char c;
            if (k < 60) {
                c = (char) (0x21 + s.nextInt(0x5E));
            } else if (k < 75) {
                c = ' ';
            } else if (k < 83) {
                c = '\t';
            } else if (k < 95) {
                c = allowNonAscii ? (char) (0x80 + s.nextInt(0x80)) : (char) ('a' + s.nextInt(26));
            } else {
                c = allowNonAscii ? s.pick(NON_ASCII_SAMPLES) : 'A';
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String body(Source s, ModifiableHttpRequest request) {
        int k = s.nextInt(100);
        if (k < 25) {
            return "none";
        }
        byte[] bytes = s.chance(50) ? s.nextBytes(2048) : fixedSizeBytes(s);
        if (k < 35) {
            request.setBody(DataStream.ofBytes(new byte[0]));
            return "empty";
        }
        if (k < 60) {
            request.setBody(DataStream.ofBytes(bytes));
            return "heap[" + bytes.length + "]";
        }
        if (k < 70) {
            var direct = ByteBuffer.allocateDirect(bytes.length);
            direct.put(bytes).flip();
            request.setBody(DataStream.ofByteBuffer(direct));
            return "direct[" + bytes.length + "]";
        }
        if (k < 80) {
            int pad = s.nextInt(10);
            byte[] padded = new byte[bytes.length + pad + 5];
            System.arraycopy(bytes, 0, padded, pad, bytes.length);
            request.setBody(DataStream.ofByteBuffer(ByteBuffer.wrap(padded, pad, bytes.length).asReadOnlyBuffer()));
            return "readOnly[" + bytes.length + "@" + pad + "]";
        }
        if (k < 90) {
            int pad = s.nextInt(10);
            byte[] padded = new byte[bytes.length + pad + 5];
            System.arraycopy(bytes, 0, padded, pad, bytes.length);
            request.setBody(DataStream.ofByteBuffer(ByteBuffer.wrap(padded, pad, bytes.length).slice()));
            return "slice[" + bytes.length + "@" + pad + "]";
        }
        request.setBody(DataStream.ofInputStream(new ByteArrayInputStream(bytes)));
        return "unknownLength[" + bytes.length + "]";
    }

    private static byte[] fixedSizeBytes(Source s) {
        int size = BODY_SIZES[s.nextInt(BODY_SIZES.length)];
        byte[] bytes = new byte[size];
        byte[] seed = s.nextBytes(16);
        for (int i = 0; i < size; i++) {
            bytes[i] = seed.length == 0 ? (byte) i : (byte) (seed[i % seed.length] + i);
        }
        return bytes;
    }

    private static String token(Source s, int minLength, int maxLength) {
        int length = minLength + s.nextInt(maxLength - minLength + 1);
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int k = s.nextInt(37);
            sb.append(k < 26 ? (char) ('a' + k) : k < 36 ? (char) ('0' + k - 26) : '-');
        }
        return sb.toString();
    }
}
