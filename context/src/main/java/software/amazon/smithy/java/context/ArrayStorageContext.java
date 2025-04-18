/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.HashMap;
import java.util.Map;

final class ArrayStorageContext implements Context {

    private static final int PADDING = 16;
    private static final Object[] EMPTY = new Object[0];

    // Stores values of the context with a size equal to the required keyspace + some padding, nulls in unused slots.
    private Object[] values;

    // Stores keys that exceed Key.MAX_ARRAY_KEY_SPACE in case this context is copied to another context.
    // This should only happen if an ArrayStorageContext is created before Contexts transition from array to
    // map-based storage due to subsequent keyspace growth. The first Key.MAX_ARRAY_KEY_SPACE keys in the keyspace are
    // stored in Key so they don't need to be stored here.
    private final Map<Integer, Key<?>> keys = new HashMap<>();

    ArrayStorageContext() {
        // Start with an empty array until the context is modified.
        this.values = EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        int idx = key.id;
        if (idx >= values.length) {
            return null;
        }
        return (T) values[idx];
    }

    @Override
    public <T> void put(Key<T> key, T value) {
        var idx = key.id;
        if (idx >= values.length) {
            resize();
        }
        this.values[idx] = value;

        // Only store keys when the id of the key exceeds the array index keyspace. This is an edge case due to
        // a growing keyspace. We need these keys in case Context#copyTo is called.
        if (idx >= Key.MAX_ARRAY_KEY_SPACE) {
            keys.put(key.id, key);
        }
    }

    private void resize() {
        // Pad to allow for new values to be added without needing a resize.
        int targetSize = Key.COUNTER.get() + PADDING;
        var newValues = new Object[targetSize];
        System.arraycopy(values, 0, newValues, 0, values.length);
        this.values = newValues;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void copyTo(Context target) {
        for (var i = 0; i < values.length; i++) {
            var v = values[i];
            if (v != null) {
                // Grab the key from the shared keyspace when possible.
                var k = i < Key.MAX_ARRAY_KEY_SPACE ? Key.KEYS[i] : keys.get(i);
                target.put(k, k.copyValue(v));
            }
        }
    }
}
