/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Used to build a query string from key value pair parameters.
 */
public final class QueryStringBuilder {

    private final List<String> values = new ArrayList<>();

    /**
     * Clears the contents of the query string builder.
     */
    public void clear() {
        values.clear();
    }

    /**
     * Add a query string parameter and value to the query string.
     * <p>
     * The given key and value will be percent-encoded. If the value is already percent-encoded, it will be
     * double percent-encoded.
     *
     * @param key   Key of the parameter.
     * @param value Value of the parameter (or null).
     */
    public void add(String key, String value) {
        values.add(key);
        values.add(value);
    }

    /**
     * Add a query string parameter and values to the query string.
     * <p>
     * The given key and values will be percent-encoded. If the values are already percent-encoded, it will be
     * double percent-encoded.
     *
     * @param key   Key of the parameter.
     * @param values List of values
     */
    public void add(String key, List<String> values) {
        for (String value : values) {
            add(key, value);
        }
    }

    /**
     * Add a query string parameter and values to the query string.
     * <p>
     * The given key and values will be percent-encoded. If the values are already percent-encoded, it will be
     * double percent-encoded.
     *
     * @param values List of values
     */
    public void add(Map<String, List<String>> values) {
        for (var entry : values.entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Check if the query string is empty.
     *
     * @return Returns true if empty.
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Get the query string as a percent-encoded query string (e.g., {@code "foo=bar&baz=test%20test"}).
     *
     * @return Returns the percent-encoded query string.
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        write(result);
        return result.toString();
    }

    /**
     * Write the query string directly to a string builder.
     *
     * @param sink Where to write.
     */
    public void write(StringBuilder sink) {
        for (int i = 0; i < values.size(); i += 2) {
            if (i > 0) {
                sink.append('&');
            }
            encode(values.get(i), sink);
            sink.append('=');
            encode(values.get(i + 1), sink);
        }
    }

    private void encode(String raw, StringBuilder builder) {
        URLEncoding.encodeUnreserved(raw, builder, false);
    }
}
