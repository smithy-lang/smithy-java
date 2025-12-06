/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * High-performance array-backed HTTP headers implementation.
 *
 * <p>Storage layout uses a flat String array with name-value pairs at alternating indices:
 * <pre>
 * array[0] = name1 (interned)
 * array[1] = value1
 * array[2] = name2 (interned)
 * array[3] = value2
 * ...
 * </pre>
 *
 * <p>Header names are interned via {@link HeaderNames}, enabling O(1) pointer
 * comparison ({@code ==}) for known headers. Unknown headers fall back to {@code equals()}.
 *
 * <p><b>Thread Safety:</b> This class is <b>not</b> thread-safe.
 */
final class ArrayHttpHeaders implements ModifiableHttpHeaders {

    private static final int INITIAL_CAPACITY = 32; // 16 name-value pairs

    private String[] array;
    private int size; // Number of entries (size*2 = array slots used)

    ArrayHttpHeaders() {
        this.array = new String[INITIAL_CAPACITY];
        this.size = 0;
    }

    ArrayHttpHeaders(int expectedPairs) {
        this.array = new String[Math.max(expectedPairs * 2, 8)];
        this.size = 0;
    }

    /**
     * Add a header with pre-interned name.
     *
     * <p>Fast path for parsers that already have an interned name from
     * {@link HeaderNames#canonicalize(String)} or HPACK static table.
     *
     * @param internedName pre-interned header name (must be from HeaderNameRegistry)
     * @param value header value
     */
    void addHeaderInterned(String internedName, String value) {
        ensureCapacity();
        int idx = size * 2;
        array[idx] = internedName;
        array[idx + 1] = HeaderUtils.normalizeValue(value);
        size++;
    }

    /**
     * Add a header directly from bytes (zero-copy for known headers).
     *
     * @param nameBytes byte buffer containing header name
     * @param nameOffset start offset in buffer
     * @param nameLength length of header name
     * @param value header value
     */
    void addHeader(byte[] nameBytes, int nameOffset, int nameLength, String value) {
        String name = HeaderNames.canonicalize(nameBytes, nameOffset, nameLength);
        addHeaderInterned(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        String key = HeaderNames.canonicalize(name);
        addHeaderInterned(key, value);
    }

    @Override
    public void addHeader(String name, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        String key = HeaderNames.canonicalize(name);
        for (String v : values) {
            addHeaderInterned(key, v);
        }
    }

    @Override
    public void setHeader(String name, String value) {
        String key = HeaderNames.canonicalize(name);
        removeByKey(key);
        addHeaderInterned(key, value);
    }

    @Override
    public void setHeader(String name, List<String> values) {
        String key = HeaderNames.canonicalize(name);
        removeByKey(key);
        for (String v : values) {
            addHeaderInterned(key, v);
        }
    }

    @Override
    public void removeHeader(String name) {
        String key = HeaderNames.canonicalize(name);
        removeByKey(key);
    }

    private void removeByKey(String key) {
        // Compact in-place: copy non-matching entries over matching ones
        int write = 0;
        for (int read = 0; read < size; read++) {
            int idx = read * 2;
            String n = array[idx];
            // Fast path: pointer compare for interned names
            if (n != key && !n.equals(key)) {
                if (write != read) {
                    array[write * 2] = n;
                    array[write * 2 + 1] = array[idx + 1];
                }
                write++;
            }
        }
        // Clear removed slots to avoid memory leaks
        for (int i = write * 2; i < size * 2; i++) {
            array[i] = null;
        }
        size = write;
    }

    @Override
    public void clear() {
        Arrays.fill(array, 0, size * 2, null);
        size = 0;
    }

    @Override
    public String firstValue(String name) {
        String key = HeaderNames.canonicalize(name);
        // Fast path: pointer comparison for interned names
        for (int i = 0; i < size * 2; i += 2) {
            if (array[i] == key) {
                return array[i + 1];
            }
        }
        // Slow path: equals() for custom/unknown headers
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

        return result != null ? result : Collections.emptyList();
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
        // Return unique header names count, not total entries
        // This maintains compatibility with the Map-based implementation
        if (size == 0) {
            return 0;
        }
        int count = 0;
        outer: for (int i = 0; i < size * 2; i += 2) {
            String name = array[i];
            // Check if we've seen this name before
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
        if (size == 0) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < size * 2; i += 2) {
            String name = array[i];
            String value = array[i + 1];
            result.computeIfAbsent(name, k -> new ArrayList<>(2)).add(value);
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return new GroupingIterator();
    }

    @Override
    public ModifiableHttpHeaders copy() {
        ArrayHttpHeaders copy = new ArrayHttpHeaders(size);
        System.arraycopy(array, 0, copy.array, 0, size * 2);
        copy.size = size;
        return copy;
    }

    @Override
    public void setHeaders(HttpHeaders headers) {
        if (headers instanceof ArrayHttpHeaders ah) {
            // Fast path: direct array copy
            ensureCapacity(ah.size);
            for (int i = 0; i < ah.size * 2; i += 2) {
                String name = ah.array[i];
                removeByKey(name);
            }
            for (int i = 0; i < ah.size * 2; i += 2) {
                ensureCapacity();
                int idx = size * 2;
                array[idx] = ah.array[i];
                array[idx + 1] = ah.array[i + 1];
                size++;
            }
        } else {
            ModifiableHttpHeaders.super.setHeaders(headers);
        }
    }

    private void ensureCapacity() {
        if (size * 2 >= array.length) {
            array = Arrays.copyOf(array, array.length * 2);
        }
    }

    private void ensureCapacity(int additional) {
        int needed = (size + additional) * 2;
        if (needed > array.length) {
            int newLen = array.length;
            while (newLen < needed) {
                newLen *= 2;
            }
            array = Arrays.copyOf(array, newLen);
        }
    }

    /**
     * Get the underlying array for zero-copy access by serializers.
     *
     * @return the backing array (name, value pairs)
     */
    String[] rawArray() {
        return array;
    }

    /**
     * Get the number of name-value pairs (entries).
     *
     * @return entry count
     */
    int entryCount() {
        return size;
    }

    /**
     * Iterator that groups values by header name for compatibility with the Map-based API.
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
            return new AbstractMap.SimpleImmutableEntry<>(name, values);
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
}
