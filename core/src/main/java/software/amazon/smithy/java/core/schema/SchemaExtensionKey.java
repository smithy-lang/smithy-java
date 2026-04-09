/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A typed key for storing extension data on {@link Schema} objects.
 *
 * <p>Each key has a unique integer ID used for O(1) array-indexed access.
 * Keys should be declared as {@code static final} fields.
 *
 * @param <T> The type of extension data stored under this key.
 */
public final class SchemaExtensionKey<T> {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    final int id;

    public SchemaExtensionKey() {
        this.id = NEXT_ID.getAndIncrement();
    }

    static int count() {
        return NEXT_ID.get();
    }
}
