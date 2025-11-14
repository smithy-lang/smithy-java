/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A typed context map.
 */
public sealed interface Context permits ChunkedArrayStorageContext, UnmodifiableContext {

    /**
     * A {@code Key} provides an identity-based, immutable token.
     *
     * <p>The token also contains a name used to describe the value.
     *
     * <p>Create dedicated keys that are stored in static final class properties.
     * <em>Do not</em> create ephemeral keys on demand as this will continually increase the size of every created
     * Context.
     */
    final class Key<T> {

        // Global registry of all keys for copyTo operations
        @SuppressWarnings("rawtypes")
        static final List<Key> KEYS = new ArrayList<>();

        private final String name;
        final int id;
        private final Function<T, T> copyFunction;

        /**
         * @param name Name of the value.
         */
        private Key(String name, Function<T, T> copyFunction) {
            this.name = Objects.requireNonNull(name);
            this.copyFunction = Objects.requireNonNull(copyFunction);
            synchronized (KEYS) {
                this.id = KEYS.size();
                KEYS.add(this);
            }
        }

        @Override
        public String toString() {
            return name;
        }

        /**
         * Given a value stored in a context key of this type, creates an independent copy of the value.
         *
         * @param value Value to copy.
         * @return the copied value.
         */
        public T copyValue(T value) {
            return copyFunction.apply(value);
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
        return key(name, Function.identity());
    }

    /**
     * Create a new identity-based key to store in the context, and use a function to copy mutable values so they
     * can be independently copied into other contexts.
     *
     * @param name Name of the key.
     * @param copyFunction A function that takes the current value of a key and returns an independent copy of it.
     * @return the created key.
     * @param <T> Value type associated with the key.
     */
    static <T> Key<T> key(String name, Function<T, T> copyFunction) {
        return new Key<>(name, copyFunction);
    }

    /**
     * Set a Property. If it was already present, it is overridden.
     *
     * @param key   Property key.
     * @param value Value to set.
     * @param <T>   Value type.
     */
    <T> Context put(Key<T> key, T value);

    /**
     * Set a Property if not already present.
     *
     * @param key   Property key.
     * @param value Value to set.
     * @param <T>   Value type.
     */
    default <T> Context putIfAbsent(Key<T> key, T value) {
        if (get(key) == null) {
            put(key, value);
        }
        return this;
    }

    /**
     * Get a property.
     *
     * @param key Property key to get by exact reference identity.
     * @return    the value, or null if not present.
     * @param <T> Value type.
     */
    <T> T get(Key<T> key);

    /**
     * Get a property from the context, or return a default value if not found.
     *
     * @param key Property key to get by exact reference identity.
     * @param defaultValue Value to return if the property isn't found.
     * @return the value, or null if not present.
     * @param <T> Value type.
     */
    default <T> T getOrDefault(Key<T> key, T defaultValue) {
        var result = get(key);
        return result == null ? defaultValue : result;
    }

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
            throw new NullPointerException("Missing required context property: " + key);
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
    default <T> T computeIfAbsent(Key<T> key, Function<Key<T>, ? extends T> mappingFunction) {
        var result = get(key);
        if (result == null) {
            result = mappingFunction.apply(key);
            put(key, result);
        }
        return result;
    }

    /**
     * Copy this context into the target context, overwriting any existing keys.
     *
     * @param target Context to copy to.
     */
    void copyTo(Context target);

    /**
     * Merges this context with {@code other}, returning <strong>a new context instance</strong>.
     *
     * @param other The context to merge. Keys from this context overwrite keys from this context.
     * @return the created and merged context.
     */
    default Context merge(Context other) {
        Context result = Context.create();
        copyTo(result);
        other.copyTo(result);
        return result;
    }

    /**
     * Get an empty and unmodifiable Context.
     *
     * @return the empty and unmodifiable context.
     */
    static Context empty() {
        return UnmodifiableContext.EMPTY;
    }

    /**
     * Creates an empty Context.
     *
     * @return the created context.
     */
    static Context create() {
        return new ChunkedArrayStorageContext();
    }

    /**
     * Get a modifiable copy of the Context.
     *
     * @return a modifiable copy of the Context.
     */
    static Context modifiableCopy(Context context) {
        Context copy = Context.create();
        context.copyTo(copy);
        return copy;
    }

    /**
     * Get an unmodifiable copy of the Context.
     *
     * @return an unmodifiable copy of the Context.
     */
    static Context unmodifiableCopy(Context context) {
        return unmodifiableView(modifiableCopy(context));
    }

    /**
     * Get an unmodifiable view of the Context.
     *
     * @return an unmodifiable view of the Context.
     */
    static Context unmodifiableView(Context context) {
        if (context instanceof UnmodifiableContext) {
            return context;
        } else {
            return new UnmodifiableContext(context);
        }
    }
}
