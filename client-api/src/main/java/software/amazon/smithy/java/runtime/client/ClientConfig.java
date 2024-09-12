/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.client.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointResolver;
import software.amazon.smithy.java.runtime.client.interceptors.ClientInterceptor;

/**
 * An immutable representation of configurations of a Client.
 *
 * <p>It has well-defined configuration elements that every Client needs. For extensible parts of a
 * Client that may need additional configuration, type safe configuration can be included using
 * {@link Context.Key}.
 */
public final class ClientConfig {

    private static final AuthScheme<Object, Identity> NO_AUTH_AUTH_SCHEME = AuthScheme.noAuthAuthScheme();

    private final ClientTransport<?, ?> transport;
    private final ClientProtocol<?, ?> protocol;
    private final EndpointResolver endpointResolver;
    private final List<ClientInterceptor> interceptors;
    private final List<AuthScheme<?, ?>> supportedAuthSchemes;
    private final AuthSchemeResolver authSchemeResolver;
    private final List<IdentityResolver<?>> identityResolvers;
    private final Context context;

    private ClientConfig(Builder builder) {
        this.transport = Objects.requireNonNull(builder.transport, "transport cannot be null");
        this.protocol = Objects.requireNonNull(builder.protocol, "protocol cannot be null");
        validateProtocolAndTransport(protocol, transport);

        this.endpointResolver = Objects.requireNonNull(builder.endpointResolver, "endpointResolver is null");

        this.interceptors = List.copyOf(builder.interceptors);

        // By default, support NoAuthAuthScheme
        List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        supportedAuthSchemes.add(NO_AUTH_AUTH_SCHEME);
        supportedAuthSchemes.addAll(builder.supportedAuthSchemes);
        this.supportedAuthSchemes = List.copyOf(supportedAuthSchemes);

        this.authSchemeResolver = Objects.requireNonNullElse(builder.authSchemeResolver, AuthSchemeResolver.DEFAULT);
        this.identityResolvers = List.copyOf(builder.identityResolvers);

        this.context = Context.unmodifiableCopy(builder.context);
    }

    public ClientTransport<?, ?> transport() {
        return transport;
    }

    public ClientProtocol<?, ?> protocol() {
        return protocol;
    }

    public EndpointResolver endpointResolver() {
        return endpointResolver;
    }

    public List<ClientInterceptor> interceptors() {
        return interceptors;
    }

    public List<AuthScheme<?, ?>> supportedAuthSchemes() {
        return supportedAuthSchemes;
    }

    public AuthSchemeResolver authSchemeResolver() {
        return authSchemeResolver;
    }

    public List<IdentityResolver<?>> identityResolvers() {
        return identityResolvers;
    }

    public Context context() {
        return context;
    }

    /**
     * Create a new builder to build {@link ClientConfig}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder builder = builder()
            .transport(transport)
            .protocol(protocol)
            .endpointResolver(endpointResolver)
            .authSchemeResolver(authSchemeResolver)
            .identityResolvers(identityResolvers);
        interceptors.forEach(builder::addInterceptor);
        supportedAuthSchemes.forEach(builder::putSupportedAuthSchemes);
        builder.putAllConfig(context);
        return builder;
    }

    /**
     * Ensures that the given protocol and transport are compatible by comparing their request and response classes.
     *
     * @param protocol Protocol to check.
     * @param transport Transport to check.
     * @throws IllegalStateException if the protocol and transport use different request or response classes.
     */
    public static void validateProtocolAndTransport(ClientProtocol<?, ?> protocol, ClientTransport<?, ?> transport) {
        if (protocol.requestClass() != transport.requestClass()) {
            throw new IllegalStateException("Protocol request != transport: " + protocol + " vs " + transport);
        } else if (protocol.responseClass() != transport.responseClass()) {
            throw new IllegalStateException("Protocol response != transport: " + protocol + " vs " + transport);
        }
    }

