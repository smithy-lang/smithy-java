/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleModifiableHttpHeaders implements ModifiableHttpHeaders {

    private final Map<String, List<String>> headers = new ConcurrentHashMap<>();

    @Override
    public void putHeader(String name, String value) {
        headers.computeIfAbsent(formatPutKey(name), k -> new ArrayList<>()).add(value);
    }

    @Override
    public void putHeader(String name, List<String> values) {
        headers.computeIfAbsent(formatPutKey(name), k -> new ArrayList<>()).addAll(values);
    }

    private static String formatPutKey(String name) {
        return name.trim().toLowerCase(Locale.ENGLISH);
    }

    @Override
    public void removeHeader(String name) {
        headers.remove(name.toLowerCase(Locale.ENGLISH));
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
