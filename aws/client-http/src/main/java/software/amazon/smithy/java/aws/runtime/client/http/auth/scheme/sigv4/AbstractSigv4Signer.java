/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.smithy.java.logging.InternalLogger;

// TODO: move into shared location for both server and client
/**
 * Abstract class that provides common Sigv4 signing methods for request signing and validation.
 */
public abstract class AbstractSigv4Signer {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(AbstractSigv4Signer.class);
    protected static final String ALGORITHM = "AWS4-HMAC-SHA256";
    protected static final String TERMINATOR = "aws4_request";
    protected static final List<String> HEADERS_TO_IGNORE_IN_LOWER_CASE = List.of(
        "connection",
        "x-amzn-trace-id",
        "user-agent",
        "expect"
    );
    private static final String HMAC_SHA_256 = "HmacSHA256";

    /**
     * Characters that we need to fix up after URLEncoder.encode().
     */
    private static final String[] ENCODED_CHARACTERS_WITH_SLASHES = new String[]{"+", "*", "%7E", "%2F", "%25"};
    private static final String[] ENCODED_CHARACTERS_WITH_SLASHES_REPLACEMENTS = new String[]{"%20", "%2A", "~", "/", "%"};
    private static final String[] ENCODED_CHARACTERS_WITHOUT_SLASHES = new String[]{"+", "*", "%7E", "%25"};
    private static final String[] ENCODED_CHARACTERS_WITHOUT_SLASHES_REPLACEMENTS = new String[]{"%20", "%2A", "~", "%"};

    protected static String getCanonicalRequest(
        String method,
        URI uri,
        Map<String, List<String>> headers,
        Set<String> sortedHeaderKeys,
        String signedHeaders,
        String payloadHash
    ) {
        // TODO: allow un-normalized uri? this is the same step as:
        //  https://github.com/aws/aws-sdk-java-v2/blob/master/core/auth/src/main/java/software/amazon/awssdk/auth/signer/internal/AbstractAws4Signer.java#L525
        String canonicalRequest = method + "\n"
            + getCanonicalizedResourcePath(uri) + "\n"
            + getCanonicalizedQueryString(uri) + "\n"
            + getCanonicalizedHeaderString(headers, sortedHeaderKeys) + "\n"
            + signedHeaders + "\n"
            + payloadHash;
        LOGGER.trace("AWS SigV4 Canonical Request: '{}'", canonicalRequest);
        return canonicalRequest;
    }

