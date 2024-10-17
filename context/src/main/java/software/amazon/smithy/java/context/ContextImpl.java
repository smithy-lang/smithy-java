/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.function.Function;

final class ContextImpl implements Context {

    private static final int PADDING = 16;
    private static final Object[] EMPTY = new Object[0];
    private Object[] values;

    ContextImpl() {
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
    }

    private void resize() {
        // Pad to allow for new values to be added without needing a resize.
        int targetSize = Key.COUNTER.get() + PADDING;
        // Only resize when a copy is needed or the target size is not the current size.
        if (targetSize != values.length) {
            values = new Object[targetSize];
        }
    }

    @Override
    public <T> void putIfAbsent(Key<T> key, T value) {
        var idx = key.id;
        if (idx >= values.length) {
            resize();
            values[idx] = value;
        } else if (values[idx] == null) {
            values[idx] = value;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(Key<T> key, Function<Key<T>, ? extends T> mappingFunction) {
        var idx = key.id;
        T currentValue;
        if (idx >= values.length) {
            resize();
            currentValue = mappingFunction.apply(key);
            values[idx] = currentValue;
        } else if (values[idx] == null) {
            currentValue = mappingFunction.apply(key);
            values[idx] = currentValue;
        } else {
            currentValue = (T) values[idx];
        }
        return currentValue;
    }

    @Override
    public void putAll(Context context) {
        Object[] valuesToAdd;
        if (context instanceof ContextImpl impl) {
            valuesToAdd = impl.values;
        } else if (context instanceof UnmodifiableContext unmodifiableContext) {
            valuesToAdd = unmodifiableContext.delegate().values;
        } else {
            throw new IllegalArgumentException("Unsupported context type: " + context.getClass().getName());
        }

        // No need to copy or resize when the provided context is empty.
        if (valuesToAdd.length == 0) {
            return;
        }

        // Make sure it's operating on an allocated object array with the right size and doesn't need a copy.
        resize();

        // If the current context is empty, use the values of the target context.
        if (values.length == 0) {
            System.arraycopy(valuesToAdd, 0, values, 0, valuesToAdd.length - 1);
        } else {
            // Overwrite only when the values being copied are not null.
            for (var i = 0; i < valuesToAdd.length; i++) {
                if (valuesToAdd[i] != null) {
                    values[i] = valuesToAdd[i];
                }
            }
        }
    }
}
