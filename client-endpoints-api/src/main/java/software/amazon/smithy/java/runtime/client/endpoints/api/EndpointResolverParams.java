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
    private final Map<EndpointProperty<?>, Object> immutableMap;

    private EndpointResolverParams(Map<EndpointProperty<?>, Object> map, String operationName) {
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
     * Get the value of an EndpointProperty.
     *
     * @param property Endpoint property to get.
     * @return the value or null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T property(EndpointProperty<T> property) {
        return (T) immutableMap.get(property);
    }

    /**
     * Get all the properties available when resolving the endpoint.
     *
     * @return the properties.
     */
    public Iterator<EndpointProperty<?>> properties() {
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
        private final Map<EndpointProperty<?>, Object> map = new HashMap<>();

        /**
         * Build the params.
         * @return the built params.
         */
        public EndpointResolverParams build() {
            return new EndpointResolverParams(map, operationName);
        }

        /**
         * Put a typed property on the params.
         *
         * @param property Property to set.
         * @param value    Value to associate with the property.
         * @return the builder.
         * @param <T> Value type.
         */
        public <T> Builder putProperty(EndpointProperty<T> property, T value) {
            map.put(property, value);
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
