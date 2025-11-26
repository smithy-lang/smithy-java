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
        } else if (input.isEmpty()) {
            this.headers = Collections.emptyMap();
        } else {
            // Single pass to normalize, trim, and make immutable in one go
            Map<String, List<String>> result = HashMap.newHashMap(input.size());
            for (var entry : input.entrySet()) {
                var key = normalizeKey(entry.getKey());
                var values = entry.getValue();
                var existing = result.get(key);
                if (existing == null) {
                    existing = new ArrayList<>();
                    result.put(key, existing);
                }
                copyAndTrimValuesInto(values, existing);
            }
            // make immutable lists
            for (var e : result.entrySet()) {
                e.setValue(Collections.unmodifiableList(e.getValue()));
            }
            this.headers = result;
        }
    }

    private static String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ENGLISH);
    }

    private static List<String> copyAndTrimValuesMutable(List<String> source) {
        int size = source.size();
        if (size == 0) {
            return new ArrayList<>(4);
        }
        var result = new ArrayList<String>(size);
        copyAndTrimValuesInto(source, result);
        return result;
    }

    private static void copyAndTrimValuesInto(List<String> source, List<String> dest) {
        for (String s : source) {
            dest.add(s.trim());
        }
    }

    @Override
    public List<String> allValues(String name) {
        var values = headers.get(name.toLowerCase(Locale.ENGLISH));
        return values != null ? values : List.of();
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
        Map<String, List<String>> copy = HashMap.newHashMap(headers.size());
        for (var entry : headers.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        mod.addHeaders(copy);
        return mod;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof HttpHeaders other)) {
            return false;
        }
        return headers.equals(other.map());
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }

    @Override
    public String toString() {
        return "SimpleUnmodifiableHttpHeaders{" + headers + '}';
    }

    // Note: all of these methods must:
    // 1. Lowercase header names and trim them
    // 2. Copy header value lists and trim each value.
    //
    // Because of this, when creating HttpHeaders from the returned maps, there's no need to copy the map.

    static Map<String, List<String>> addHeaders(
            HttpHeaders original,
            Map<String, List<String>> mutatedHeaders,
            Map<String, List<String>> from
    ) {
        if (mutatedHeaders == null) {
            if (original.isEmpty()) {
                return copyHeaders(from);
            }
            mutatedHeaders = copyHeaders(original.map());
        }
        for (var entry : from.entrySet()) {
            var key = normalizeKey(entry.getKey());
            var list = mutatedHeaders.get(key);
            if (list == null) {
                list = copyAndTrimValuesMutable(entry.getValue());
                mutatedHeaders.put(key, list);
            } else {
                copyAndTrimValuesInto(entry.getValue(), list);
            }
        }
        return mutatedHeaders;
    }

    static Map<String, List<String>> addHeader(
            HttpHeaders original,
            Map<String, List<String>> mutatedHeaders,
            String field,
            String value
    ) {
        if (mutatedHeaders == null) {
            mutatedHeaders = copyHeaders(original.map());
        }
        field = normalizeKey(field);
        value = value.trim();
        var list = mutatedHeaders.get(field);
        if (list == null) {
            list = new ArrayList<>(4);
            mutatedHeaders.put(field, list);
        }
        list.add(value);
        return mutatedHeaders;
    }

    static Map<String, List<String>> copyHeaders(Map<String, List<String>> from) {
        if (from.isEmpty()) {
            return new HashMap<>(8);
        }
        Map<String, List<String>> into = HashMap.newHashMap(from.size());
        for (var entry : from.entrySet()) {
            into.put(normalizeKey(entry.getKey()), copyAndTrimValuesMutable(entry.getValue()));
        }
        return into;
    }

    static Map<String, List<String>> replaceHeaders(
            HttpHeaders original,
            Map<String, List<String>> mutated,
            Map<String, List<String>> replace
    ) {
        if (mutated == null) {
            mutated = copyHeaders(original.map());
        }
        for (var entry : replace.entrySet()) {
            mutated.put(normalizeKey(entry.getKey()), copyAndTrimValuesMutable(entry.getValue()));
        }
        return mutated;
    }
}
