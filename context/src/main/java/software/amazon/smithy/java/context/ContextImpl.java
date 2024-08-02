/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

final class ContextImpl implements Context {
    private final Map<Key<?>, Object> attributes = new HashMap<>();

    ContextImpl() {
    }

    Map<Key<?>, Object> attributes() {
        return attributes;
    }

    @Override
    public <T> void put(Key<T> key, T value) {
        attributes.put(key, value);
    }

    @Override
    public <T> void putIfAbsent(Key<T> key, T value) {
        attributes.putIfAbsent(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        return (T) attributes.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(Key<T> key, Function<Key<T>, ? extends T> mappingFunction) {
        return (T) attributes.computeIfAbsent(key, k -> mappingFunction.apply((Key<T>) k));
    }

    @Override
    public void putAll(Context context) {
        Map<Key<?>, Object> attributesToAdd;
        if (context instanceof ContextImpl impl) {
            attributesToAdd = impl.attributes();
        } else if (context instanceof UnmodifiableContext unmodifiableContext) {
            attributesToAdd = unmodifiableContext.delegate().attributes();
        } else {
            throw new IllegalArgumentException("Unsupported context type: " + context.getClass().getName());
        }
        attributes.putAll(attributesToAdd);
    }
}
