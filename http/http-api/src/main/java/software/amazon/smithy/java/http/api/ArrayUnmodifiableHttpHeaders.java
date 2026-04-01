/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable array-backed HTTP headers implementation.
 *
 * <p>Uses the same flat array storage as {@link ArrayHttpHeaders} for efficient
 * lookups with pointer comparison for interned header names.
 *
 * <p>Instances are created by copying from modifiable headers or directly from arrays.
 */
final class ArrayUnmodifiableHttpHeaders extends AbstractArrayHttpHeaders {

    static final HttpHeaders EMPTY = new ArrayUnmodifiableHttpHeaders(new String[0], 0);
    private volatile Map<String, List<String>> mapView;

    private ArrayUnmodifiableHttpHeaders(String[] array, int size) {
        super(array, size);
    }

    /**
     * Create from an ArrayHttpHeaders by copying its array.
     */
    static HttpHeaders of(ArrayHttpHeaders headers) {
        int entryCount = headers.size;
        if (entryCount == 0) {
            return EMPTY;
        }

        String[] copy = new String[entryCount * 2];
        System.arraycopy(headers.array, 0, copy, 0, entryCount * 2);
        return new ArrayUnmodifiableHttpHeaders(copy, entryCount);
    }

    /**
     * Create from any HttpHeaders.
     */
    static HttpHeaders of(HttpHeaders headers) {
        if (headers instanceof ArrayUnmodifiableHttpHeaders) {
            return headers;
        } else if (headers instanceof ArrayHttpHeaders ah) {
            return of(ah);
        } else if (headers.isEmpty()) {
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
            String name = HeaderName.canonicalize(e.getKey());
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
        ArrayHttpHeaders mutable = new ArrayHttpHeaders(input.size() * 2);
        for (Map.Entry<String, List<String>> e : input.entrySet()) {
            String name = HeaderName.canonicalize(e.getKey());
            for (String value : e.getValue()) {
                mutable.addHeaderCanonical(name, value);
            }
        }
        return of(mutable);
    }

    @Override
    public List<String> allValues(String name) {
        return allValuesCanonical(HeaderName.canonicalize(name));
    }

    @Override
    public List<String> allValues(HeaderName name) {
        return allValuesCanonical(name.name());
    }

    private List<String> allValuesCanonical(String key) {
        Map<String, List<String>> m = mapView;
        return m != null ? m.getOrDefault(key, Collections.emptyList()) : canonicalAllValues(key);
    }

    @Override
    public Map<String, List<String>> map() {
        Map<String, List<String>> m = mapView;
        if (m == null) {
            m = buildMapFromArray(array, size);
            mapView = m;
        }
        return m;
    }

    @Override
    public ModifiableHttpHeaders toModifiable() {
        return copyFrom(array, size);
    }

    @Override
    public HttpHeaders toUnmodifiable() {
        return this;
    }

    @Override
    public String toString() {
        return "ArrayUnmodifiableHttpHeaders{" + map() + '}';
    }
}
