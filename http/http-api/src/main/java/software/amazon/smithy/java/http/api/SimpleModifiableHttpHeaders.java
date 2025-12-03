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

    static ModifiableHttpHeaders of(HttpHeaders headers) {
        if (headers instanceof ModifiableHttpHeaders h) {
            return h;
        } else {
            var hd = new SimpleModifiableHttpHeaders();
            hd.setHeaders(headers);
            return hd;
        }
    }

    @Override
    public void addHeader(String name, String value) {
        getOrCreateValues(name).add(HeaderUtils.normalizeValue(value));
    }

    @Override
    public void addHeader(String name, List<String> values) {
        if (!values.isEmpty()) {
            var line = getOrCreateValues(name);
            for (var v : values) {
                line.add(HeaderUtils.normalizeValue(v));
            }
        }
    }

    private List<String> getOrCreateValues(String name) {
        return getOrCreateValuesUnsafe(HeaderUtils.normalizeName(name));
    }

    private List<String> getOrCreateValuesUnsafe(String key) {
        var values = headers.get(key);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(key, values);
        }
        return values;
    }

    @Override
    public void setHeader(String name, String value) {
        var key = HeaderUtils.normalizeName(name);
        var list = headers.get(key);
        if (list == null) {
            list = new ArrayList<>(1);
            headers.put(key, list);
        } else {
            list.clear();
        }

        list.add(HeaderUtils.normalizeValue(value));
    }

    @Override
    public void setHeader(String name, List<String> values) {
        List<String> copy = new ArrayList<>(values.size());
        for (var v : values) {
            copy.add(HeaderUtils.normalizeValue(v));
        }
        headers.put(HeaderUtils.normalizeName(name), copy);
    }

    @Override
    public void removeHeader(String name) {
        headers.remove(HeaderUtils.normalizeName(name));
    }

    @Override
    public void clear() {
        headers.clear();
    }

    @Override
    public List<String> allValues(String name) {
        return headers.getOrDefault(name.toLowerCase(Locale.ROOT), Collections.emptyList());
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
    public void setHeaders(HttpHeaders headers) {
        // No need to reformat or group keys because they come from another HttpHeaders container.
        for (var e : headers.map().entrySet()) {
            setHeaderUnsafe(e.getKey(), e.getValue());
        }
    }

    @Override
    public List<String> setHeaderIfAbsent(String name, List<String> values) {
        return headers.computeIfAbsent(HeaderUtils.normalizeName(name), n -> {
            var trimmed = new ArrayList<String>(values.size());
            for (var v : values) {
                trimmed.add(HeaderUtils.normalizeValue(v));
            }
            return trimmed;
        });
    }

    @Override
    public List<String> setHeaderIfAbsent(String name, String value) {
        return headers.computeIfAbsent(HeaderUtils.normalizeName(name),
                n -> List.of(HeaderUtils.normalizeValue(value)));
    }

    // Set header using a pre-formatted keys and already trimmed values.
    private void setHeaderUnsafe(String key, List<String> values) {
        var list = getOrCreateValuesUnsafe(key);
        // believe it or not, this is more efficient than the bulk constructor
        // https://bugs.openjdk.org/browse/JDK-8368292
        for (var element : values) {
            list.add(element);
        }
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
