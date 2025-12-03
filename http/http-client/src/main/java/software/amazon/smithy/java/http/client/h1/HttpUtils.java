/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * Interned HTTP header names to avoid String allocation for common headers.
 *
 * <p>Uses length-based switching and case-insensitive byte comparison to
 * return pre-allocated String constants for well-known headers.
 */
final class HttpUtils {

    private static final String[] GROUP_4 = {"date", "vary", "etag"};
    private static final String[] GROUP_6 = {"server"};
    private static final String[] GROUP_7 = {"trailer", "expires", "upgrade"};
    private static final String[] GROUP_8 = {"location"};
    private static final String[] GROUP_10 = {"connection", "keep-alive", "set-cookie"};
    private static final String[] GROUP_12 = {"content-type"};
    private static final String[] GROUP_13 = {"cache-control", "last-modified", "content-range", "accept-ranges"};
    private static final String[] GROUP_14 = {"content-length"};
    private static final String[] GROUP_16 = {
            "content-encoding",
            "x-amzn-requestid",
            "x-amz-request-id",
            "www-authenticate",
            "proxy-connection"
    };
    private static final String[] GROUP_17 = {"transfer-encoding"};
    private static final String[] GROUP_18 = {"proxy-authenticate"};

    private HttpUtils() {}

    /**
     * Returns an interned String for common header names, or creates a new String for unknown headers.
     *
     * @param buf byte buffer containing header name
     * @param start start offset in buffer
     * @param len length of header name
     * @return interned String for known headers, new String for unknown
     */
    static String internHeader(byte[] buf, int start, int len) {
        return switch (len) {
            case 4 -> match(buf, start, len, GROUP_4);
            case 6 -> match(buf, start, len, GROUP_6);
            case 7 -> match(buf, start, len, GROUP_7);
            case 8 -> match(buf, start, len, GROUP_8);
            case 10 -> match(buf, start, len, GROUP_10);
            case 12 -> match(buf, start, len, GROUP_12);
            case 13 -> match(buf, start, len, GROUP_13);
            case 14 -> match(buf, start, len, GROUP_14);
            case 16 -> match(buf, start, len, GROUP_16);
            case 17 -> match(buf, start, len, GROUP_17);
            case 18 -> match(buf, start, len, GROUP_18);
            default -> new String(buf, start, len, StandardCharsets.US_ASCII);
        };
    }

    private static String match(byte[] buf, int start, int len, String[] group) {
        for (String candidate : group) {
            if (equalsIgnoreCase(buf, start, candidate)) {
                return candidate;
            }
        }
        return new String(buf, start, len, StandardCharsets.US_ASCII);
    }

    private static boolean equalsIgnoreCase(byte[] buf, int start, String expected) {
        for (int i = 0; i < expected.length(); i++) {
            byte b = buf[start + i];
            // Convert to lowercase if uppercase ASCII letter
            if (b >= 'A' && b <= 'Z') {
                b += 32;
            }
            if (b != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse a header line and add it to the headers collection.
     *
     * @param buf byte buffer containing header line
     * @param len length of header line (excluding CRLF)
     * @param headers collection to add the parsed header to
     * @return the interned header name, or null if line is malformed (no colon)
     */
    static String parseHeaderLine(byte[] buf, int len, ModifiableHttpHeaders headers) {
        // Find colon
        int colon = -1;
        for (int i = 0; i < len; i++) {
            if (buf[i] == ':') {
                colon = i;
                break;
            }
        }

        if (colon <= 0) {
            return null;
        }

        // Intern header name
        String name = internHeader(buf, 0, colon);

        // Find value bounds, skip leading/trailing OWS (space or tab per RFC 9110)
        int valueStart = colon + 1;
        int valueEnd = len;
        while (valueStart < valueEnd && isOWS(buf[valueStart])) {
            valueStart++;
        }
        while (valueEnd > valueStart && isOWS(buf[valueEnd - 1])) {
            valueEnd--;
        }

        String value = new String(buf, valueStart, valueEnd - valueStart, StandardCharsets.US_ASCII);
        headers.addHeader(name, value);
        return name;
    }

    /**
     * Check if byte is optional whitespace (OWS) per RFC 9110: SP or HTAB.
     */
    private static boolean isOWS(byte b) {
        return b == ' ' || b == '\t';
    }
}
