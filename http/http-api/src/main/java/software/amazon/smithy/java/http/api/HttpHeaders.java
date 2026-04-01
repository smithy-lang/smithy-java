/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Contains case-insensitive HTTP headers.
 *
 * <p>Implementations must always normalize header names to lowercase.
 */
public interface HttpHeaders {
    /**
     * Create an immutable HttpHeaders.
     *
     * @param headers Headers to set.
     * @return the created headers.
     */
    static HttpHeaders of(Map<String, List<String>> headers) {
        return ArrayUnmodifiableHttpHeaders.of(headers);
    }

    /**
     * Creates a mutable headers.
     *
     * @return the created headers.
     */
    static ModifiableHttpHeaders ofModifiable() {
        return new ArrayHttpHeaders();
    }

    /**
     * Creates a mutable headers with expected capacity.
     *
     * @param expectedPairs expected number of header name-value pairs
     * @return the created headers.
     */
    static ModifiableHttpHeaders ofModifiable(int expectedPairs) {
        return new ArrayHttpHeaders(expectedPairs);
    }

    /**
     * Create mutable HttpHeaders.
     *
     * @param headers Headers to set.
     * @return the created headers.
     */
    static ModifiableHttpHeaders ofModifiable(Map<String, List<String>> headers) {
        return of(headers).toModifiable();
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
     * Check if the given header is present.
     *
     * @param name Header to check.
     * @return true if the header is present.
     */
    default boolean hasHeader(HeaderName name) {
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
     * Get the first header value of a specific header by case-insensitive name.
     *
     * @param name Name of the header to get.
     * @return the matching header value, or null if not found.
     */
    default String firstValue(HeaderName name) {
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
     * Get the values of a specific header by name.
     *
     * @param name Name of the header to get the values of, case-insensitively.
     * @return the values of the header, or an empty list.
     */
    default List<String> allValues(HeaderName name) {
        return allValues(name.name());
    }

    /**
     * Get the content-type header, or null if not found.
     *
     * @return the content-type header or null.
     */
    default String contentType() {
        return firstValue(HeaderName.CONTENT_TYPE);
    }

    /**
     * Get the content-length header value, or null if not found.
     *
     * @return the parsed content-length or null.
     */
    default Long contentLength() {
        var value = firstValue(HeaderName.CONTENT_LENGTH);
        return value == null ? null : Long.parseLong(value);
    }

    /**
     * Get the number of header name-value pairs.
     *
     * <p>Note: if a header has multiple values, each value is counted separately.
     *
     * @return total number of header entries.
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
     * Iterates over each header name-value pair, invoking the consumer for every individual value.
     *
     * <p>This is the most efficient way to iterate headers. Unlike {@link #map()}, this method
     * avoids grouping values by name and does not allocate intermediate collections.
     *
     * @param consumer called with each header name and individual value.
     */
    default void forEachEntry(BiConsumer<String, String> consumer) {
        for (var entry : map().entrySet()) {
            for (var value : entry.getValue()) {
                consumer.accept(entry.getKey(), value);
            }
        }
    }

    /**
     * Iterates over each header name-value pair, invoking the consumer for every individual value.
     *
     * <p>This is the most efficient way to iterate headers. Unlike {@link #map()}, this method
     * avoids grouping values by name and does not allocate intermediate collections.
     *
     * @param consumer called with a context value and each header name and individual value.
     */
    default <C> void forEachEntry(C contextValue, HeaderWithValueConsumer<C> consumer) {
        for (var entry : map().entrySet()) {
            for (var value : entry.getValue()) {
                consumer.accept(contextValue, entry.getKey(), value);
            }
        }
    }

    interface HeaderWithValueConsumer<C> {
        void accept(C context, String key, String value);
    }

    /**
     * Get or create a modifiable version of the headers.
     *
     * @return the created modifiable headers.
     */
    default ModifiableHttpHeaders toModifiable() {
        if (this instanceof ModifiableHttpHeaders m) {
            return m;
        } else if (this instanceof ArrayUnmodifiableHttpHeaders) {
            return ((ArrayUnmodifiableHttpHeaders) this).toModifiable();
        } else {
            ModifiableHttpHeaders copy = new ArrayHttpHeaders(size());
            for (var e : map().entrySet()) {
                copy.addHeader(e.getKey(), e.getValue());
            }
            return copy;
        }
    }

    /**
     * Get an unmodifiable version of the headers.
     *
     * @return the unmodifiable headers.
     */
    default HttpHeaders toUnmodifiable() {
        return ArrayUnmodifiableHttpHeaders.of(this);
    }
}
