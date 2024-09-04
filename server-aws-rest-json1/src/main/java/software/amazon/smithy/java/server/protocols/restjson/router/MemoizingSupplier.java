/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.function.Supplier;

final class MemoizingSupplier<T> implements Supplier<T> {

    private volatile T value;
    private volatile boolean initialized = false;
    private final Supplier<T> delegate;

    MemoizingSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    value = delegate.get();
                    initialized = true;
                }
            }
        }

        return value;
    }
}
