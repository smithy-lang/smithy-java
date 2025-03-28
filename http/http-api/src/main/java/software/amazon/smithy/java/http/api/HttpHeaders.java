/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.List;
import java.util.Map;

/**
 * Contains case-insensitive HTTP headers.
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
}
