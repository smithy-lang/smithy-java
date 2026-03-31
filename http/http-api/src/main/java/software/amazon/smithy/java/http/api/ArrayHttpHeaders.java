/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.Arrays;
import java.util.List;

/**
 * High-performance mutable array-backed HTTP headers implementation.
 *
 * <p><b>Thread Safety:</b> This class is <b>not</b> thread-safe.
 */
final class ArrayHttpHeaders extends AbstractArrayHttpHeaders implements ModifiableHttpHeaders {

    private static final int INITIAL_CAPACITY = 32; // 16 name-value pairs

    ArrayHttpHeaders() {
        super(new String[INITIAL_CAPACITY], 0);
    }

    ArrayHttpHeaders(int expectedPairs) {
        super(new String[Math.max(expectedPairs * 2, 8)], 0);
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
        ensureCapacity(values.size());
        String key = HeaderNames.canonicalize(name);
        for (String v : values) {
            addHeaderInterned(key, v);
        }
    }

    @Override
    public void setHeader(String name, String value) {
        String key = HeaderNames.canonicalize(name);
        String normalizedValue = HeaderUtils.normalizeValue(value);

        int end = size * 2;
        for (int i = 0; i < end; i += 2) {
            String headerName = array[i];
            if (headerName == key || headerName.equals(key)) {
                array[i + 1] = normalizedValue;

                // Happy path: Fast scan for duplicates without tracking write pointers.
                for (int j = i + 2; j < end; j += 2) {
                    String otherName = array[j];
                    if (otherName == key || otherName.equals(key)) {

                        // Unhappy path: Duplicate found. Compact the remainder of the array.
                        int write = j;
                        for (int read = j + 2; read < end; read += 2) {
                            String n = array[read];
                            if (n != key && !n.equals(key)) {
                                // Unconditional copy avoids branch prediction misses
                                // since 'read' is guaranteed to be > 'write' here.
                                array[write] = n;
                                array[write + 1] = array[read + 1];
                                write += 2;
                            }
                        }

                        // Null out remaining trailing elements
                        for (int k = write; k < end; k++) {
                            array[k] = null;
                        }
                        size = write / 2;
                        return;
                    }
                }
                return;
            }
        }

        // Did not exist, add it.
        ensureCapacity();
        int idx = size * 2;
        array[idx] = key;
        array[idx + 1] = normalizedValue;
        size++;
    }

    @Override
    public void setHeader(String name, List<String> values) {
        String key = HeaderNames.canonicalize(name);
        removeByKey(key);
        ensureCapacity(values.size());
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
            if (n != key && !n.equals(key)) {
                if (write != read) {
                    array[write * 2] = n;
                    array[write * 2 + 1] = array[idx + 1];
                }
                write++;
            }
        }

        // Clear removed slots
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
    public ModifiableHttpHeaders copy() {
        return copyFrom(array, size);
    }

    @Override
    public void addHeaders(HttpHeaders headers) {
        if (headers instanceof AbstractArrayHttpHeaders ah) {
            ensureCapacity(ah.size);
            System.arraycopy(ah.array, 0, array, size * 2, ah.size * 2);
            size += ah.size;
        } else {
            ModifiableHttpHeaders.super.addHeaders(headers);
        }
    }

    @Override
    public void setHeaders(HttpHeaders headers) {
        if (headers instanceof AbstractArrayHttpHeaders ah) {
            // Single-pass compaction: remove all entries whose names appear in source
            int write = 0;
            for (int read = 0; read < size; read++) {
                int idx = read * 2;
                String n = array[idx];
                if (!containsName(ah, n)) {
                    if (write != read) {
                        array[write * 2] = n;
                        array[write * 2 + 1] = array[idx + 1];
                    }
                    write++;
                }
            }
            for (int i = write * 2; i < size * 2; i++) {
                array[i] = null;
            }
            size = write;

            // Bulk copy source entries
            ensureCapacity(ah.size);
            System.arraycopy(ah.array, 0, array, size * 2, ah.size * 2);
            size += ah.size;
        } else {
            ModifiableHttpHeaders.super.setHeaders(headers);
        }
    }

    private static boolean containsName(AbstractArrayHttpHeaders source, String name) {
        for (int i = 0; i < source.size * 2; i += 2) {
            if (source.array[i] == name || source.array[i].equals(name)) {
                return true;
            }
        }
        return false;
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
}
