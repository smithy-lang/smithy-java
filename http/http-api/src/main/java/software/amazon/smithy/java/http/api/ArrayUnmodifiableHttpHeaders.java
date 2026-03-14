/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Immutable array-backed HTTP headers implementation.
 *
 * <p>Uses the same flat array storage as {@link ArrayHttpHeaders} for efficient
 * lookups with pointer comparison for interned header names.
 *
 * <p>Instances are created by copying from modifiable headers or directly from arrays.
 */
final class ArrayUnmodifiableHttpHeaders implements HttpHeaders {

    static final HttpHeaders EMPTY = new ArrayUnmodifiableHttpHeaders(new String[0], 0);

    private final String[] array;
    private final int size; // Number of name-value pairs

    // Lazily computed map view
    private volatile Map<String, List<String>> mapView;

    private ArrayUnmodifiableHttpHeaders(String[] array, int size) {
        this.array = array;
        this.size = size;
    }

    /**
     * Create from an ArrayHttpHeaders by copying its array.
     */
    static HttpHeaders of(ArrayHttpHeaders headers) {
        int entryCount = headers.entryCount();
        if (entryCount == 0) {
            return EMPTY;
        }
        String[] copy = new String[entryCount * 2];
        System.arraycopy(headers.rawArray(), 0, copy, 0, entryCount * 2);
        return new ArrayUnmodifiableHttpHeaders(copy, entryCount);
    }

    /**
     * Create from any HttpHeaders.
     */
    static HttpHeaders of(HttpHeaders headers) {
        if (headers instanceof ArrayUnmodifiableHttpHeaders) {
            return headers;
        }
        if (headers instanceof ArrayHttpHeaders ah) {
            return of(ah);
        }
        if (headers.isEmpty()) {
            return EMPTY;
        }

        // Convert from map-based headers
        Map<String, List<String>> map = headers.map();
        int totalPairs = 0;
        for (List<String> values : map.values()) {
            totalPairs += values.size();
        }

        String[] arr = new String[totalPairs * 2];
        int idx = 0;
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            String name = HeaderNames.canonicalize(e.getKey());
            for (String value : e.getValue()) {
                arr[idx++] = name;
                arr[idx++] = value;
            }
        }
        return new ArrayUnmodifiableHttpHeaders(arr, totalPairs);
    }

    /**
     * Create from a Map of headers.
     *
     * <p>Headers with the same normalized name (case-insensitive) are merged.
     */
    static HttpHeaders of(Map<String, List<String>> input) {
        if (input.isEmpty()) {
            return EMPTY;
        }

        // Use ArrayHttpHeaders to handle merging of same-named headers
        ArrayHttpHeaders builder = new ArrayHttpHeaders(input.size() * 2);
        for (Map.Entry<String, List<String>> e : input.entrySet()) {
            String name = HeaderNames.canonicalize(e.getKey());
            for (String value : e.getValue()) {
                builder.addHeaderInterned(name, value);
            }
        }
        return of(builder);
    }

    @Override
    public String firstValue(String name) {
        String key = HeaderNames.canonicalize(name);
        // Fast path: pointer comparison
        for (int i = 0; i < size * 2; i += 2) {
            if (array[i] == key) {
                return array[i + 1];
            }
        }
        // Slow path: equals()
        for (int i = 0; i < size * 2; i += 2) {
            if (array[i].equals(key)) {
                return array[i + 1];
            }
        }
        return null;
    }

    @Override
    public List<String> allValues(String name) {
        String key = HeaderNames.canonicalize(name);
        List<String> result = null;

        // Single pass: try pointer comparison first, fall back to equals
        for (int i = 0; i < size * 2; i += 2) {
            String headerName = array[i];
            if (headerName == key || headerName.equals(key)) {
                if (result == null) {
                    result = new ArrayList<>(2);
                }
                result.add(array[i + 1]);
            }
        }

        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    @Override
    public boolean hasHeader(String name) {
        String key = HeaderNames.canonicalize(name);
        for (int i = 0; i < size * 2; i += 2) {
            String headerName = array[i];
            if (headerName == key || headerName.equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        // Return unique header names count
        if (size == 0) {
            return 0;
        }
        int count = 0;
        outer: for (int i = 0; i < size * 2; i += 2) {
            String name = array[i];
            for (int j = 0; j < i; j += 2) {
                if (array[j] == name || array[j].equals(name)) {
                    continue outer;
                }
            }
            count++;
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Map<String, List<String>> map() {
        Map<String, List<String>> m = mapView;
        if (m == null) {
            m = buildMap();
            mapView = m;
        }
        return m;
    }

    private Map<String, List<String>> buildMap() {
        if (size == 0) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < size * 2; i += 2) {
            String name = array[i];
            String value = array[i + 1];
            result.computeIfAbsent(name, k -> new ArrayList<>(2)).add(value);
        }
        // Make all lists unmodifiable
        for (Map.Entry<String, List<String>> e : result.entrySet()) {
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return new GroupingIterator();
    }

    @Override
    public ModifiableHttpHeaders toModifiable() {
        ArrayHttpHeaders copy = new ArrayHttpHeaders(size);
        for (int i = 0; i < size * 2; i += 2) {
            copy.addHeaderInterned(array[i], array[i + 1]);
        }
        return copy;
    }

    @Override
    public HttpHeaders toUnmodifiable() {
        return this;
    }

    /**
     * Iterator that groups values by header name.
     */
    private class GroupingIterator implements Iterator<Map.Entry<String, List<String>>> {
        private int nextIndex = 0;
        private final boolean[] visited;

        GroupingIterator() {
            this.visited = new boolean[size];
        }

        @Override
        public boolean hasNext() {
            while (nextIndex < size && visited[nextIndex]) {
                nextIndex++;
            }
            return nextIndex < size;
        }

        @Override
        public Map.Entry<String, List<String>> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            String name = array[nextIndex * 2];
            List<String> values = new ArrayList<>(2);

            // Collect all values for this name
            for (int i = nextIndex; i < size; i++) {
                if (!visited[i] && (array[i * 2] == name || array[i * 2].equals(name))) {
                    values.add(array[i * 2 + 1]);
                    visited[i] = true;
                }
            }

            nextIndex++;
            return new AbstractMap.SimpleImmutableEntry<>(name, Collections.unmodifiableList(values));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpHeaders other)) {
            return false;
        }
        return map().equals(other.map());
    }

    @Override
    public int hashCode() {
        return map().hashCode();
    }

    @Override
    public String toString() {
        return "ArrayUnmodifiableHttpHeaders{" + map() + '}';
    }
}
