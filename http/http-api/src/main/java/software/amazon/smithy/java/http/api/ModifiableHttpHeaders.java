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
     * <p>Any previously set values for this header are replaced as if {@link #removeHeader(String) and
     * {@link #addHeader(String, String)}} were called in sequence. To add a new value to a
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
     * <p>Any previously set values for this header are replaced as if {@link #removeHeader(String) and
     * {@link #addHeader(String, String)}} were called in sequence. To add new values to a
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
     * Puts the given {@code headers}, similarly to if {@link #setHeader(String, List)} were to be called for each
     * entry in the given map.
     *
     * @param headers Map of case-insensitive header names to their values.
     */
    default void setHeaders(Map<String, List<String>> headers) {
        for (var entry : headers.entrySet()) {
            setHeader(entry.getKey(), entry.getValue());
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

    @Override
    default ModifiableHttpHeaders toModifiable() {
        return this;
    }
}
