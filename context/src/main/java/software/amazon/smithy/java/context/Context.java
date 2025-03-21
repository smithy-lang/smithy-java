/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A typed context map.
 */
public sealed interface Context permits ArrayStorageContext, MapStorageContext, UnmodifiableContext {

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

        static final int MAX_ARRAY_KEY_SPACE = 64;

        // Hold onto keys because they're needed when putting the contents of an array context into a map context.
        // This could happen if the array context was created before the keyspace grew beyond MAX_ARRAY_KEY_SIZE,
        // but a map context was created after.
        @SuppressWarnings("rawtypes")
        static final Key[] KEYS = new Key[MAX_ARRAY_KEY_SPACE];

        // Each created key will get an assigned ID used to index into an array of possible keys.
        static final AtomicInteger COUNTER = new AtomicInteger();

        private final String name;
        final int id;
        private final Function<T, T> copyFunction;

        /**
         * @param name Name of the value.
         */
        private Key(String name, Function<T, T> copyFunction) {
            this.name = Objects.requireNonNull(name);
            this.id = COUNTER.getAndIncrement();
            this.copyFunction = Objects.requireNonNull(copyFunction);
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
        Key<T> key = new Key<>(name, copyFunction);
        if (key.id < Key.MAX_ARRAY_KEY_SPACE) {
            Key.KEYS[key.id] = key;
        }
        return key;
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
    default <T> void putIfAbsent(Key<T> key, T value) {
        if (get(key) == null) {
            put(key, value);
        }
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
     * @return the empty and umodifiable context.
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
        if (Key.COUNTER.get() >= Key.MAX_ARRAY_KEY_SPACE) {
            return new MapStorageContext();
        } else {
            return new ArrayStorageContext();
        }
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
