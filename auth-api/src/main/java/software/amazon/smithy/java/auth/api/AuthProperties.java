/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.context.Context;

/**
 * An immutable collection of auth-related properties used for signing, identity resolution, etc.
 */
public final class AuthProperties {

    private static final AuthProperties EMPTY = AuthProperties.builder().build();
    private final Map<Context.Key<?>, ?> properties;

    private AuthProperties(Map<Context.Key<?>, ?> properties) {
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
    public <T> T get(Context.Key<T> property) {
        return (T) properties.get(property);
    }

    /**
     * Get the value of a property or a default value.
     *
     * @param property Property to get.
     * @param defaultValue Default to return if property is not found.
     * @return the value or default if not found.
     */
    public <T> T getOrDefault(Context.Key<T> property, T defaultValue) {
        T value = get(property);
        return value != null ? value : defaultValue;
    }

    /**
     * Get the value of a property.
     *
     * @param property Property to get.
     * @return the value of the property.
     * @throws ExpectationNotMetException if the property is not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T expect(Context.Key<T> property) {
        var value = properties.get(property);
        if (value == null) {
            throw new ExpectationNotMetException("Could not find expected property: " + property);
        }
        return (T) value;
    }

    /**
     * Get the properties that are set on the AuthProperties object.
     *
     * @return the properties.
     */
    public Set<Context.Key<?>> properties() {
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
        if (other.properties.isEmpty()) {
            return this;
        }
        if (this.properties.isEmpty()) {
            return other;
        }
        var builder = toBuilder();
        for (var property : other.properties()) {
            copyPropertyToBuilder(property, other, builder);
        }
        return builder.build();
    }

    private static <T> void copyPropertyToBuilder(Context.Key<T> key, AuthProperties src, Builder dst) {
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
        private Map<Context.Key<?>, Object> properties = new HashMap<>();

        private Builder() {}

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
        public <T> Builder put(Context.Key<T> property, T value) {
            properties.put(property, value);
            return this;
        }
    }
}
