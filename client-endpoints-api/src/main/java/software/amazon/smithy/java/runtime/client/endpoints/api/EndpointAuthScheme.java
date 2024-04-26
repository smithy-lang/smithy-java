/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.util.Iterator;

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
     * Get an auth scheme-specific property using a strongly typed key, or {@code null}.
     *
     * @param key Key of the property to get.
     * @return Returns the value or null of not found.
     */
    <T> T attribute(EndpointKey<T> key);

    /**
     * Get the attribute keys of the auth scheme.
     *
     * @return the attribute keys.
     */
    Iterator<EndpointKey<?>> attributeKeys();

    /**
     * Convert the EndpointAuthScheme to a builder.
     *
     * @return the created builder.
     */
    default Builder toBuilder() {
        EndpointAuthSchemeImpl.Builder builder = new EndpointAuthSchemeImpl.Builder();
        builder.authSchemeId(authSchemeId());
        attributeKeys().forEachRemaining(k -> builder.attributes.put(k, attribute(k)));
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
         * Add an attribute to the EndpointAuthScheme.
         *
         * @param key   Ket to set.
         * @param value Value to set.
         * @return the builder.
         * @param <T> the value type.
         */
        <T> Builder putAttribute(EndpointKey<T> key, T value);

        /**
         * Create the {@link EndpointAuthScheme}.
         *
         * @return the created EndpointAuthScheme.
         */
        EndpointAuthScheme build();
    }
}
