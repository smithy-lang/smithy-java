/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import software.amazon.smithy.java.http.api.HeaderNames;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * HTTP/1.1 parsing utilities.
 *
 * <p>Uses {@link HeaderNames} for header name normalization.
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

        // Normalize header name using centralized registry
        String name = HeaderNames.canonicalize(buf, 0, colon);

        // Find value bounds, skip leading/trailing OWS (space or tab per RFC 9110)
        int valueStart = colon + 1;
        int valueEnd = len;
        while (valueStart < valueEnd && isOWS(buf[valueStart])) {
            valueStart++;
        }
        while (valueEnd > valueStart && isOWS(buf[valueEnd - 1])) {
            valueEnd--;
        }

        String value = new String(buf, valueStart, valueEnd - valueStart, java.nio.charset.StandardCharsets.US_ASCII);
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
