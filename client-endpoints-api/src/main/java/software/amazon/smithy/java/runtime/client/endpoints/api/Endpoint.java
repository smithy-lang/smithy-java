/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

/**
 * A resolved endpoint.
 */
public interface Endpoint {
    /**
     * The endpoint URI.
     *
     * @return URI of the endpoint.
     */
    URI uri();

    /**
     * Get the value of an EndpointProperty for the endpoint.
     *
     * <p>For example, in some AWS use cases, this might contain HTTP headers to add to each request.
     *
     * @param property Endpoint property to get.
     * @return the value or null if not found.
     */
    <T> T property(EndpointProperty<T> property);

    /**
     * Get the properties of the endpoint.
     *
     * @return the properties.
     */
    Iterator<EndpointProperty<?>> properties();

    /**
     * Get the list of auth scheme overrides for the endpoint.
     *
     * @return the auth schemes overrides.
     */
    List<EndpointAuthScheme> authSchemes();

    /**
     * Convert the endpoint to a builder.
     *
     * @return the builder.
     */
    default Builder toBuilder() {
        var builder = new EndpointImpl.Builder();
        builder.uri(uri());
        properties().forEachRemaining(k -> builder.properties.put(k, property(k)));
        for (EndpointAuthScheme authScheme : authSchemes()) {
            builder.addAuthScheme(authScheme);
        }
        return builder;
    }

    /**
     * Create a builder used to build an {@link Endpoint}.
     *
     * @return the created builder.
     */
    static Builder builder() {
        return new EndpointImpl.Builder();
    }

    /**
     * Builder to create an {@link Endpoint}.
     */
    interface Builder {

        /**
         * Set the URI of the endpoint.
         *
         * @param uri URI to set.
         * @return the builder.
         */
        Builder uri(URI uri);

        /**
         * Set the URI of the endpoint.
         *
         * @param uri URI to set.
         * @return the builder.
         */
        default Builder uri(String uri) {
            try {
                return uri(new URI(uri));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Add an auth scheme override to the endpoint.
         *
         * @param authScheme Auth scheme override to add.
         * @return the builder.
         */
        Builder addAuthScheme(EndpointAuthScheme authScheme);

        /**
         * Put a typed property on the endpoint.
         *
         * @param property Property to set.
         * @param value    Value to associate with the property.
         * @return the builder.
         * @param <T> Value type.
         */
        <T> Builder putProperty(EndpointProperty<T> property, T value);

        /**
         * Create the endpoint.
         *
         * @return the created endpoint.
         */
        Endpoint build();
    }
}
