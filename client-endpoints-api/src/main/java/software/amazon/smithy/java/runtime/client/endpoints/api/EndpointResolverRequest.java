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
 * Encapsulates endpoint resolver request parameters.
 */
public final class EndpointResolverRequest {

    private final String operationName;
    private final Map<EndpointKey<?>, Object> immutableMap;

    private EndpointResolverRequest(Map<EndpointKey<?>, Object> map, String operationName) {
        this.immutableMap = new HashMap<>(map);
        this.operationName = Objects.requireNonNull(operationName, "operationName is null");
    }

    /**
     * Create a new {@link EndpointResolverRequest}.
     *
     * @return the created request.
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
     * Get all the keys registered with the request.
     *
     * @return the keys of the request.
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
        EndpointResolverRequest request = (EndpointResolverRequest) o;
        return Objects.equals(operationName, request.operationName)
            && Objects.equals(immutableMap, request.immutableMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationName, immutableMap);
    }

    /**
     * Create a builder from this request.
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
     * Builder used to create and {@link EndpointResolverRequest}.
     */
    public static final class Builder {

        private String operationName;
        private final Map<EndpointKey<?>, Object> map = new HashMap<>();

        /**
         * Build the request.
         * @return the built request.
         */
        public EndpointResolverRequest build() {
            return new EndpointResolverRequest(map, operationName);
        }

        /**
         * Put an attribute on the request.
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
