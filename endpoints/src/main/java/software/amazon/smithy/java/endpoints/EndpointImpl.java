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
        this.authSchemes = builder.authSchemes != null
                ? Collections.unmodifiableList(builder.authSchemes)
                : builder.authScheme == null ? List.of() : List.of(builder.authScheme);
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
        private EndpointAuthScheme authScheme;
        private List<EndpointAuthScheme> authSchemes;
        private Map<Context.Key<?>, Object> properties;
        private EndpointImpl cachedEndpoint;

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
                this.authSchemes = new ArrayList<>();
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
            if (properties == null && authSchemes == null && cachedEndpoint != null
                    && cachedEndpoint.uri.equals(uri)) {
                if (authScheme == null && cachedEndpoint.authSchemes.isEmpty()
                        || authScheme != null
                                && cachedEndpoint.authSchemes.size() == 1
                                && cachedEndpoint.authSchemes.getFirst().equals(authScheme)) {
                    authScheme = null;
                    return cachedEndpoint;
                }
            }
            EndpointImpl result = new EndpointImpl(this);
            if (result.properties.isEmpty() && result.authSchemes.size() <= 1) {
                cachedEndpoint = result;
            }
            return result;
        }
    }
}
