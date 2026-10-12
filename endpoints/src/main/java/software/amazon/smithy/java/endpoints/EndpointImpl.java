/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.endpoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.io.uri.SmithyUri;

final class EndpointImpl implements Endpoint {

    private final SmithyUri uri;
    private final List<EndpointAuthScheme> authSchemes;
    private final Map<Context.Key<?>, Object> properties;

    private EndpointImpl(Builder builder) {
        this.uri = Objects.requireNonNull(builder.uri);
        if (builder.authSchemes != null) {
            this.authSchemes = Collections.unmodifiableList(builder.authSchemes);
        } else if (builder.authScheme != null) {
            this.authSchemes = List.of(builder.authScheme);
        } else {
            this.authSchemes = List.of();
        }
        this.properties = builder.properties == null ? Map.of() : Collections.unmodifiableMap(builder.properties);
        // Clear out the builder, making this class immutable and the builder still reusable.
        builder.authScheme = null;
        builder.authSchemes = null;
        builder.properties = null;
    }

    @Override
    public SmithyUri uri() {
        return uri;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T property(Context.Key<T> property) {
        return (T) properties.get(property);
    }

    @Override
    public Set<Context.Key<?>> properties() {
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

    @Override
    public String toString() {
        return "Endpoint{uri=" + uri + ", authSchemes=" + authSchemes + ", properties=" + properties + '}';
    }

    static final class Builder implements Endpoint.Builder {

        private SmithyUri uri;
        /**
         * The single auth scheme an endpoint almost always has, held without a list.
         *
         * <p>Going straight to {@code new ArrayList<>()} cost three objects for one element: the list,
         * the capacity-10 {@code Object[]} its first {@code add} grows into, and the unmodifiable wrapper.
         * That was ~31% of the allocation an S3 endpoint resolution did. {@code authSchemes} is only
         * allocated once a second scheme actually arrives.
         */
        private EndpointAuthScheme authScheme;
        private List<EndpointAuthScheme> authSchemes;
        private Map<Context.Key<?>, Object> properties;

        @Override
        public Builder uri(SmithyUri uri) {
            this.uri = uri;
            return this;
        }

        @Override
        public Builder addAuthScheme(EndpointAuthScheme authScheme) {
            if (this.authSchemes != null) {
                this.authSchemes.add(authScheme);
            } else if (this.authScheme == null) {
                this.authScheme = authScheme;
            } else {
                this.authSchemes = new ArrayList<>(4);
                this.authSchemes.add(this.authScheme);
                this.authSchemes.add(authScheme);
                this.authScheme = null;
            }
            return this;
        }

        @Override
        public <T> Builder putProperty(Context.Key<T> property, T value) {
            if (this.properties == null) {
                this.properties = new HashMap<>();
            }
            properties.put(property, value);
            return this;
        }

        @Override
        public Endpoint build() {
            return new EndpointImpl(this);
        }
    }
}
