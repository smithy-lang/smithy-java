/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.retries.api.RetryStrategy;

/**
 * An immutable representation of configurations of a {@link Client}.
 *
 * <p>It has well-defined configuration elements that every {@link Client} needs. For extensible parts of a
 * {@link Client} that may need additional configuration, type safe configuration can be included using
 * {@link Context.Key}.
 *
 * <p>When built, the ClientTransport resolved for the protocol will apply the {@link ClientTransport#configureClient}
 * method after other plugins are applied.
 */
public final class ClientConfig {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientConfig.class);
    private static final List<ClientTransportFactory<?, ?>> TRANSPORT_FACTORIES = ClientTransportFactory
            .load(ClientConfig.class.getClassLoader());
    private static final AuthScheme<Object, Identity> NO_AUTH_AUTH_SCHEME = AuthScheme.noAuthAuthScheme();

    private final Builder originalBuilder;
    private final ClientTransport<?, ?> transport;
    private final ClientProtocol<?, ?> protocol;
    private final EndpointResolver endpointResolver;
    private final List<ClientInterceptor> interceptors;
    private final List<AuthScheme<?, ?>> supportedAuthSchemes;
    private final AuthSchemeResolver authSchemeResolver;
    private final List<IdentityResolver<?>> identityResolvers;
    private final Context context;
    private final Set<Class<? extends ClientPlugin>> appliedPlugins;
    private final ApiService service;

    private final RetryStrategy retryStrategy;
    private final String retryScope;

    private ClientConfig(Builder builder) {
        // Transports can change between builders to toBuilder. Transports can modify the builder when they're applied.
        // To prevent a previous configuration meant for one transport to impact a future configuration, we create a
        // copy of the original builder. We also don't want to apply the transport modifications multiple times.
        // This builder is used in toBuilder.
        this.originalBuilder = builder.copyBuilder();

        // Ensure the transport was resolved and applied as a plugin.
        // When using a Client, transport is applied before build is called to let user-defined plugins supersede
        // transport-applied plugins. This is performed here in case configs are created manually.
        builder.resolveTransport();

        this.protocol = builder.protocol;
        this.transport = builder.transport;
        ClientPipeline.validateProtocolAndTransport(protocol, transport);

        // Use an explicitly given resolver if one was set.
        if (builder.endpointResolver != null) {
            this.endpointResolver = builder.endpointResolver;
        } else {
            // Use a custom endpoint and static endpoint resolver if a custom endpoint was given.
            // Things like the Smithy rules engine based resolver look for this property to know if a custom endpoint
            // was provided in this manner.
            var customEndpoint = builder.context.get(ClientContext.CUSTOM_ENDPOINT);
            if (customEndpoint == null) {
                throw new NullPointerException("Both endpointResolver and ClientContext.CUSTOM_ENDPOINT are not set");
            }
            this.endpointResolver = EndpointResolver.staticEndpoint(customEndpoint);
        }

        this.interceptors = List.copyOf(builder.interceptors);

        // By default, support NoAuthAuthScheme
        List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        supportedAuthSchemes.add(NO_AUTH_AUTH_SCHEME);
        supportedAuthSchemes.addAll(builder.supportedAuthSchemes);
        this.supportedAuthSchemes = Collections.unmodifiableList(supportedAuthSchemes);

        this.authSchemeResolver = Objects.requireNonNullElse(builder.authSchemeResolver, AuthSchemeResolver.DEFAULT);
        this.identityResolvers = List.copyOf(builder.identityResolvers);

        this.retryStrategy = builder.retryStrategy;
        this.retryScope = builder.retryScope;

        this.context = Context.unmodifiableCopy(builder.context);
        this.appliedPlugins = Collections.unmodifiableSet(new LinkedHashSet<>(builder.appliedPlugins));
        this.service = Objects.requireNonNull(builder.service, "Missing required service schema");
    }

    /**
     * Search for a transport service provider that is compatible with the provided protocol.
     */
    private static ClientTransport<?, ?> discoverTransport(ClientProtocol<?, ?> protocol) {
        for (var factory : TRANSPORT_FACTORIES) {
            // Find the first applicable transport factory
            if (factory.messageExchange().equals(protocol.messageExchange())) {
                return factory.createTransport();
            }
        }
        throw new IllegalArgumentException(
                "No compatible transport found for protocol '" + protocol + "'. "
                        + "Add a compatible ClientTransportFactory Service provider to the classpath, "
                        + "or add a compatible transport to the client builder.");
    }

    /**
     * Get the ordered set of plugins that were applied to the configuration.
     *
     * @return the applied plugins.
     */
    public Set<Class<? extends ClientPlugin>> appliedPlugins() {
        return appliedPlugins;
    }

    /**
     * Get the configured service schema.
     *
     * @return the service schema.
     */
    public ApiService service() {
        return service;
    }

    /**
     * @return Transport for client to use to send data to an endpoint.
     */
    public ClientTransport<?, ?> transport() {
        return transport;
    }

    /**
     * @return Protocol for client to use for request and response serialization and deserialization.
     */
    public ClientProtocol<?, ?> protocol() {
        return protocol;
    }

    /**
     * @return EndpointResolver to use to resolve an endpoint for an operation.
     */
    public EndpointResolver endpointResolver() {
        return endpointResolver;
    }

    /**
     * @return Interceptors configured to hook into the client's request execution pipeline.
     */
    public List<ClientInterceptor> interceptors() {
        return interceptors;
    }

    /**
     * @return Authentication schemes supported by the client.
     */
    public List<AuthScheme<?, ?>> supportedAuthSchemes() {
        return supportedAuthSchemes;
    }

    /**
     * @return Resolver to use to resolve the authentication scheme that should be used to sign a request.
     */
    public AuthSchemeResolver authSchemeResolver() {
        return authSchemeResolver;
    }

    /**
     * @return Resolvers to use to resolve an identity for authentication.
     */
    public List<IdentityResolver<?>> identityResolvers() {
        return identityResolvers;
    }

    /**
     * @return Context to use
     */
    public Context context() {
        return context;
    }

    RetryStrategy retryStrategy() {
        return retryStrategy;
    }

    String retryScope() {
        return retryScope;
    }

    /**
     * Create a new builder to build {@link ClientConfig}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convert to a builder.
     *
     * @return the builder based on this configuration.
     */
    public Builder toBuilder() {
        return originalBuilder.copyBuilder();
    }

    /**
     * Create a copy of the ClientConfig after applying overrides.
     *
     * @param overrideConfig The overrides to apply.
     * @return copy of ClientConfig with overrides applied.
     */
    public ClientConfig withRequestOverride(RequestOverrideConfig overrideConfig) {
        Objects.requireNonNull(overrideConfig, "overrideConfig cannot be null");
        Builder builder = toBuilder();
        applyOverrides(builder, overrideConfig);
        for (ClientPlugin plugin : overrideConfig.plugins()) {
            builder.applyPlugin(plugin);
        }
        return builder.build();
    }

    private void applyOverrides(Builder builder, RequestOverrideConfig overrideConfig) {
        if (overrideConfig.protocol() != null) {
            builder.protocol(overrideConfig.protocol());
        }
        if (overrideConfig.endpointResolver() != null) {
            builder.endpointResolver(overrideConfig.endpointResolver());
        }
        if (overrideConfig.interceptors() != null) {
            overrideConfig.interceptors().forEach(builder::addInterceptor);
        }
        if (overrideConfig.authSchemeResolver() != null) {
            builder.authSchemeResolver(overrideConfig.authSchemeResolver());
        }
        if (overrideConfig.supportedAuthSchemes() != null) {
            overrideConfig.supportedAuthSchemes().forEach(builder::putSupportedAuthSchemes);
        }
        if (overrideConfig.identityResolvers() != null) {
            overrideConfig.identityResolvers().forEach(builder::addIdentityResolver);
        }
        if (overrideConfig.retryStrategy() != null) {
            builder.retryStrategy(overrideConfig.retryStrategy());
        }
        if (overrideConfig.retryScope() != null) {
            builder.retryScope(overrideConfig.retryScope());
        }

        builder.putAllConfig(overrideConfig.context());
    }

    /**
     * Static builder for ClientConfiguration.
     */
    public static final class Builder {
        private ApiService service;
        private ClientTransport<?, ?> transport;
        private ClientProtocol<?, ?> protocol;
        private EndpointResolver endpointResolver;
        private final List<ClientInterceptor> interceptors = new ArrayList<>();
        private AuthSchemeResolver authSchemeResolver;
        private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        private final List<IdentityResolver<?>> identityResolvers = new ArrayList<>();
        private final Context context = Context.create();
        private RetryStrategy retryStrategy;
        private String retryScope;
        private final Set<Class<? extends ClientPlugin>> appliedPlugins = new LinkedHashSet<>();

        private Builder copyBuilder() {
            Builder builder = new Builder();
            builder.service = service;
            builder.transport = transport;
            builder.protocol = protocol;
            builder.endpointResolver = endpointResolver;
            builder.interceptors.addAll(interceptors);
            builder.authSchemeResolver = authSchemeResolver;
            builder.supportedAuthSchemes.addAll(supportedAuthSchemes);
            builder.identityResolvers.addAll(identityResolvers);
            context.copyTo(builder.context);
            builder.retryStrategy = retryStrategy;
            builder.retryScope = retryScope;
            builder.appliedPlugins.addAll(appliedPlugins);
            return builder;
        }

        /**
         * @return Get the service schema.
         */
        public ApiService service() {
            return service;
        }

        /**
         * @return Get the transport.
         */
        public ClientTransport<?, ?> transport() {
            return transport;
        }

        /**
         * @return Get the protocol.
         */
        public ClientProtocol<?, ?> protocol() {
            return protocol;
        }

        /**
         * @return Get the endpoint resolver.
         */
        public EndpointResolver endpointResolver() {
            return endpointResolver;
        }

        /**
         * @return Get the interceptors.
         */
        public List<ClientInterceptor> interceptors() {
            return interceptors;
        }

        /**
         * @return Get the auth scheme resolver.
         */
        public AuthSchemeResolver authSchemeResolver() {
            return authSchemeResolver;
        }

        /**
         * @return Get the supported auth schemes.
         */
        public List<AuthScheme<?, ?>> supportedAuthSchemes() {
            return supportedAuthSchemes;
        }

        /**
         * @return Get the identity resolvers.
         */
        public List<IdentityResolver<?>> identityResolvers() {
            return identityResolvers;
        }

        /**
         * @return Get the context.
         */
        public Context context() {
            return context;
        }

        /**
         * @return Get the retry strategy.
         */
        public RetryStrategy retryStrategy() {
            return retryStrategy;
        }

        /**
         * @return Get the retry scope.
         */
        public String retryScope() {
            return retryScope;
        }

        /**
         * Set the service schema.
         *
         * @param service The service schema.
         * @return Returns the builder.
         */
        public Builder service(ApiService service) {
            this.service = service;
            return this;
        }

        /**
         * Set the transport used to send requests.
         *
         * @param transport Client transport used to send requests.
         * @return Returns the builder.
         */
        public Builder transport(ClientTransport<?, ?> transport) {
            this.transport = transport;
            applyPlugin(transport);
            return this;
        }

        /**
         * If no transport was provided, then resolve a transport now based on the selected protocol, and apply the
         * transport as a plugin if it has not yet been applied.
         *
         * @return the builder.
         * @throws NullPointerException if not protocol is set.
         * @throws IllegalArgumentException if no transport could be resolved.
         */
        public Builder resolveTransport() {
            Objects.requireNonNull(protocol, "protocol must be set to resolve a transport");
            if (transport == null) {
                transport(discoverTransport(protocol));
            }
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
        Builder putAllConfig(Context context) {
            context.copyTo(this.context);
            return this;
        }

        /**
         * Set a retry strategy to use.
         *
         * @param retryStrategy Retry strategy to use.
         * @return the builder.
         * @see Client.Builder#retryStrategy(RetryStrategy)
         */
        public Builder retryStrategy(RetryStrategy retryStrategy) {
            this.retryStrategy = retryStrategy;
            return this;
        }

        /**
         * Set a retry scope to use with retries.
         *
         * @param retryScope The retry scope to set (e.g., an ARN).
         * @return the builder.
         */
        public Builder retryScope(String retryScope) {
            this.retryScope = retryScope;
            return this;
        }

        /**
         * Applies a plugin to the configuration and tracks the plugin class as applied.
         *
         * @param plugin Plugin to apply.
         * @return the updated builder.
         */
        public Builder applyPlugin(ClientPlugin plugin) {
            if (appliedPlugins.contains(plugin.getClass())) {
                LOGGER.debug("Skipping already applied client plugin: {}", plugin.getClass());
            } else {
                LOGGER.debug("Applying client plugin: {}", plugin.getClass());
                appliedPlugins.add(plugin.getClass());
                plugin.configureClient(this);
            }
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
