/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.util.Set;

/**
 * An authentication scheme supported for the endpoint.
 */
public interface EndpointAuthScheme {
    /**
     * The ID of the auth scheme (e.g., "aws.auth#sigv4").
     *
     * @return the auth scheme ID.
     */
    String authSchemeId();

    /**
     * Get the value of an EndpointProperty for the EndpointAuthScheme.
     *
     * @param property Endpoint property to get.
     * @return the value or null if not found.
     */
    <T> T property(EndpointProperty<T> property);

    /**
     * Get the properties of the EndpointAuthScheme.
     *
     * @return the properties.
     */
    Set<EndpointProperty<?>> properties();

    /**
     * Convert the EndpointAuthScheme to a builder.
     *
     * @return the created builder.
     */
    default Builder toBuilder() {
        EndpointAuthSchemeImpl.Builder builder = new EndpointAuthSchemeImpl.Builder();
        builder.authSchemeId(authSchemeId());
        properties().forEach(k -> builder.properties.put(k, property(k)));
        return builder;
    }

    /**
     * Create a builder for {@link EndpointAuthScheme}.
     *
     * @return the builder.
     */
    static Builder builder() {
        return new EndpointAuthSchemeImpl.Builder();
    }

    /**
     * Builds an EndpointAuthScheme.
     */
    interface Builder {
        /**
         * Set the auth scheme ID.
         *
         * @param authSchemeId Auth scheme ID to set.
         * @return the builder.
         */
        Builder authSchemeId(String authSchemeId);

        /**
         * Put a typed property on the EndpointAuthScheme.
         *
         * @param property Property to set.
         * @param value    Value to associate with the property.
         * @return the builder.
         * @param <T> Value type.
         */
        <T> Builder putProperty(EndpointProperty<T> property, T value);

        /**
         * Create the {@link EndpointAuthScheme}.
         *
         * @return the created EndpointAuthScheme.
         */
        EndpointAuthScheme build();
    }
}