    // Based on: https://github.com/aws/aws-sdk-java-v2/blob/master/core/auth/src/main/java/software/amazon/awssdk/auth/signer/internal/AbstractAws4Signer.java#L525
    private static String getCanonicalizedResourcePath(URI uri) {
        // TODO: handle unnormalized?
        String path = uri.normalize().getRawPath();
        if (path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // TODO: allow unencoded?
        return urlEncode(path, true);
    }

    private static String getCanonicalizedQueryString(URI uri) {
        SortedMap<String, String> sorted = new TreeMap<>();

        // Getting the raw query means the keys and values don't need to be encoded again.
        var query = uri.getRawQuery();
        if (query == null) {
            return "";
        }

        var params = query.split("&");

        for (var param : params) {
            var keyVal = param.split("=");
            var key = keyVal[0];
            var encodedKey = urlEncode(key, false);
            if (keyVal.length == 2) {
                var encodedValue = urlEncode(keyVal[1], false);
                sorted.put(encodedKey, encodedValue);
            } else {
                sorted.put(key, "");
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

    private static String getCanonicalizedHeaderString(
        Map<String, List<String>> headers,
        Set<String> sortedHeaderKeys
    ) {
        // 2048 was determined experimentally by the JavaSdkV2 to minimize resizing.
        var buffer = new StringBuilder(2048);
        for (var headerKey : sortedHeaderKeys) {
            var lowerCaseHeader = headerKey.toLowerCase(Locale.ENGLISH);
            if (HEADERS_TO_IGNORE_IN_LOWER_CASE.contains(lowerCaseHeader)) {
                continue;
            }
            buffer.append(lowerCaseHeader);
            buffer.append(":");
            for (String headerValue : headers.get(headerKey)) {
                addAndTrim(buffer, headerValue);
                buffer.append(",");
            }
            buffer.setLength(buffer.length() - 1);
            buffer.append("\n");
        }
        return buffer.toString();
    }

    /**
     * From JAVA V2 sdk: https://github.com/aws/aws-sdk-java-v2/blob/master/core/auth/src/main/java/software/amazon/awssdk/auth/signer/internal/AbstractAws4Signer.java#L651
     * "The addAndTrim function removes excess white space before and after values,
     * and converts sequential spaces to a single space."
     * <p>
     * <a href="https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html">...</a>
     * <p>
     * The collapse-whitespace logic is equivalent to:
     * <pre>
     *     value.replaceAll("\\s+", " ")
     * </pre>
     * but does not create a Pattern object that needs to compile the match
     * string; it also prevents us from having to make a Matcher object as well.
     */
    private static void addAndTrim(StringBuilder result, String value) {
        int lengthBefore = result.length();
        boolean isStart = true;
        boolean previousIsWhiteSpace = false;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isWhiteSpace(ch)) {
                if (previousIsWhiteSpace || isStart) {
                    continue;
                }
                result.append(' ');
                previousIsWhiteSpace = true;
            } else {
                result.append(ch);
                isStart = false;
                previousIsWhiteSpace = false;
            }
        }

        if (lengthBefore == result.length()) {
            return;
        }

        int lastNonWhitespaceChar = result.length() - 1;
        while (isWhiteSpace(result.charAt(lastNonWhitespaceChar))) {
            --lastNonWhitespaceChar;
        }

        result.setLength(lastNonWhitespaceChar + 1);
    }

    /**
     * Tests a char to see if is it whitespace.
     * This method considers the same characters to be white
     * space as the Pattern class does when matching {@code \s}
     *
     * @param ch the character to be tested
     * @return true if the character is white  space, false otherwise.
     */
    private static boolean isWhiteSpace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\u000b' || ch == '\r' || ch == '\f';
    }

    /**
     * AWS4 uses a series of derived keys, formed by hashing different pieces of data
     */
    protected static byte[] deriveSigningKey(
        String secretKey,
        String dateStamp,
        String regionName,
        String serviceName
    ) {
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = sign(dateStamp, kSecret);
        var kRegion = sign(regionName, kDate);
        var kService = sign(serviceName, kRegion);
        return sign(TERMINATOR, kService);
    }

    protected static String computeSignature(
        String canonicalRequest,
        String scope,
        String requestTime,
        byte[] signingKey
    ) {
        var stringToSign = ALGORITHM + "\n"
            + requestTime + "\n"
            + scope + "\n"
            + HexFormat.of().formatHex(hash(canonicalRequest));
        return HexFormat.of().formatHex(sign(stringToSign.getBytes(StandardCharsets.UTF_8), signingKey));
    }

    protected static byte[] sign(String data, byte[] key) {
        try {
            return sign(data.getBytes(StandardCharsets.UTF_8), key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] sign(byte[] data, byte[] key) {
        try {
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hash(String str) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(str.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: Should this go in a utils package?
    private static String urlEncode(String string, boolean ignoreSlashes) {
        var encodedString = URLEncoder.encode(string, StandardCharsets.UTF_8);
        // Consider using this method from JAVAV2 if this is a performance issue:
        // https://github.com/aws/aws-sdk-java-v2/blob/master/utils/src/main/java/software/amazon/awssdk/utils/StringUtils.java#L747
        var chars = ignoreSlashes ? ENCODED_CHARACTERS_WITH_SLASHES : ENCODED_CHARACTERS_WITHOUT_SLASHES;
        var replacements = ignoreSlashes
            ? ENCODED_CHARACTERS_WITH_SLASHES_REPLACEMENTS
            : ENCODED_CHARACTERS_WITHOUT_SLASHES_REPLACEMENTS;
        for (int i = 0; i < chars.length; ++i) {
            encodedString = encodedString.replace(chars[i], replacements[i]);
        }
        return encodedString;
    }
}
