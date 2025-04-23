/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import java.util.function.Function;

final class UnmodifiableContext implements Context {

    static final Context EMPTY = new UnmodifiableContext(new MapStorageContext());

    private final Context delegate;

    UnmodifiableContext(Context delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> Context put(Key<T> key, T value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Context putIfAbsent(Key<T> key, T value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T get(Key<T> key) {
        return delegate.get(key);
    }

    @Override
    public <T> T computeIfAbsent(Key<T> key, Function<Key<T>, ? extends T> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyTo(Context target) {
        delegate.copyTo(target);
    }
}
