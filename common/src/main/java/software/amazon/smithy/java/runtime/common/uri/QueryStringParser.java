/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.common.uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
