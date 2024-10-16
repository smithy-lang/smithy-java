/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import software.amazon.smithy.java.context.Context;

public interface ClientSetting<B extends Client.Builder<?, B>> {
    /**
     * Put a strongly typed configuration on the builder.
     *
     * @param key Configuration key.
     * @param value Value to associate with the key.
     * @return the builder.
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked")
    default <V> B putConfig(Context.Key<V> key, V value) {
        ((B) this).configBuilder().putConfig(key, value);
        return (B) this;
    }

    /**
     * Put a strongly typed configuration on the builder, if not already present.
     *
     * @param key Configuration key.
     * @param value Value to associate with the key.
     * @return the builder.
     * @param <T> Value type.
     */
    @SuppressWarnings("unchecked")
    default <T> B putConfigIfAbsent(Context.Key<T> key, T value) {
        ((B) this).configBuilder().putConfigIfAbsent(key, value);
        return (B) this;
    }
}
