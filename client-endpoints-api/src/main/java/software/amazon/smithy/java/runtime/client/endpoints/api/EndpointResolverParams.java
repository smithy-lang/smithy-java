/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates endpoint resolver parameters.
 */
public final class EndpointResolverParams {

    private final String operationName;
    private final Map<EndpointKey<?>, Object> immutableMap;

    private EndpointResolverParams(Map<EndpointKey<?>, Object> map, String operationName) {
        this.immutableMap = new HashMap<>(map);
        this.operationName = Objects.requireNonNull(operationName, "operationName is null");
    }

    /**
     * Create a new builder to build {@link EndpointResolverParams}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get an auth scheme-specific property using a strongly typed key, or {@code null}.
     *
     * @param key Key of the property to get.
     * @return Returns the value or null of not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T attribute(EndpointKey<T> key) {
        return (T) immutableMap.get(key);
    }

    /**
     * Get all the keys available when resolving the endpoint.
     *
     * @return the keys.
     */
    public Iterator<EndpointKey<?>> attributeKeys() {
        return immutableMap.keySet().iterator();
    }

    /**
     * Get the name of the operation to resolve the endpoint for.
     *
     * @return name of the operation.
     */
    public String operationName() {
        return operationName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EndpointResolverParams params = (EndpointResolverParams) o;
        return Objects.equals(operationName, params.operationName)
            && Objects.equals(immutableMap, params.immutableMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName, immutableMap);
    }

    /**
     * Create a builder from this {@link EndpointResolverParams}.
     *
     * @return the builder.
     */
    public Builder toBuilder() {
        Builder builder = builder();
        builder.map.putAll(immutableMap);
        builder.operationName(operationName);
        return builder;
    }

    /**
     * Builder used to create and {@link EndpointResolverParams}.
     */
    public static final class Builder {

        private String operationName;
        private final Map<EndpointKey<?>, Object> map = new HashMap<>();

        /**
         * Build the params.
         * @return the built params.
         */
        public EndpointResolverParams build() {
            return new EndpointResolverParams(map, operationName);
        }

        /**
         * Put an attribute on the params.
         *
         * @param key   Key to set.
         * @param value Value to set.
         * @return the builder.
         * @param <T> value type stored in the key.
         */
        public <T> Builder putAttribute(EndpointKey<T> key, T value) {
            map.put(key, value);
            return this;
        }

        /**
         * Set the name of the operation to resolve.
         *
         * @param operationName Name of the operation.
         * @return the builder.
         */
        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }
    }
}
