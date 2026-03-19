/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import software.amazon.smithy.java.context.Context;

/**
 * Allows for adding settings to a client.
 *
 * @param <B> Fluent interface to return.
 */
public interface ClientSetting<B extends ClientSetting<B>> {
    /**
     * Put a strongly typed configuration on the builder.
     *
     * @param key Configuration key.
     * @param value Value to associate with the key.
     * @return the builder.
     * @param <V> Value type.
     */
    <V> B putConfig(Context.Key<V> key, V value);

    /**
     * Put a strongly typed configuration on the builder, if not already present.
     *
     * @param key Configuration key.
     * @param value Value to associate with the key.
     * @return the builder.
     * @param <T> Value type.
     */
    <T> B putConfigIfAbsent(Context.Key<T> key, T value);
}
