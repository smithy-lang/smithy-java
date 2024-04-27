/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.util.Objects;

/**
 * A strongly typed, identity-based key used to store endpoint properties in {@link Endpoint},
 * {@link EndpointAuthScheme}, etc.
 *
 * @param <T> Value type associated with the property.
 */
public class EndpointProperty<T> {

    private final String name;

    /**
     * @param name Name of the value.
     */
    private EndpointProperty(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Create a new EndpointProperty.
     *
     * @param name Name of the value used to describe the property.
     * @return the created property.
     * @param <T> Value type associated with the property.
     */
    public static <T> EndpointProperty<T> of(String name) {
        return new EndpointProperty<>(name);
    }

    /**
     * Get the name of the property.
     *
     * @return the property name.
     */
    @Override
    public final String toString() {
        return name;
    }
}
