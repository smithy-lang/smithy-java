/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.EndpointAuthSchemeSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.io.uri.URLEncoding;

/**
 * The reference shares path normalization, percent-encoding, and the canonical-query shortcut with the production signer.
 */
final class StringSigV4Reference {

    private static final Set<String> EXCLUDED = Set.of(
            "x-amz-date",
            "authorization",
            "connection",
            "content-length",
            "transfer-encoding",
            "x-amzn-trace-id",
            "user-agent",
            "expect");
    private static final String REGION_SET_HEADER = "x-amz-region-set";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    /**
     * @param signature the hex signature for SigV4, or {@code null} for randomized SigV4a signatures.
     *                  The SigV4a Authorization header in {@code headers} ends at {@code Signature=}.
     */
    record Expected(
            String canonicalRequest,
            String stringToSign,
            String signature,
            Map<String, List<String>> headers) {}

    private StringSigV4Reference() {}

    static Expected sigV4(HttpRequest request, AwsCredentialsIdentity identity, Context properties) throws Exception {
        return sign(request, identity, properties, false);
    }

    static Expected sigV4a(HttpRequest request, AwsCredentialsIdentity identity, Context properties) throws Exception {
        return sign(request, identity, properties, true);
    }

    private static Expected sign(
            HttpRequest request,
            AwsCredentialsIdentity identity,
            Context properties,
            boolean asymmetric
    ) throws Exception {
        String service = properties.get(EndpointAuthSchemeSettings.SIGNING_NAME);
        if (service == null) {
            service = properties.expect(SigV4Settings.SIGNING_NAME);
        }
        String region = asymmetric ? null : region(properties);
        String regionSet = asymmetric ? regionSet(properties) : null;
        Clock clock = properties.getOrDefault(SigV4Settings.CLOCK, Clock.systemUTC());
        boolean omitToken = properties.getOrDefault(SigV4Settings.OMIT_SECURITY_TOKEN, false);
        boolean isS3 = service.equals("s3") || service.equals("s3express");
        boolean normalize = pathFlag(properties.get(SigV4Settings.NORMALIZE_PATH), !isS3);
        Boolean disableDouble = properties.get(EndpointAuthSchemeSettings.DISABLE_DOUBLE_ENCODING);
        boolean doubleEncode = disableDouble != null
                ? !disableDouble
                : pathFlag(properties.get(SigV4Settings.DOUBLE_URL_ENCODE), !isS3);
        String dateTime = STAMP.format(clock.instant());
        String dateStamp = dateTime.substring(0, 8);

        String payloadHash = properties.get(SigV4Settings.PAYLOAD_HASH_OVERRIDE);
        if (payloadHash == null) {
            payloadHash = request.headers().firstValue("x-amz-content-sha256");
        }
        if (payloadHash == null) {
            payloadHash = sha256Hex(bodyBytes(request.body()));
        }

        var uri = request.uri();
        var requestHeaders = request.headers();
        boolean addToken = !omitToken && identity.sessionToken() != null;

        var canonical = new TreeMap<String, List<String>>();
        requestHeaders.forEachEntry((name, value) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!EXCLUDED.contains(lower) && !(asymmetric && lower.equals(REGION_SET_HEADER))) {
                canonical.computeIfAbsent(lower, k -> new ArrayList<>()).add(canonicalHeaderValue(value));
            }
        });
        if (!requestHeaders.hasHeader("host")) {
            canonical.put("host", List.of(hostHeader(uri)));
        }
        canonical.put("x-amz-date", List.of(dateTime));
        if (asymmetric) {
            canonical.put(REGION_SET_HEADER, List.of(regionSet));
        }
        if (addToken) {
            canonical.put("x-amz-security-token", List.of(canonicalHeaderValue(identity.sessionToken())));
        }
        if (isS3) {
            canonical.put("x-amz-content-sha256", List.of(canonicalHeaderValue(payloadHash)));
        }

        var headerLines = new StringBuilder();
        var signedHeaders = new StringBuilder();
        for (var e : canonical.entrySet()) {
            headerLines.append(e.getKey()).append(':').append(String.join(",", e.getValue())).append('\n');
            if (!signedHeaders.isEmpty()) {
                signedHeaders.append(';');
            }
            signedHeaders.append(e.getKey());
        }

        String canonicalRequest = request.method() + '\n'
                + canonicalPath(uri, normalize, doubleEncode)
                + '\n'
                + canonicalQuery(uri.getQuery())
                + '\n'
                + headerLines
                + '\n'
                + signedHeaders
                + '\n'
                + payloadHash;
        String algorithm = asymmetric ? "AWS4-ECDSA-P256-SHA256" : "AWS4-HMAC-SHA256";
        String scope = dateStamp + '/' + (asymmetric ? "" : region + '/') + service + "/aws4_request";
        String stringToSign = algorithm + '\n'
                + dateTime
                + '\n'
                + scope
                + '\n'
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        String signature = null;
        if (!asymmetric) {
            byte[] key = hmac(("AWS4" + identity.secretAccessKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
            key = hmac(key, region);
            key = hmac(key, service);
            key = hmac(key, "aws4_request");
            signature = HexFormat.of().formatHex(hmac(key, stringToSign));
        }
        String authorization = algorithm + " Credential=" + identity.accessKeyId() + '/' + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + (signature == null ? "" : signature);

        var signed = request.toModifiable();
        var headers = signed.headers();
        headers.setHeader("x-amz-date", dateTime);
        if (asymmetric) {
            headers.setHeader(REGION_SET_HEADER, regionSet);
        }
        if (addToken) {
            headers.setHeader("x-amz-security-token", identity.sessionToken());
        }
        if (isS3) {
            headers.setHeader("x-amz-content-sha256", payloadHash);
        }
        headers.setHeader("authorization", authorization);
        return new Expected(canonicalRequest, stringToSign, signature, new TreeMap<>(signed.headers().map()));
    }

    private static String region(Context properties) {
        String region = properties.get(EndpointAuthSchemeSettings.SIGNING_REGION);
        return region != null ? region : properties.expect(RegionSetting.REGION);
    }

    private static String regionSet(Context properties) {
        List<String> set = properties.get(EndpointAuthSchemeSettings.SIGNING_REGION_SET);
        if (set == null || set.isEmpty()) {
            set = properties.get(SigV4aSettings.SIGNING_REGION_SET);
        }
        if (set == null || set.isEmpty()) {
            return region(properties);
        }
        var regions = new LinkedHashSet<String>();
        for (String region : set) {
            if (region == null) {
                throw new IllegalArgumentException("null region");
            }
            String trimmed = region.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains(",") || trimmed.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("invalid region " + region);
            }
            regions.add(trimmed);
        }
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("empty region set");
        }
        return String.join(",", regions);
    }

    static String canonicalHeaderValue(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhiteSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhiteSpace(value.charAt(end - 1))) {
            end--;
        }
        var sb = new StringBuilder(end - start);
        boolean previousWhiteSpace = false;
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (isWhiteSpace(c)) {
                if (!previousWhiteSpace) {
                    sb.append(' ');
                }
                previousWhiteSpace = true;
            } else {
                sb.append(c);
                previousWhiteSpace = false;
            }
        }
        return sb.toString();
    }

    static boolean isWhiteSpace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\f');
    }

    static String hostHeader(SmithyUri uri) {
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || (port == 80 && "http".equals(uri.getScheme()))
                || (port == 443 && "https".equals(uri.getScheme()));
        return defaultPort ? uri.getHost() : uri.getHost() + ':' + port;
    }

    private static boolean pathFlag(Boolean explicit, boolean fallback) {
        return explicit != null ? explicit : fallback;
    }

    static String canonicalPath(SmithyUri uri, boolean normalize, boolean doubleEncode) {
        String path = normalize ? uri.getNormalizedPath() : uri.getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.charAt(0) != '/') {
            path = "/" + path;
        }
        return doubleEncode ? URLEncoding.encodeUnreserved(path, true) : path;
    }

    static String canonicalQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        boolean alreadyCanonical = isAlreadyCanonical(query);
        var pairs = new ArrayList<String[]>();
        for (String part : query.split("&", -1)) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = eq == -1 ? part : part.substring(0, eq);
            String value = eq == -1 ? "" : part.substring(eq + 1);
            if (key.isEmpty()) {
                continue;
            }
            if (alreadyCanonical) {
                pairs.add(new String[] {key, value});
            } else {
                pairs.add(new String[] {
                        URLEncoding.encodeUnreserved(URLEncoding.urlDecode(key), false),
                        URLEncoding.encodeUnreserved(URLEncoding.urlDecode(value), false)});
            }
        }
        pairs.sort((a, b) -> a[0].equals(b[0]) ? a[1].compareTo(b[1]) : a[0].compareTo(b[0]));
        var sb = new StringBuilder();
        for (String[] p : pairs) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(p[0]).append('=').append(p[1]);
        }
        return sb.toString();
    }

    private static boolean isAlreadyCanonical(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '&' || c == '=' || URLEncoding.isUnreserved(c)) {
                continue;
            }
            if (c == '%' && i + 2 < len && isUppercaseHex(s.charAt(i + 1)) && isUppercaseHex(s.charAt(i + 2))) {
                i += 2;
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isUppercaseHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
    }

    static byte[] bodyBytes(DataStream body) {
        if (body == null || body.contentLength() == 0) {
            return new byte[0];
        }
        if (!body.hasKnownLength()) {
            throw new UnsupportedOperationException("unknown length body");
        }
        ByteBuffer buffer = body.asByteBuffer().duplicate();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    static byte[] hmac(byte[] key, String data) throws Exception {
        return hmac(key, data.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] hmac(byte[] key, byte[] data) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
