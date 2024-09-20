/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleModifiableHttpHeaders implements ModifiableHttpHeaders {

    private final Map<String, List<String>> headers = new ConcurrentHashMap<>();

    @Override
    public void putHeader(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    @Override
    public void putHeader(Map<String, List<String>> headers) {
        this.headers.putAll(headers);
    }

    @Override
    public void putHeader(String name, List<String> values) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).addAll(values);
    }

    @Override
    public void removeHeader(String name) {
        headers.remove(name);
    }

    @Override
    public String getFirstHeader(String name) {
        var list = headers.get(name);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public List<String> getHeader(String name) {
        return headers.get(name);
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
}
