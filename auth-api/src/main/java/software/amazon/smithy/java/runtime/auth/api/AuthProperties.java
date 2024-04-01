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
    private final Map<AuthProperty<?>, Object> attributes;

    private AuthProperties(Map<AuthProperty<?>, Object> attributes) {
        this.attributes = attributes;
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
     * Get a property.
     *
     * @param key   property key to get by exact reference identity.
     * @param <T>   Returns the value, or null if not present.
     * @return Returns the nullable property value.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(AuthProperty<T> key) {
        return (T) attributes.get(key);
    }

    /**
     * Get the set of property keys that are set on the AuthProperties object.
     *
     * @return the set property keys.
     */
    public Set<AuthProperty<?>> keys() {
        return attributes.keySet();
    }

    /**
     * Create a new builder using the same properties as this object.
     *
     * @return the created builder.
     */
    public Builder toBuilder() {
        var builder = builder();
        builder.attributes.putAll(attributes);
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
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }

    /**
     * Creates an {@link AuthProperties}.
     */
    public static final class Builder {
        private Map<AuthProperty<?>, Object> attributes = new HashMap<>();

        private Builder() {
        }

        /**
         * Create the {@code AuthProperties} object.
         *
         * @return the built AuthProperties object.
         */
        public AuthProperties build() {
            var copy = attributes;
            this.attributes = new HashMap<>();
            return new AuthProperties(copy);
        }

        /**
         * Put a strongly typed property on the builder.
         *
         * @param key   AuthProperty of the property, accessed by identity.
         * @param value Value associated with the property.
         * @return the builder.
         * @param <T> value type.
         */
        public <T> Builder put(AuthProperty<T> key, T value) {
            attributes.put(key, value);
            return this;
        }
    }
}