    /**
     * Static builder for ClientConfiguration.
     */
    public static final class Builder {
        private ClientTransport<?, ?> transport;
        private ClientProtocol<?, ?> protocol;
        private EndpointResolver endpointResolver;
        private final List<ClientInterceptor> interceptors = new ArrayList<>();
        private AuthSchemeResolver authSchemeResolver;
        private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        private final List<IdentityResolver<?>> identityResolvers = new ArrayList<>();
        private final Context context = Context.create();

        // TODO: Add getters for each, so that a ClientPlugin can read the existing values.

        /**
         * Set the transport used to send requests.
         *
         * @param transport Client transport used to send requests.
         * @return Returns the builder.
         */
        public Builder transport(ClientTransport<?, ?> transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Set the protocol to use when sending requests.
         *
         * @param protocol Client protocol used to send requests.
         * @return Returns the builder.
         */
        public Builder protocol(ClientProtocol<?, ?> protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Set the resolver used to resolve endpoints.
         *
         * @param endpointResolver Endpoint resolver to use to resolve endpoints.
         * @return Returns the endpoint resolver.
         */
        public Builder endpointResolver(EndpointResolver endpointResolver) {
            this.endpointResolver = endpointResolver;
            return this;
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder endpoint(Endpoint endpoint) {
            return endpointResolver(EndpointResolver.staticEndpoint(endpoint));
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder endpoint(URI endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder endpoint(String endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Add an interceptor to the client.
         *
         * @param interceptor Interceptor to add.
         * @return the builder.
         */
        public Builder addInterceptor(ClientInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        /**
         * Set the auth scheme resolver of the client.
         *
         * @param authSchemeResolver Auth scheme resolver to use.
         * @return the builder.
         */
        public Builder authSchemeResolver(AuthSchemeResolver authSchemeResolver) {
            this.authSchemeResolver = authSchemeResolver;
            return this;
        }

        /**
         * Add supported auth schemes to the client that works in tandem with the {@link AuthSchemeResolver}.
         *
         * <p>If the scheme ID is already supported, it will be replaced by the provided auth scheme.
         *
         * @param authSchemes Auth schemes to add.
         * @return the builder.
         */
        public Builder putSupportedAuthSchemes(AuthScheme<?, ?>... authSchemes) {
            supportedAuthSchemes.addAll(Arrays.asList(authSchemes));
            return this;
        }

        /**
         * Add identity resolvers to the client.
         *
         * @param identityResolvers Identity resolvers to add.
         * @return the builder.
         */
        public Builder addIdentityResolver(IdentityResolver<?>... identityResolvers) {
            this.identityResolvers.addAll(Arrays.asList(identityResolvers));
            return this;
        }

        /**
         * Set the identity resolvers of the client.
         *
         * @param identityResolvers Identity resolvers to set.
         * @return the builder.
         */
        public Builder identityResolvers(List<IdentityResolver<?>> identityResolvers) {
            this.identityResolvers.clear();
            this.identityResolvers.addAll(identityResolvers);
            return this;
        }

        /**
         * Put a strongly typed configuration on the builder. If a key was already present, it is overridden.
         *
         * @param key Configuration key.
         * @param value Value to associate with the key.
         * @return the builder.
         * @param <T> Value type.
         */
        public <T> Builder putConfig(Context.Key<T> key, T value) {
            context.put(key, value);
            return this;
        }

        /**
         * Put a strongly typed configuration on the builder, if not already present.
         *
         * @param key Configuration key.
         * @param value Value to associate with the key.
         * @return the builder.
         * @param <T> Value type.
         */
        public <T> Builder putConfigIfAbsent(Context.Key<T> key, T value) {
            context.putIfAbsent(key, value);
            return this;
        }

        /**
         * Put all the strongly typed configuration from the given Context. If a key was already present, it is
         * overridden.
         *
         * @param context Context containing all the configuration to put.
         * @return the builder.
         */
        public Builder putAllConfig(Context context) {
            this.context.putAll(context);
            return this;
        }

        /**
         * Creates the client configuration.
         *
         * @return the created client configuration.
         */
        public ClientConfig build() {
            return new ClientConfig(this);
        }
    }
}
