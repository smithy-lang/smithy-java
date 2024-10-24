/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SimpleUnmodifiableHttpHeaders implements HttpHeaders {

    static final HttpHeaders EMPTY = new SimpleUnmodifiableHttpHeaders(Collections.emptyMap());

    private final Map<String, List<String>> headers;

    SimpleUnmodifiableHttpHeaders(Map<String, List<String>> input) {
        // Ensure map keys are normalized to use lower-case header names.
        this.headers = new HashMap<>(input.size());
        for (var entry : input.entrySet()) {
            var key = entry.getKey().trim().toLowerCase(Locale.ENGLISH);
            headers.computeIfAbsent(key, k -> new ArrayList<>()).addAll(trimValues(entry.getValue()));
        }
        // Make the value immutable.
        for (var entry : headers.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
    }

    private static List<String> trimValues(List<String> source) {
        List<String> trimmedValues = new ArrayList<>(source.size());
        for (var value : source) {
            trimmedValues.add(value.trim());
        }
        return trimmedValues;
    }

    @Override
    public List<String> allValues(String name) {
        return headers.getOrDefault(name.toLowerCase(Locale.ENGLISH), Collections.emptyList());
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return headers.entrySet().iterator();
    }

    @Override
    public Map<String, List<String>> map() {
        return Collections.unmodifiableMap(headers);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof HttpHeaders)) {
            return false;
        }

        // For unmodifiable headers, we treat mutable implementations the same.
        var other = (HttpHeaders) obj;
        return headers.equals(other.map());
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }
}
