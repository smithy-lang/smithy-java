/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.List;
import java.util.Map;

/**
 * Contains case-insensitive HTTP headers.
 *
 * <p>Implementations must always normalize header names to lowercase.
 */
public interface HttpHeaders extends Iterable<Map.Entry<String, List<String>>> {

    /**
     * Create an immutable HttpHeaders.
     *
     * @param headers Headers to set.
     * @return the created headers.
     */
    static HttpHeaders of(Map<String, List<String>> headers) {
        return headers.isEmpty() ? SimpleUnmodifiableHttpHeaders.EMPTY : new SimpleUnmodifiableHttpHeaders(headers);
    }

    /**
     * Creates a mutable headers.
     *
     * @return the created headers.
     */
    static ModifiableHttpHeaders ofModifiable() {
        return new SimpleModifiableHttpHeaders();
    }

    /**
     * Check if the given header is present.
     *
     * @param name Header to check.
     * @return true if the header is present.
     */
    default boolean hasHeader(String name) {
        return !allValues(name).isEmpty();
    }

    /**
     * Get the first header value of a specific header by case-insensitive name.
     *
     * @param name Name of the header to get.
     * @return the matching header value, or null if not found.
     */
    default String firstValue(String name) {
        var list = allValues(name);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Get the values of a specific header by name.
     *
     * @param name Name of the header to get the values of, case-insensitively.
     * @return the values of the header, or an empty list.
     */
    List<String> allValues(String name);

    /**
     * Get the content-type header, or null if not found.
     *
     * @return the content-type header or null.
     */
    default String contentType() {
        return firstValue("content-type");
    }

    /**
     * Get the content-length header value, or null if not found.
     *
     * @return the parsed content-length or null.
     */
    default Long contentLength() {
        var value = firstValue("content-length");
        return value == null ? null : Long.parseLong(value);
    }

    /**
     * Get the number of header entries (not individual values).
     *
     * @return header entries.
     */
    int size();

    /**
     * Check if there are no headers.
     *
     * @return true if no headers.
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Convert the HttpHeader to an unmodifiable map.
     *
     * @return the headers as a map.
     */
    Map<String, List<String>> map();

    /**
     * Get or create a modifiable version of the headers.
     *
     * @return the created modifiable headers.
     */
    ModifiableHttpHeaders toModifiable();

    /**
     * Normalizes an HTTP header name by trimming whitespace and converting ASCII uppercase to lowercase.
     *
     * <p>Trimming behavior matches {@link String#trim()}, removing characters {@code <= 'u0020'}.
     * Only ASCII uppercase letters (A-Z) are lowercased; non-ASCII characters pass through unchanged,
     * which is correct per RFC 7230 (HTTP/1.1) and RFC 9110 (HTTP semantics) since header field names
     * are defined as ASCII tokens.
     *
     * @param name the header name to normalize
     * @return the normalized header name, or the original instance if already normalized
     */
    static String normalizeHeaderName(String name) {
        int len = name.length();
        int start = 0;
        int end = len - 1;
        boolean needsWork = false;

        // Detect leading whitespace to trim if needed
        while (start <= end && name.charAt(start) <= ' ') {
            needsWork = true;
            start++;
        }

        // Detect trailing whitespace to trim if needed
        while (end >= start && name.charAt(end) <= ' ') {
            needsWork = true;
            end--;
        }

        // All whitespace
        if (start > end) {
            return "";
        }

        // Scan for ASCII uppercase
        for (int i = start; i <= end; i++) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                needsWork = true;
                break;
            }
        }

        if (!needsWork) {
            return name;
        }

        int outLen = end - start + 1;
        char[] chars = new char[outLen];
        for (int src = start, dst = 0; src <= end; src++, dst++) {
            char c = name.charAt(src);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }
            chars[dst] = c;
        }

        return new String(chars);
    }
}
