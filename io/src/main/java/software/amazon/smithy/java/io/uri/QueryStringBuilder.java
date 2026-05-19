/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Used to build a query string from key value pair parameters.
 */
public final class QueryStringBuilder {

    private final StringBuilder builder = new StringBuilder();
    private final HashSet<String> httpQueryKeys = new HashSet<>();
    private boolean empty = true;

    /**
     * Clears the contents of the query string builder so it can be reused.
     */
    public void clear() {
        builder.setLength(0);
        empty = true;
        httpQueryKeys.clear();
    }

    /**
     * Add a query string parameter and value to the query string.
     *
     * <p>The given key and value are percent-encoded. If the value is already
     * percent-encoded, it will be double percent-encoded.
     *
     * @param key   Key of the parameter.
     * @param value Value of the parameter (or null, which appends {@code key=}).
     */
    public void add(String key, String value) {
        append(key, value);
        httpQueryKeys.add(key);
    }

    /**
     * Add multiple values for the same key.
     *
     * @param key    Key of the parameter.
     * @param values Values to add (each is added as a separate {@code key=value} pair).
     */
    public void add(String key, List<String> values) {
        for (String v : values) {
            add(key, v);
        }
    }

    /**
     * Add all entries from a map of {@code key -> [value, ...]} to the query string.
     *
     * @param values Map of keys to lists of values.
     */
    public void add(Map<String, List<String>> values) {
        for (var entry : values.entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Add a query string parameter and value from a {@code @httpQueryParams} member.
     *
     * <p>Skips the key if it was already added via {@link #add} so {@code @httpQuery}
     * members take precedence over {@code @httpQueryParams} entries with the same key.
     *
     * @param key   Key of the parameter.
     * @param value Value of the parameter (or null).
     */
    public void addForQueryParams(String key, String value) {
        if (!httpQueryKeys.contains(key)) {
            append(key, value);
        }
    }

    /**
     * Append a pre-encoded {@code "key=value"} entry to the query string and register its raw key with the
     * {@code @httpQuery} dedupe set so a subsequent {@link #addForQueryParams} with the same key will be skipped.
     *
     * @param rawKey            raw (unencoded) key, used only for the dedupe set.
     * @param encodedKeyEqValue {@code "encodedKey=encodedValue"} as a single ready-to-append string.
     */
    public void addPreEncoded(String rawKey, String encodedKeyEqValue) {
        if (!empty) {
            builder.append('&');
        } else {
            empty = false;
        }
        builder.append(encodedKeyEqValue);
        httpQueryKeys.add(rawKey);
    }

    private void append(String key, String value) {
        if (!empty) {
            builder.append('&');
        } else {
            empty = false;
        }

        URLEncoding.encodeUnreserved(key, builder, false);
        builder.append('=');
        if (value != null) {
            URLEncoding.encodeUnreserved(value, builder, false);
        }
    }

    /**
     * Check if the query string is empty.
     *
     * @return Returns true if no parameters have been added.
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * Get the query string as a percent-encoded query string (e.g., {@code "foo=bar&baz=test%20test"}).
     *
     * @return Returns the percent-encoded query string.
     */
    @Override
    public String toString() {
        return builder.toString();
    }

    /**
     * Append the query string directly to a string builder.
     *
     * @param sink Where to write.
     */
    public void write(StringBuilder sink) {
        sink.append(builder);
    }
}
