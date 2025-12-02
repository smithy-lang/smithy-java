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

    // A faster constructor that knows the keys are already grouped and normalized.
    SimpleUnmodifiableHttpHeaders(HttpHeaders headers) {
        var map = headers.map();
        this.headers = new HashMap<>(map.size());
        for (var e : map.entrySet()) {
            this.headers.put(e.getKey(), List.copyOf(e.getValue()));
        }
    }

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
                var key = HttpHeaders.normalizeHeaderName(entry.getKey());
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

    static HttpHeaders of(HttpHeaders headers) {
        return headers instanceof SimpleUnmodifiableHttpHeaders ? headers : new SimpleUnmodifiableHttpHeaders(headers);
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
}
