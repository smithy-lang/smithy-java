/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class EndpointAuthSchemeImpl implements EndpointAuthScheme {
    private final String authSchemeId;
    private final Map<EndpointProperty<?>, Object> properties;

    EndpointAuthSchemeImpl(Builder builder) {
        this.authSchemeId = Objects.requireNonNull(builder.authSchemeId, "authSchemeId is null");
        this.properties = Map.copyOf(builder.properties);
    }

    @Override
    public String authSchemeId() {
        return authSchemeId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T property(EndpointProperty<T> property) {
        return (T) properties.get(property);
    }

    @Override
    public Set<EndpointProperty<?>> properties() {
        return properties.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EndpointAuthSchemeImpl that = (EndpointAuthSchemeImpl) o;
        return Objects.equals(authSchemeId, that.authSchemeId) && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authSchemeId, properties);
    }

    static final class Builder implements EndpointAuthScheme.Builder {
        String authSchemeId;
        final Map<EndpointProperty<?>, Object> properties = new HashMap<>();

        @Override
        public EndpointAuthScheme.Builder authSchemeId(String authSchemeId) {
            this.authSchemeId = authSchemeId;
            return this;
        }

        @Override
        public <T> EndpointAuthScheme.Builder putProperty(EndpointProperty<T> property, T value) {
            properties.put(property, value);
            return this;
        }

        @Override
        public EndpointAuthScheme build() {
            return new EndpointAuthSchemeImpl(this);
        }
    }
}
