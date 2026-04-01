/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A parser for query string values as expected by Smithy.
 */
public final class QueryStringParser {

    private QueryStringParser() {}

    /**
     * Parse a query string. Empty values are represented by "" and can come from either "key=" or standalone "key"
     * values in the string.
     *
     * @param rawQueryString the raw, encoded query string
     * @return a map of key to list of values
     */
    public static Map<String, List<String>> parse(String rawQueryString) {
        return rawQueryString == null || rawQueryString.isEmpty()
                ? Collections.emptyMap()
                : parseInto(rawQueryString, new HashMap<>());
    }

    /**
     * Parses a query string into a sorted map of key to list of values.
     *
     * @param rawQueryString the raw, encoded query string (or null).
     * @return a sorted map of decoded keys to lists of decoded values.
     */
    public static Map<String, List<String>> parseSorted(String rawQueryString) {
        return rawQueryString == null || rawQueryString.isEmpty()
                ? Collections.emptyMap()
                : parseInto(rawQueryString, new TreeMap<>());
    }

    private static Map<String, List<String>> parseInto(String source, Map<String, List<String>> sink) {
        parse(source, (key, value) -> {
            sink.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            return true;
        });
        return sink;
    }

    /**
     * Visitor that receives parsed query string parameters.
     */
    public interface Visitor {
        /**
         * Called for each parsed query string parameter.
         *
         * @param key   the percent-decoded parameter key.
         * @param value the percent-decoded parameter value, or "" if no value was present.
         * @return true to continue parsing, false to stop early.
         */
        boolean onParameter(String key, String value);
    }

    /**
     * Parses a query string, invoking the visitor for each parameter. Supports both {@code &} and {@code ;}
     * as delimiters. Keys and values are percent-decoded.
     *
     * @param queryString the raw, encoded query string (or null).
     * @param visitor     visitor to receive each parameter.
     * @return true if all parameters were visited, false if the visitor stopped early.
     */
    public static boolean parse(String queryString, Visitor visitor) {
        Objects.requireNonNull(visitor);

        if (queryString == null) {
            return true;
        }

        int start = 0;

        for (int i = 0; i < queryString.length(); i++) {
            char c = queryString.charAt(i);

            if (c == '&' || c == ';') {
                if (!handleParam(queryString, start, i, visitor)) {
                    return false;
                }

                start = i + 1;
            }
        }

        return handleParam(queryString, start, queryString.length(), visitor);
    }

    private static boolean handleParam(String queryString, int start, int end, Visitor visitor) {
        String param = queryString.substring(start, end);

        if (param.isEmpty()) {
            return true;
        }

        String key = URLEncoding.urlDecode(getKey(param));
        String value = getValue(param);
        value = URLEncoding.urlDecode(value);

        return visitor.onParameter(key, value);
    }

    private static String getKey(String keyValuePair) {
        int separator = getKeyValueSeparator(keyValuePair);
        if (separator == -1) {
            return keyValuePair;
        } else {
            return keyValuePair.substring(0, separator);
        }
    }

    private static String getValue(String keyValuePair) {
        int separator = getKeyValueSeparator(keyValuePair);
        if (separator == -1) {
            return "";
        } else {
            return keyValuePair.substring(separator + 1);
        }
    }

    private static int getKeyValueSeparator(String keyValuePair) {
        for (int i = 0; i < keyValuePair.length(); i++) {
            char c = keyValuePair.charAt(i);
            if (c == '=') {
                return i;
            }
        }
        return -1;
    }
}
