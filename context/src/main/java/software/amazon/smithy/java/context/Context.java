/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.Objects;
import java.util.function.Function;

/**
 * A typed context map.
 */
public sealed interface Context permits ContextImpl, UnmodifiableContext {
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
     * Set a Property. If it was already present, it is overridden.
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
     * Set all the properties from the given Context in. If it was already present, it is overridden.
     *
     * @param context Context containing all the properties to put.
     */
    void putAll(Context context);

    /**
     * Creates an empty Context.
     *
     * @return the created context.
     */
    static Context create() {
        return new ContextImpl();
    }

    /**
     * Get an unmodifiable view of the Context.
     *
     * @return an unmodifiable view of the Context.
     */
    static Context unmodifiableViewOf(Context context) {
        if (context instanceof ContextImpl impl) {
            return new UnmodifiableContext(impl);
        } else if (context instanceof UnmodifiableContext unmodifiableContext) {
            return unmodifiableContext;
        } else {
            throw new IllegalArgumentException("Unsupported context type: " + context.getClass().getName());
        }
    }
}
