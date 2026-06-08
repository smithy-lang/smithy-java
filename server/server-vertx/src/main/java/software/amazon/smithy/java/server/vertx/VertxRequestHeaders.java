/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.vertx;

import io.vertx.core.MultiMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import software.amazon.smithy.java.http.api.HttpHeaders;

/**
 * Read-only adapter from a Vert.x {@link MultiMap} (request headers) to
 * Smithy's {@link HttpHeaders}. Header names are normalized to lowercase
 * to match Smithy's case-insensitive contract.
 */
final class VertxRequestHeaders implements HttpHeaders {

    private final MultiMap delegate;

    VertxRequestHeaders(MultiMap delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<String> allValues(String name) {
        var values = delegate.getAll(name);
        return values == null ? List.of() : List.copyOf(values);
    }

    @Override
    public String firstValue(String name) {
        return delegate.get(name);
    }

    @Override
    public boolean hasHeader(String name) {
        return delegate.contains(name);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Map<String, List<String>> map() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var name : delegate.names()) {
            String lower = name.toLowerCase(Locale.ROOT);
            List<String> existing = result.get(lower);
            if (existing == null) {
                existing = new ArrayList<>(delegate.getAll(name));
            } else {
                existing.addAll(delegate.getAll(name));
            }
            result.put(lower, existing);
        }
        // Make values unmodifiable for the consumer.
        Map<String, List<String>> unmodifiable = new LinkedHashMap<>(result.size());
        for (var e : result.entrySet()) {
            unmodifiable.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(unmodifiable);
    }
}
