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
import java.util.Objects;

/**
 * Simple mutable HTTP headers implementation.
 *
 * <p><b>Thread Safety:</b> This class is <b>not</b> thread-safe. If multiple threads
 * access an instance concurrently, and at least one thread modifies the headers,
 * external synchronization is required.
 */
final class SimpleModifiableHttpHeaders implements ModifiableHttpHeaders {

    private final Map<String, List<String>> headers = new HashMap<>();

    @Override
    public void addHeader(String name, String value) {
        getOrCreateValues(name).add(value);
    }

    @Override
    public void addHeader(String name, List<String> values) {
        getOrCreateValues(name).addAll(values);
    }

    private List<String> getOrCreateValues(String name) {
        var key = HttpHeaders.normalizeHeaderName(name);
        var values = headers.get(key);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(key, values);
        }
        return values;
    }

    @Override
    public void setHeader(String name, String value) {
        var key = HttpHeaders.normalizeHeaderName(name);
        var list = headers.get(key);
        if (list == null) {
            list = new ArrayList<>(1);
            headers.put(key, list);
        } else {
            list.clear();
        }

        list.add(value);
    }

    @Override
    public void setHeader(String name, List<String> values) {
        var key = HttpHeaders.normalizeHeaderName(name);
        var list = headers.get(key);
        if (list == null) {
            list = new ArrayList<>(values.size());
            headers.put(key, list);
        } else {
            list.clear();
        }

        // believe it or not, this is more efficient than the bulk constructor
        // https://bugs.openjdk.org/browse/JDK-8368292
        for (var element : values) {
            list.add(element);
        }
    }

    @Override
    public void removeHeader(String name) {
        headers.remove(name.toLowerCase(Locale.ENGLISH));
    }

    @Override
    public void clear() {
        headers.clear();
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleModifiableHttpHeaders entries = (SimpleModifiableHttpHeaders) o;
        return Objects.equals(headers, entries.headers);
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }
}
