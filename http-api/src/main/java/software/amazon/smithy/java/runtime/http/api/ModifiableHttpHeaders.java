/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.List;
import java.util.Map;

/**
 * A modifiable version of {@link HttpHeaders}.
 */
public interface ModifiableHttpHeaders extends HttpHeaders {
    /**
     * Replace a header by name with the given value.
     *
     * <p>Any previously set values for this header are replaced.
     *
     * @param name Case-insensitive name of the header to set.
     * @param value Value to set.
     */
    void putHeader(String name, String value);

    /**
     * Replace a header by name with the given values.
     *
     * <p>Any previously set values for this header are replaced.
     *
     * @param name Case-insensitive name of the header to set.
     * @param values Values to set.
     */
    void putHeader(String name, List<String> values);

    /**
     * Put the given {@code headers}, similarly to if {@link #putHeader(String, List)} were to be called for each
     * entry in the given map.
     *
     * @param headers Map of case-insensitive header names to their values.
     */
    default void putHeaders(Map<String, List<String>> headers) {
        for (var entry : headers.entrySet()) {
            putHeader(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Remove a header and its values by name.
     *
     * @param name Case-insensitive name of the header to remove.
     */
    void removeHeader(String name);
}
