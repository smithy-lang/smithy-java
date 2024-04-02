/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.sigv4;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS signature version 4 signing implementation.
 */
public final class SigV4 {

    private static final System.Logger LOGGER = System.getLogger(SigV4.class.getName());
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String serviceName;
    private final String regionName;

    /**
     * @param serviceName The service to be called, e.g., "glacier"
     * @param regionName  The region, e.g., "us-east-1".
     */
    public SigV4(String serviceName, String regionName) {
        this.serviceName = serviceName;
        this.regionName = regionName;
    }

    /**
     * Sign a given request/payload
     *
     * @param method
     * @param uri
     * @param httpHeaders
     * @param accessKeyId
     * @param secretKey
     * @param payload
     * @param isStreaming
     */
    public Map<String, List<String>> createSignedHeaders(
        String method,
        URI uri,
        HttpHeaders httpHeaders,
        String accessKeyId,
        String secretKey,
        InputStream payload,
        boolean isStreaming
    ) {
        return createSignedHeaders(
            method,
            uri,
            httpHeaders,
            accessKeyId,
            secretKey,
            payload,
            isStreaming,
            Instant.now()
        );
    }

    /**
     * Sign a given request/payload
     *
     * @param accessKeyId
     * @param secretKey
     * @param isStreaming
     * @param now The timestamp to use for the current time.
     */
    public Map<String, List<String>> createSignedHeaders(
        String method,
        URI uri,
        HttpHeaders httpHeaders,
        String accessKeyId,
        String secretKey,
        InputStream payload,
        boolean isStreaming,
        Instant now
    ) {
        var headers = httpHeaders.map();

        // AWS4 requires that we sign the Host header, so we have to have it in the request by the time we sign.
        var hostHeader = uri.getHost();
        if (uri.getPort() > 0) {
            hostHeader += ":" + uri.getPort();
        }
        headers.put("Host", List.of(hostHeader));

        var dateTime = DateTimeFormatter.ISO_INSTANT.format(now);
        var dateStamp = DATE_FORMATTER.format(now);
        headers.put("X-Amz-Date", List.of(dateTime));

        // Add the x-amz-content-sha256 header
        var payloadHash = HexFormat.of().formatHex(hash(payload));
        if (isStreaming) {
            headers.put("x-amz-content-sha256", List.of(payloadHash));
        }

        // Build canonicalRequest
        var canonicalRequest = method + "\n" + uri.getRawPath() + "\n" + getCanonicalizedQueryString(uri) + "\n"
            + getCanonicalizedHeaderString(httpHeaders) + "\n" + getSignedHeaders(httpHeaders) + "\n" + payloadHash;

        LOGGER.log(System.Logger.Level.TRACE, () -> "AWS4 Canonical Request: '" + canonicalRequest + "'");

        var scope = dateStamp + '/' + regionName + '/' + serviceName + '/' + TERMINATOR;
        var signingCredentials = accessKeyId + '/' + scope;
        var stringToSign = ALGORITHM + '\n' + dateTime + '\n' + scope + '\n'
            + HexFormat.of().formatHex(hash(canonicalRequest));

        LOGGER.log(System.Logger.Level.TRACE, () -> "AWS4 String to Sign: '\"" + stringToSign + "\"");

        // AWS4 uses a series of derived keys, formed by hashing different pieces of data
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = sign(dateStamp, kSecret, "HmacSHA256");
        var kRegion = sign(regionName, kDate, "HmacSHA256");
        var kService = sign(serviceName, kRegion, "HmacSHA256");
        var kSigning = sign(TERMINATOR, kService, "HmacSHA256");

        var signature = sign(stringToSign.getBytes(StandardCharsets.UTF_8), kSigning, "HmacSHA256");

        var credentialsAuthorizationHeader = "Credential=" + signingCredentials;
        var signedHeadersAuthorizationHeader = "SignedHeaders=" + getSignedHeaders(httpHeaders);
        var signatureAuthorizationHeader = "Signature=" + HexFormat.of().formatHex(signature);

        var authorizationHeader = ALGORITHM + ' ' + credentialsAuthorizationHeader + ", "
            + signedHeadersAuthorizationHeader + ", " + signatureAuthorizationHeader;

        LOGGER.log(System.Logger.Level.TRACE, () -> "Authorization: " + authorizationHeader);

        headers.put("Authorization", List.of(authorizationHeader));

        return headers;
    }

    private byte[] sign(String data, byte[] key, String signingAlgorithmName) {
        try {
            return sign(data.getBytes(StandardCharsets.UTF_8), key, signingAlgorithmName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] sign(byte[] data, byte[] key, String signingAlgorithmName) {
        try {
            var mac = Mac.getInstance(signingAlgorithmName);
            mac.init(new SecretKeySpec(key, signingAlgorithmName));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] hash(InputStream istream) {
        if (istream == null) {
            return hash("");
        }

        if (!istream.markSupported()) {
            throw new RuntimeException("Can't reset stream, mark not supported");
        }

        istream.mark(-1);

        try {
            var md = MessageDigest.getInstance("SHA-256");

            byte[] buff = new byte[4096];
            int len = istream.read(buff);
            while (len > 0) {
                md.update(buff, 0, len);
                len = istream.read(buff);
            }

            istream.reset();
            return md.digest();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] hash(String str) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(str.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getSignedHeaders(HttpHeaders headers) {
        // Sort header names/keys
        Set<String> sortedHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedHeaders.addAll(headers.map().keySet());

        var builder = new StringBuilder();
        for (var header : sortedHeaders) {
            if (!builder.isEmpty()) {
                builder.append(";");
            }
            builder.append(header.toLowerCase(Locale.ENGLISH));
        }

        return builder.toString();
    }

    private String getCanonicalizedHeaderString(HttpHeaders headers) {
        Set<String> sortedHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedHeaders.addAll(headers.map().keySet());

        // Iterate and output canonicalized string
        var buffer = new StringBuilder();
        for (var headerKey : sortedHeaders) {
            var headerValues = headers.allValues(headerKey);
            if (headerValues.size() != 1) {
                throw new RuntimeException("Duplicate headers: " + headerValues.get(0));
            }
            // Does not handle duplicate headers
            buffer.append(headerKey.toLowerCase(Locale.ENGLISH)).append(':');
            if (headerValues.get(0) != null) {
                buffer.append(headerValues.get(0).trim());
            }
            buffer.append("\n");
        }

        return buffer.toString();
    }

    private String getCanonicalizedQueryString(URI uri) {
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
}
