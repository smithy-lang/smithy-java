/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

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
        this(input, true);
    }

    SimpleUnmodifiableHttpHeaders(Map<String, List<String>> input, boolean copyHeaders) {
        if (!copyHeaders) {
            this.headers = input;
        } else {
            // Ensure map keys are normalized to use lower-case header names.
            this.headers = new HashMap<>(input.size());
            for (var entry : input.entrySet()) {
                var key = entry.getKey().trim().toLowerCase(Locale.ENGLISH);
                headers.computeIfAbsent(key, k -> new ArrayList<>()).addAll(copyAndTrimValues(entry.getValue()));
            }
            // Make the value immutable.
            for (var entry : headers.entrySet()) {
                entry.setValue(Collections.unmodifiableList(entry.getValue()));
            }
        }
    }

    private static List<String> copyAndTrimValues(List<String> source) {
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
    public ModifiableHttpHeaders toModifiable() {
        var mod = new SimpleModifiableHttpHeaders();
        Map<String, List<String>> copy = new HashMap<>(headers.size());
        for (var entry : headers.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        mod.putHeaders(copy);
        return mod;
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

    // Note: all of these methods must:
    // 1. Lowercase header names and trim them
    // 2. Copy header value lists and trim each value.
    //
    // Because of this, when creating HttpHeaders from the returned maps, there's no need to copy the map.

    static Map<String, List<String>> addHeaders(
        HttpHeaders original,
        Map<String, List<String>> mutatedHeaders,
        HttpHeaders from
    ) {
        if (mutatedHeaders == null) {
            if (original.isEmpty()) {
                return copyHeaders(from.map());
            }
            mutatedHeaders = copyHeaders(original.map());
        }
        for (var entry : from.map().entrySet()) {
            mutatedHeaders.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .addAll(copyAndTrimValues(entry.getValue()));
        }
        return mutatedHeaders;
    }

    static Map<String, List<String>> addHeaders(
        HttpHeaders original,
        Map<String, List<String>> mutatedHeaders,
        String... fieldAndValues
    ) {
        if (mutatedHeaders == null) {
            mutatedHeaders = SimpleUnmodifiableHttpHeaders.copyHeaders(original.map());
        }
        if (fieldAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Uneven number of header keys and fields: " + fieldAndValues.length);
        }
        for (int i = 0; i < fieldAndValues.length - 1; i += 2) {
            String field = fieldAndValues[i].toLowerCase(Locale.ENGLISH).trim();
            String value = fieldAndValues[i + 1].trim();
            mutatedHeaders.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
        }
        return mutatedHeaders;
    }

    static Map<String, List<String>> copyHeaders(Map<String, List<String>> from) {
        Map<String, List<String>> into = new HashMap<>(from.size());
        for (var entry : from.entrySet()) {
            into.put(entry.getKey().toLowerCase(Locale.ENGLISH).trim(), copyAndTrimValues(entry.getValue()));
        }
        return into;
    }

    static Map<String, List<String>> replaceHeaders(
        HttpHeaders original,
        Map<String, List<String>> mutated,
        Map<String, List<String>> replace
    ) {
        if (mutated == null) {
            mutated = SimpleUnmodifiableHttpHeaders.copyHeaders(original.map());
        }
        for (Map.Entry<String, List<String>> entry : replace.entrySet()) {
            mutated.put(entry.getKey().toLowerCase(Locale.ENGLISH).trim(), copyAndTrimValues(entry.getValue()));
        }
        return mutated;
    }
}
