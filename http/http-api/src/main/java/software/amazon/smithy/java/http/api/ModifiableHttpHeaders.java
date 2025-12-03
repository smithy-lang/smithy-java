/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.List;
import java.util.Map;

/**
 * A modifiable version of {@link HttpHeaders}.
 *
 * <p><b>Thread Safety:</b> Implementations are <b>not</b> guaranteed to be thread-safe.
 * If multiple threads access an instance concurrently, and at least one thread modifies
 * the headers, external synchronization is required.
 */
public interface ModifiableHttpHeaders extends HttpHeaders {
    /**
     * Add a header by name with the given value.
     *
     * <p>Any previously set values for this header are retained. This value is added
     * to a list of values for this header name. To overwrite an existing value, use
     * {@link #setHeader(String, String)}.
     *
     * @param name Case-insensitive name of the header to set.
     * @param value Value to set.
     */
    void addHeader(String name, String value);

    /**
     * Add a header by name with the given values.
     *
     * <p>Any previously set values for this header are retained. This value is added
     * to a list of values for this header name. To overwrite an existing value, use
     * {@link #setHeader(String, List)}.
     *
     * @param name Case-insensitive name of the header to set.
     * @param values Values to set.
     */
    void addHeader(String name, List<String> values);

    /**
     * Adds the given {@code headers}, similarly to if {@link #addHeader(String, List)} were to be called for each
     * entry in the given map.
     *
     * @param headers Map of case-insensitive header names to their values.
     */
    default void addHeaders(Map<String, List<String>> headers) {
        for (var entry : headers.entrySet()) {
            addHeader(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Sets a header to the given value, overwriting old values if present.
     *
     * <p>Any previously set values for this header are replaced as if {@link #removeHeader(String)} and
     * {@link #addHeader(String, String)} were called in sequence. To add a new value to a
     * list of values, use {@link #addHeader(String, String)}.
     *
     * @param name Case-insensitive name of the header to set.
     * @param value Value to set.
     */
    default void setHeader(String name, String value) {
        removeHeader(name);
        addHeader(name, value);
    }

    /**
     * Sets a header to the given value, overwriting old values if present.
     *
     * <p>Any previously set values for this header are replaced as if {@link #removeHeader(String)} and
     * {@link #addHeader(String, String)} were called in sequence. To add new values to a
     * list of values, use {@link #addHeader(String, List)}.
     *
     * @param name Case-insensitive name of the header to set.
     * @param values Values to set.
     */
    default void setHeader(String name, List<String> values) {
        removeHeader(name);
        addHeader(name, values);
    }

    /**
     * Set a value, but only if it isn't set already.
     *
     * @param name Name of the value to set.
     * @param values Value to set.
     * @return the previous values associated with the name, or null if there was no mapping.
     */
    default List<String> setHeaderIfAbsent(String name, List<String> values) {
        var current = allValues(name);
        if (current != null) {
            return current;
        } else {
            setHeader(name, values);
            return null;
        }
    }

    /**
     * Set a value, but only if it isn't set already.
     *
     * @param name Name of the value to set.
     * @param value Value to set.
     * @return the previous values associated with the name, or null if there was no mapping.
     */
    default List<String> setHeaderIfAbsent(String name, String value) {
        var current = allValues(name);
        if (current != null) {
            return current;
        } else {
            setHeader(name, List.of(value));
            return null;
        }
    }

    /**
     * Puts the given {@code headers}, similarly to if {@link #setHeader(String, List)} were to be called for each
     * entry in the given map (leaving other headers in place).
     *
     * @param headers Map of case-insensitive header names to their values.
     */
    default void setHeaders(Map<String, List<String>> headers) {
        for (var entry : headers.entrySet()) {
            setHeader(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Puts the given {@code headers}, similarly to if {@link #setHeader(String, List)} were to be called for each
     * entry in the given HttpHeaders.
     *
     * @param headers HTTP headers to copy from.
     */
    default void setHeaders(HttpHeaders headers) {
        // Note: the default implementation is overridden in SimpleModifiableHttpHeaders.
        for (var e : headers.map().entrySet()) {
            setHeader(e.getKey(), e.getValue());
        }
    }

    /**
     * Remove a header and its values by name.
     *
     * @param name Case-insensitive name of the header to remove.
     */
    void removeHeader(String name);

    /**
     * Removes all headers.
     */
    void clear();

    /**
     * Create a copy of the modifiable headers.
     *
     * @return a copy of the modifiable headers.
     */
    default ModifiableHttpHeaders copy() {
        var copy = new SimpleModifiableHttpHeaders();
        copy.setHeaders(this);
        return copy;
    }
}
