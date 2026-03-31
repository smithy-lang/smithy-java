/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

/**
 * Base class for array-backed HTTP headers implementations.
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
 */
abstract class AbstractArrayHttpHeaders implements HttpHeaders {

    protected String[] array;
    protected int size; // Number of name-value pairs (size*2 = array slots used)

    AbstractArrayHttpHeaders(String[] array, int size) {
        this.array = array;
        this.size = size;
    }

    @Override
    public String firstValue(String name) {
        String key = HeaderNames.canonicalize(name);
        for (int i = 0; i < size * 2; i += 2) {
            String headerName = array[i];
            if (headerName == key || headerName.equals(key)) {
                return array[i + 1];
            }
        }
        return null;
    }

    @Override
    public List<String> allValues(String name) {
        return canonicalAllValues(HeaderNames.canonicalize(name));
    }

    protected final List<String> canonicalAllValues(String canonicalName) {
        for (int i = 0; i < size * 2; i += 2) {
            String headerName = array[i];
            if (headerName == canonicalName || headerName.equals(canonicalName)) {
                return new HeaderValues(canonicalName, i);
            }
        }
        return Collections.emptyList();
    }

    // Header list implementation that implements a fast iterator, and has a slow materialization pass when things
    // like size or get by index are called.
    private final class HeaderValues extends AbstractList<String> {
        private final String key;
        private final int firstIndex; // index into array (already multiplied by 2)
        private int cachedCount = 0;

        HeaderValues(String key, int firstIndex) {
            this.key = key;
            this.firstIndex = firstIndex;
        }

        @Override
        public String get(int index) {
            int found = 0;
            for (int i = firstIndex; i < size * 2; i += 2) {
                String headerName = array[i];
                if (headerName == key || headerName.equals(key)) {
                    if (found == index) {
                        return array[i + 1];
                    }
                    found++;
                }
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int size() {
            int count = cachedCount;
            if (count == 0) {
                for (int i = firstIndex; i < size * 2; i += 2) {
                    String headerName = array[i];
                    if (headerName == key || headerName.equals(key)) {
                        count++;
                    }
                }
                cachedCount = count;
            }
            return count;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Iterator<String> iterator() {
            return new HeaderValueIterator(key, array, firstIndex, size);
        }
    }

    private static final class HeaderValueIterator implements Iterator<String> {
        private final int size;
        private final String[] array;
        private final String key;
        private int pos;

        private HeaderValueIterator(String key, String[] array, int firstIndex, int size) {
            this.key = key;
            this.array = array;
            this.pos = firstIndex;
            this.size = size;
        }

        @Override
        public boolean hasNext() {
            while (pos < size * 2) {
                String headerName = array[pos];
                if (headerName == key || headerName.equals(key)) {
                    return true;
                }
                pos += 2;
            }
            return false;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String value = array[pos + 1];
            pos += 2;
            return value;
        }
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
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Map<String, List<String>> map() {
        return buildMapFromArray(array, size);
    }

    protected static Map<String, List<String>> buildMapFromArray(String[] array, int size) {
        if (size == 0) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < size * 2; i += 2) {
            String name = array[i];
            List<String> values = result.get(name);
            if (values == null) {
                values = new ArrayList<>(2);
                result.put(name, values);
            }
            values.add(array[i + 1]);
        }

        // Make immutable
        for (var e : result.entrySet()) {
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }

        return Collections.unmodifiableMap(result);
    }

    @Override
    public void forEachEntry(BiConsumer<String, String> consumer) {
        for (int i = 0; i < size * 2; i += 2) {
            consumer.accept(array[i], array[i + 1]);
        }
    }

    /**
     * Create a new ArrayHttpHeaders by copying raw array data.
     */
    protected static ArrayHttpHeaders copyFrom(String[] source, int entryCount) {
        ArrayHttpHeaders copy = new ArrayHttpHeaders(entryCount);
        System.arraycopy(source, 0, copy.array, 0, entryCount * 2);
        copy.size = entryCount;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpHeaders other)) {
            return false;
        }
        if (size != other.size()) {
            return false;
        }

        // Fast path: both are array-backed, compare flat arrays directly
        if (o instanceof AbstractArrayHttpHeaders ah) {
            for (int i = 0; i < size * 2; i++) {
                if (!array[i].equals(ah.array[i])) {
                    // Might be a different order, so create and compare maps.
                    return map().equals(other.map());
                }
            }
            return true;
        }

        return map().equals(other.map());
    }

    @Override
    public int hashCode() {
        return map().hashCode();
    }
}
