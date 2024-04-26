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
     * Get an endpoint-specific property using a strongly typed key, or {@code null}.
     *
     * <p>For example, in some AWS use cases, this might contain HTTP headers to add to each request.
     *
     * @param key Endpoint key to get.
     * @return Returns the value or null of not found.
     */
    <T> T endpointAttribute(EndpointKey<T> key);

    /**
     * Get the attribute keys of the endpoint.
     *
     * @return the attribute keys.
     */
    Iterator<EndpointKey<?>> endpointAttributeKeys();

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
        endpointAttributeKeys().forEachRemaining(k -> builder.attributes.put(k, endpointAttribute(k)));
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
         * Put a typed attribute on the endpoint.
         *
         * @param key   Key to set.
         * @param value Value to associate with the key.
         * @return the builder.
         * @param <T> Value type.
         */
        <T> Builder putAttribute(EndpointKey<T> key, T value);

        /**
         * Create the endpoint.
         *
         * @return the created endpoint.
         */
        Endpoint build();
    }
}
