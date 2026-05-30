/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * HTTP/1.1 parsing utilities.
 *
 * <p>Uses {@link HeaderName} for header name normalization.
 */
final class H1Utils {

    private H1Utils() {}

    /**
     * Parse a header line and add it to the headers collection.
     *
     * @param buf byte buffer containing header line
     * @param len length of header line (excluding CRLF)
     * @param headers collection to add the parsed header to
     * @return the interned header name, or null if line is malformed (no colon)
     */
    static String parseHeaderLine(byte[] buf, int len, ModifiableHttpHeaders headers) {
        int colon = findHeaderColon(buf, len);
        if (colon <= 0) {
            return null;
        }

        int valueStart = headerValueStart(buf, colon, len);
        int valueEnd = headerValueEnd(buf, valueStart, len);
        return parseHeaderLine(buf, colon, valueStart, valueEnd, headers);
    }

    static String parseHeaderLine(
            byte[] buf,
            int colon,
            int valueStart,
            int valueEnd,
            ModifiableHttpHeaders headers
    ) {
        // Normalize header name using centralized registry
        String name = HeaderName.canonicalize(buf, 0, colon);

        String value = new String(buf, valueStart, valueEnd - valueStart, StandardCharsets.US_ASCII);
        headers.addHeaderCanonical(name, value);
        return name;
    }

    static int findHeaderColon(byte[] buf, int len) {
        for (int i = 0; i < len; i++) {
            if (buf[i] == ':') {
                return i;
            }
        }
        return -1;
    }

    static int headerValueStart(byte[] buf, int colon, int len) {
        int valueStart = colon + 1;
        while (valueStart < len && isOWS(buf[valueStart])) {
            valueStart++;
        }
        return valueStart;
    }

    static int headerValueEnd(byte[] buf, int valueStart, int len) {
        int valueEnd = len;
        while (valueEnd > valueStart && isOWS(buf[valueEnd - 1])) {
            valueEnd--;
        }
        return valueEnd;
    }

    /**
     * Check if byte is optional whitespace (OWS) per RFC 9110: SP or HTAB.
     */
    private static boolean isOWS(byte b) {
        return b == ' ' || b == '\t';
    }
}
