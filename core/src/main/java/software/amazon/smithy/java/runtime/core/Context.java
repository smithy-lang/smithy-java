/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A thread safe, mutable, typed context map.
 */
public interface Context {
    /**
     * A {@code Key} provides an identity-based, immutable token.
     *
     * <p>The token also contains a name used to describe the value.
     */
    final class Key<T> {
        private final String name;

        /**
         * @param name Name of the value.
         */
        private Key(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Create a new identity-based key to store in the context.
     *
     * @param name Name of the key.
     * @return the created key.
     * @param <T> Value type associated with the key.
     */
    static <T> Key<T> key(String name) {
        return new Key<>(name);
    }

    /**
     * Set a Property.
     *
     * @param key   Property key.
     * @param value Value to set.
     * @param <T>   Value type.
     */
    <T> void put(Key<T> key, T value);

    /**
     * Set a Property if not already present.
     *
     * @param key   Property key.
     * @param value Value to set.
     * @param <T>   Value type.
     */
    <T> void putIfAbsent(Key<T> key, T value);

    /**
     * Get a property.
     *
     * @param key Property key to get by exact reference identity.
     * @return    the value, or null if not present.
     * @param <T> Value type.
     */
    <T> T get(Key<T> key);

    /**
     * Get a property and throw if it isn't present.
     *
     * @param key Property key to get by exact reference identity.
     * @return the value
     * @throws NullPointerException if the property isn't found.
     * @param <T> Value type.
     */
    default <T> T expect(Key<T> key) {
        T value = get(key);
        if (value == null) {
            throw new NullPointerException("Unknown context property: " + key);
        }
        return value;
    }

    /**
     * Get a property or set and get a default if not present.
     *
     * <p>The mapping function should not modify the context during computation.
     *
     * @param key Property key to get by exact reference identity.
     * @param mappingFunction A function that computes a value for this key if the value is not assigned.
     * @return the value assigned to the key.
     * @param <T> Value type.
     */
    <T> T computeIfAbsent(Key<T> key, Function<Key<T>, ? extends T> mappingFunction);

    /**
     * Get the keys added to the context.
     *
     * @return the keys.
     */
    Iterator<Key<?>> keys();

    /**
     * Creates a thread-safe, mutable context map.
     *
     * @return the created context.
     */
    static Context create() {
        return new Context() {
            private final ConcurrentMap<Key<?>, Object> attributes = new ConcurrentHashMap<>();

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
            public Iterator<Key<?>> keys() {
                return attributes.keySet().iterator();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T computeIfAbsent(Key<T> key, Function<Key<T>, ? extends T> mappingFunction) {
                return (T) attributes.computeIfAbsent(key, k -> mappingFunction.apply((Key<T>) k));
            }
        };
    }
}
