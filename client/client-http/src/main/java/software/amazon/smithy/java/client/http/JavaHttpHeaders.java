/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;

/**
 * Adapter that exposes a JDK {@link java.net.http.HttpHeaders} as a smithy-java {@link HttpHeaders}.
 */
final class JavaHttpHeaders implements HttpHeaders {
    private final java.net.http.HttpHeaders headers;
    private volatile Map<String, List<String>> materialized;
    private int size = -1; // cheap enough to recompute

    JavaHttpHeaders(java.net.http.HttpHeaders headers) {
        this.headers = headers;
    }

    @Override
    public List<String> allValues(String name) {
        return headers.allValues(name);
    }

    @Override
    public List<String> allValues(HeaderName name) {
        return headers.allValues(name.name());
    }

    @Override
    public int size() {
        int result = size;
        if (result >= 0) {
            return result;
        }

        result = 0;
        for (var entry : headers.map().entrySet()) {
            String k = entry.getKey();
            // pseudo-headers are excluded
            if (k.charAt(0) != ':') {
                result += entry.getValue().size();
            }
        }

        size = result;
        return result;
    }

    @Override
    public Map<String, List<String>> map() {
        return materialize();
    }

    @Override
    public void forEachEntry(BiConsumer<String, String> consumer) {
        for (var entry : materialize().entrySet()) {
            for (var value : entry.getValue()) {
                consumer.accept(entry.getKey(), value);
            }
        }
    }

    @Override
    public <C> void forEachEntry(C contextValue, HeaderWithValueConsumer<C> consumer) {
        for (var entry : materialize().entrySet()) {
            for (var value : entry.getValue()) {
                consumer.accept(contextValue, entry.getKey(), value);
            }
        }
    }

    /**
     * Lazy single-shot canonicalization of the JDK headers. The JDK guarantees one entry per
     * case-insensitive name (its internal map uses {@code String.CASE_INSENSITIVE_ORDER}), so
     * we just lowercase each key and reuse the JDK's already-immutable value list by reference.
     */
    private Map<String, List<String>> materialize() {
        var result = materialized;
        if (result != null) {
            return result;
        }

        var grouped = new LinkedHashMap<String, List<String>>(headers.map().size());
        for (var entry : headers.map().entrySet()) {
            String key = entry.getKey();
            // pseudo-headers are excluded
            if (key.charAt(0) != ':') {
                grouped.put(HeaderName.canonicalize(key), entry.getValue());
            }
        }

        materialized = grouped;
        return grouped;
    }
}
