package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * Interface that provides common Sigv4 signing methods for request signing and validation
 */
public abstract class AbstractSigv4Signer {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(AbstractSigv4Signer.class);
    protected static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    protected static final List<String> HEADERS_TO_IGNORE_IN_LOWER_CASE = List.of(
            "connection",
            "x-amzn-trace-id",
            "user-agent",
            "expect"
    );

    private static String HMAC_SHA_256 = "HmacSHA256";


    static String getCanonicalRequest(String method, URI uri, ) {
        String canonicalRequest = method + "\n"
                + uri.normalize().getRawPath() + "\n"
                + getCanonicalizedQueryString(uri) + "\n"
                + getCanonicalizedHeaderString(headers, sortedHeaderKeys) + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        LOGGER.trace("AWS SigV4 Canonical Request: '{}'", canonicalRequest);
    }

    /**
     * <pre>
     *
     * </pre>
     * @param headers
     * @param sortedHeaderKeys
     * @return
     */
    protected static String getCanonicalizedHeaderString(
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




    /**
     * AWS4 uses a series of derived keys, formed by hashing different pieces of data
     */
    static byte[] deriveSigningKey(String secretKey, String dateStamp, String regionName, String serviceName) {
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = sign(dateStamp, kSecret);
        var kRegion = sign(regionName, kDate);
        var kService = sign(serviceName, kRegion);
        return sign(TERMINATOR, kService);
    }

    /**
     * Used by clients and servers to compute the Signature for a request.
     *
     * @param canonicalRequest
     * @param scope
     * @param requestTime formatted Date Time of to use as signing time
     * @param signingKey
     * @return
     */
    static byte[] computeSignature(
            String canonicalRequest,
            String scope,
            String requestTime,
            byte[] signingKey
    ) {
        var stringToSign = ALGORITHM + "\n"
                + requestTime + "\n"
                + scope + "\n"
                + HexFormat.of().formatHex(hash(canonicalRequest));
        return sign(stringToSign.getBytes(StandardCharsets.UTF_8), signingKey);
    }

    static byte[] sign(String data, byte[] key) {
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
}
