/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.util.Objects;

/**
 * A strongly typed, identity-based key used to store attributes in {@link Endpoint} and {@link EndpointAuthScheme}.
 *
 * @param <T> value type associated with the key.
 */
public class EndpointKey<T> {

    private final String name;

    /**
     * @param name Name of the value.
     */
    private EndpointKey(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Create a new identity-based endpoint key.
     *
     * @param name Name to associate with the key.
     * @return the created key.
     * @param <T> the value to store in the key.
     */
    public static <T> EndpointKey<T> of(String name) {
        return new EndpointKey<>(name);
    }

    /**
     * Get the name of the key.
     *
     * @return the key name.
     */
    @Override
    public final String toString() {
        return name;
    }
}
