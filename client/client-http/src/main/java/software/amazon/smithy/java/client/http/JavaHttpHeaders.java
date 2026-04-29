/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;

final class JavaHttpHeaders implements HttpHeaders {
    private final java.net.http.HttpHeaders headers;
    private volatile Map<String, List<String>> materialized;
    private volatile int size = -1;

    JavaHttpHeaders(java.net.http.HttpHeaders headers) {
        this.headers = headers;
    }

    @Override
    public List<String> allValues(String name) {
        return allValuesCanonical(HeaderName.canonicalize(name));
    }

    @Override
    public List<String> allValues(HeaderName name) {
        return allValuesCanonical(name.name());
    }

    @Override
    public int size() {
        int result = size;
        if (result >= 0) {
            return result;
        }

        result = 0;
        for (List<String> values : headers.map().values()) {
            result += values.size();
        }
        size = result;
        return result;
    }

    @Override
    public Map<String, List<String>> map() {
        Map<String, List<String>> result = materialized;
        if (result != null) {
            return result;
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (var entry : headers.map().entrySet()) {
            String canonical = HeaderName.canonicalize(entry.getKey());
            if (canonical.equals(":status")) {
                continue;
            }
            List<String> values = grouped.get(canonical);
            if (values == null) {
                values = new ArrayList<>(entry.getValue().size());
                grouped.put(canonical, values);
            }
            values.addAll(entry.getValue());
        }
        materialized = grouped;
        return grouped;
    }

    private List<String> allValuesCanonical(String canonical) {
        Map<String, List<String>> cached = materialized;
        if (cached != null) {
            return cached.getOrDefault(canonical, Collections.emptyList());
        }

        if (canonical.equals(":status")) {
            return Collections.emptyList();
        }

        List<String> values = null;
        for (var entry : headers.map().entrySet()) {
            if (HeaderName.canonicalize(entry.getKey()).equals(canonical)) {
                if (values == null) {
                    values = new ArrayList<>(entry.getValue().size());
                }
                values.addAll(entry.getValue());
            }
        }
        return values == null ? Collections.emptyList() : values;
    }
}
