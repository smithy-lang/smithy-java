/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

final class EndpointAuthSchemeImpl implements EndpointAuthScheme {
    private final String authSchemeId;
    private final Map<EndpointKey<?>, Object> attributes;

    EndpointAuthSchemeImpl(Builder builder) {
        this.authSchemeId = Objects.requireNonNull(builder.authSchemeId, "authSchemeId is null");
        this.attributes = Map.copyOf(builder.attributes);
    }

    @Override
    public String authSchemeId() {
        return authSchemeId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T attribute(EndpointKey<T> key) {
        return (T) attributes.get(key);
    }

    @Override
    public Iterator<EndpointKey<?>> attributeKeys() {
        return attributes.keySet().iterator();
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
        return Objects.equals(authSchemeId, that.authSchemeId) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authSchemeId, attributes);
    }

    static final class Builder implements EndpointAuthScheme.Builder {
        String authSchemeId;
        final Map<EndpointKey<?>, Object> attributes = new HashMap<>();

        @Override
        public EndpointAuthScheme.Builder authSchemeId(String authSchemeId) {
            this.authSchemeId = authSchemeId;
            return this;
        }

        @Override
        public <T> EndpointAuthScheme.Builder putAttribute(EndpointKey<T> key, T value) {
            attributes.put(key, value);
            return this;
        }

        @Override
        public EndpointAuthScheme build() {
            return new EndpointAuthSchemeImpl(this);
        }
    }
}
