/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class EndpointImpl implements Endpoint {

    private final URI uri;
    private final List<EndpointAuthScheme> authSchemes;
    private final Map<EndpointKey<?>, Object> attributes;

    private EndpointImpl(Builder builder) {
        this.uri = Objects.requireNonNull(builder.uri);
        this.authSchemes = List.copyOf(builder.authSchemes);
        this.attributes = Map.copyOf(builder.attributes);
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T endpointAttribute(EndpointKey<T> key) {
        return (T) attributes.get(key);
    }

    @Override
    public Iterator<EndpointKey<?>> endpointAttributeKeys() {
        return attributes.keySet().iterator();
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
            && Objects.equals(attributes, endpoint.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, authSchemes, attributes);
    }

    static final class Builder implements Endpoint.Builder {

        private URI uri;
        private final List<EndpointAuthScheme> authSchemes = new ArrayList<>();
        final Map<EndpointKey<?>, Object> attributes = new HashMap<>();

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
        public <T> Builder putAttribute(EndpointKey<T> key, T value) {
            attributes.put(key, value);
            return this;
        }

        @Override
        public Endpoint build() {
            return new EndpointImpl(this);
        }
    }
}
