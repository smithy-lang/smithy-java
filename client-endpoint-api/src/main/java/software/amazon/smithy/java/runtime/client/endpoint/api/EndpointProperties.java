/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable collection of endpoint properties used for endpoint resolution.
 */
public final class EndpointProperties {

    private static final EndpointProperties EMPTY = EndpointProperties.builder().build();
    private final Map<EndpointProperty<?>, ?> properties;

    private EndpointProperties(Map<EndpointProperty<?>, ?> properties) {
        this.properties = properties;
    }

    /**
     * Get an empty EndpointProperties object.
     *
     * @return the empty EndpointProperties.
     */
    public static EndpointProperties empty() {
        return EMPTY;
    }

    /**
     * Creates a builder used to build {@link EndpointProperties}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the value of a property.
     *
     * @param property Property to get.
     * @return the value or null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(EndpointProperty<T> property) {
        return (T) properties.get(property);
    }

    /**
     * Get the properties that are set on the EndpointProperties object.
     *
     * @return the properties.
     */
    public Set<EndpointProperty<?>> properties() {
        return properties.keySet();
    }

    /**
     * Create a new builder using the same properties as this object.
     *
     * @return the created builder.
     */
    public Builder toBuilder() {
        var builder = builder();
        builder.properties.putAll(properties);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EndpointProperties that = (EndpointProperties) o;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    /**
     * Creates an {@link EndpointProperties}.
     */
    public static final class Builder {
        private Map<EndpointProperty<?>, Object> properties = new HashMap<>();

        private Builder() {
        }

        /**
         * Create the {@code EndpointProperties} object.
         *
         * @return the built EndpointProperties object.
         */
        public EndpointProperties build() {
            var copy = properties;
            this.properties = new HashMap<>();
            return new EndpointProperties(copy);
        }

        /**
         * Put a strongly typed property on the builder.
         *
         * @param property Property to set.
         * @param value    Value to associate with the property.
         * @return the builder.
         * @param <T> Value type.
         */
        public <T> Builder put(EndpointProperty<T> property, T value) {
            properties.put(property, value);
            return this;
        }
    }
}
