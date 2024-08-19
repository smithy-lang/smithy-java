/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable collection of auth-related properties used for signing, identity resolution, etc.
 */
public final class AuthProperties {

    private static final AuthProperties EMPTY = AuthProperties.builder().build();
    private final Map<AuthProperty<?>, ?> properties;

    private AuthProperties(Map<AuthProperty<?>, ?> properties) {
        this.properties = properties;
    }

    /**
     * Get an empty AuthProperties object.
     *
     * @return the empty AuthProperties.
     */
    public static AuthProperties empty() {
        return EMPTY;
    }

    /**
     * Creates a builder used to build {@link AuthProperties}.
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
    public <T> T get(AuthProperty<T> property) {
        return (T) properties.get(property);
    }

    /**
     * Get the properties that are set on the AuthProperties object.
     *
     * @return the properties.
     */
    public Set<AuthProperty<?>> properties() {
        return properties.keySet();
    }

    /**
     * Creates a new set of {@link AuthProperties} that is the merged result of these properties and another set of
     * properties.
     *
     * @param other Auth properties to merge with this instance.
     * @return New auth properties that use the default.
     */
    public AuthProperties merge(AuthProperties other) {
        var builder = toBuilder();
        for (var property : other.properties()) {
            copyPropertyToBuilder(property, other, builder);
        }
        return builder.build();
    }

    private static <T> void copyPropertyToBuilder(AuthProperty<T> key, AuthProperties src, Builder dst) {
        dst.put(key, src.get(key));
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
        AuthProperties that = (AuthProperties) o;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    /**
     * Creates an {@link AuthProperties}.
     */
    public static final class Builder {
        private Map<AuthProperty<?>, Object> properties = new HashMap<>();

        private Builder() {
        }

        /**
         * Create the {@code AuthProperties} object.
         *
         * @return the built AuthProperties object.
         */
        public AuthProperties build() {
            var copy = properties;
            this.properties = new HashMap<>();
            return new AuthProperties(copy);
        }

        /**
         * Put a strongly typed property on the builder.
         *
         * @param property Property to set.
         * @param value    Value to associate with the property.
         * @return the builder.
         * @param <T> Value type.
         */
        public <T> Builder put(AuthProperty<T> property, T value) {
            properties.put(property, value);
            return this;
        }
    }
}
