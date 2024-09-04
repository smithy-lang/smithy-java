/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.uri;

import java.util.*;
import java.util.regex.Pattern;

/**
 * A parser for query string values as expected by Smithy.
 */
public final class QueryStringParser {

    private static final Pattern REGEX_STRING_OF_SLASHES = Pattern.compile("/+");

    private QueryStringParser() {}

    /**
     * Parse a query string. Empty values are represented by "" and can come from either "key=" or standalone "key"
     * values in the string.
     *
     * @param rawQueryString the raw, encoded query string
     * @return a map of key to list of values
     */
    public static Map<String, List<String>> parse(String rawQueryString) {
        if (rawQueryString == null || rawQueryString.isEmpty()) {
            return Collections.emptyMap();
        }
        var returnMap = new HashMap<String, List<String>>();
        for (String segment : rawQueryString.split("&")) {
            String[] keyValue = segment.split("=", 2);
            var values = returnMap.computeIfAbsent(keyValue[0], k -> new ArrayList<>());
            if (keyValue.length == 1) {
                values.add("");
            } else {
                values.add(URLEncoding.urlDecode(keyValue[1]));
            }
        }
        return returnMap;
    }

    public interface Visitor {
        boolean onParameter(String key, String value);
    }

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

    public static Map<String, List<String>> toMapOfLists(String queryString) {
        final Map<String, List<String>> map = new TreeMap<>();

        parse(queryString, (key, value) -> {
            String strValue = null;
            if (value != null) {
                strValue = value;
            }

            List<String> values = map.computeIfAbsent(key, k -> new ArrayList<>());
            values.add(strValue);

            return true;
        });

        return map;
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

    public static String getPath(String uri) {
        return getPath(uri, false);
    }

    public static String getRawPath(String uri) {
        Objects.requireNonNull(uri);

        int i = 0;
        // Remove leading slashes
        while (i < uri.length() - 1 && uri.charAt(i) == '/') {
            i++;
        }
        int j = uri.indexOf('?');
        if (j < 0) {
            j = uri.length();
        }
        // Remove trailing slashes
        while (j > i && uri.charAt(j - 1) == '/') {
            j--;
        }
        return uri.substring(i, j);
    }

    public static String getPath(String uri, boolean allowEmptyPathSegments) {
        uri = getRawPath(uri);
        if (allowEmptyPathSegments) {
            return uri;
        }
        return REGEX_STRING_OF_SLASHES.matcher(uri).replaceAll("/");
    }

    public static String getQuery(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException();
        }

        int questionMark = uri.indexOf('?');
        if (questionMark < 0) {
            return null;
        } else {
            return uri.substring(questionMark + 1);
        }
    }
}
