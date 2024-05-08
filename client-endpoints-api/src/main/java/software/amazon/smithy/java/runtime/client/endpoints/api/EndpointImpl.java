/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class EndpointImpl implements Endpoint {

    private final URI uri;
    private final List<EndpointAuthScheme> authSchemes;
    private final Map<EndpointProperty<?>, Object> properties;

    private EndpointImpl(Builder builder) {
        this.uri = Objects.requireNonNull(builder.uri);
        this.authSchemes = List.copyOf(builder.authSchemes);
        this.properties = Map.copyOf(builder.properties);
    }

    @Override
    public URI uri() {
        return uri;
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
    public List<EndpointAuthScheme> authSchemes() {
        return authSchemes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EndpointImpl endpoint = (EndpointImpl) o;
        return Objects.equals(uri, endpoint.uri) && Objects.equals(authSchemes, endpoint.authSchemes)
            && Objects.equals(properties, endpoint.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, authSchemes, properties);
    }

    static final class Builder implements Endpoint.Builder {

        private URI uri;
        private final List<EndpointAuthScheme> authSchemes = new ArrayList<>();
        final Map<EndpointProperty<?>, Object> properties = new HashMap<>();

        @Override
        public Builder uri(URI uri) {
            this.uri = uri;
            return this;
        }

        @Override
        public Builder uri(String uri) {
            try {
                return uri(new URI(uri));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Builder addAuthScheme(EndpointAuthScheme authScheme) {
            this.authSchemes.add(authScheme);
            return this;
        }

        @Override
        public <T> Builder putProperty(EndpointProperty<T> property, T value) {
            properties.put(property, value);
            return this;
        }

        @Override
        public Endpoint build() {
            return new EndpointImpl(this);
        }
    }
}
