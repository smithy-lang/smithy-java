/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.plugins.DefaultPlugin;
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
 */
public final class ClientConfig {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientConfig.class);
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
    private final ApiService service;
    private final RetryStrategy retryStrategy;
    private final String retryScope;
    private final Set<Class<? extends ClientPlugin>> appliedPluginClasses;

    private ClientConfig(Builder builder) {
        // Collect and apply plugins, updating builder.appliedPluginClasses as we go
        builder.applyFlattenedPlugins(
                collectPlugins(builder.plugins, builder.pluginPredicate, builder.appliedPluginClasses));
        this.originalBuilder = builder.copyBuilder();
        this.appliedPluginClasses = Set.copyOf(builder.appliedPluginClasses);

        this.protocol = Objects.requireNonNull(builder.protocol, "protocol must not be null");
        this.transport = Objects.requireNonNull(builder.transport, "transport must not be null");
        ClientPipeline.validateProtocolAndTransport(protocol, transport);

        // Use an explicitly given resolver if one was set.
        if (builder.endpointResolver != null) {
            this.endpointResolver = builder.endpointResolver;
        } else {
            // Use a custom endpoint and static endpoint resolver if a custom endpoint was given.
            // Things like the Smithy rules engine based resolver look for this property to know if a custom endpoint
            // was provided in this manner.
            var customEndpoint = Objects.requireNonNull(
                    builder.context.get(ClientContext.CUSTOM_ENDPOINT),
                    "One of endpointResolver or ClientContext.CUSTOM_ENDPOINT must be set");
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
        this.service = Objects.requireNonNull(builder.service, "Missing required service schema");
    }

    private static List<ClientPlugin> collectPlugins(
            Map<Class<? extends ClientPlugin>, ClientPlugin> plugins,
            Predicate<ClientPlugin> pluginPredicate,
            Set<Class<? extends ClientPlugin>> appliedPluginClasses
    ) {
        // Flatten out plugins with children recursively, and sort plugins by phase and insertion order.
        List<ClientPlugin> flattenedPlugins = new ArrayList<>();
        for (var plugin : plugins.values()) {
            collectPluginsRecursively(plugin, pluginPredicate, appliedPluginClasses, flattenedPlugins);
        }
        flattenedPlugins.sort(ClientConfig::compareByPhase);
        return flattenedPlugins;
    }

    // Collects plugins and applies the plugin predicate so that child plugins are only attempted to be added if
    // the parent plugin is accepted. Also skips plugins whose class has already been applied.
    // Mutates appliedPluginClasses as plugins are collected.
    private static void collectPluginsRecursively(
            ClientPlugin plugin,
            Predicate<ClientPlugin> pluginPredicate,
            Set<Class<? extends ClientPlugin>> appliedPluginClasses,
            List<ClientPlugin> collector
    ) {
        Class<? extends ClientPlugin> pluginClass = plugin.getClass();

        // Skip if this plugin class has already been applied
        if (appliedPluginClasses.contains(pluginClass)) {
            LOGGER.debug("Skipping already-applied plugin class: ", pluginClass);
            return;
        }

        if (pluginPredicate.test(plugin)) {
            collector.add(plugin);
            appliedPluginClasses.add(pluginClass); // Mark as applied immediately
            for (ClientPlugin child : plugin.getChildPlugins()) {
                collectPluginsRecursively(child, pluginPredicate, appliedPluginClasses, collector);
            }
        } else {
            LOGGER.debug("Plugin predicate prevented applying plugin to ClientBuilder: ", pluginClass);
        }
    }

    private static int compareByPhase(ClientPlugin a, ClientPlugin b) {
        return a.getPluginPhase().compareTo(b.getPluginPhase());
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
     * Create a copy of the ClientConfig that applies the given request overrides.
     *
     * <p>Plugins applied to a request override are still sorted and ordered by phase just like plugins applied when
     * building up a client. However, override plugins are only ordered relative to other override plugins; that is,
     * they aren't ordered relative to already applied plugins as those have already irreversibly been applied to the
     * config.
     *
     * @param overrideConfig The overrides to apply.
     * @return copy of ClientConfig with overrides applied.
     */
    public ClientConfig withRequestOverride(RequestOverrideConfig overrideConfig) {
        Objects.requireNonNull(overrideConfig, "overrideConfig cannot be null");
        Builder builder = toBuilder();

        applyOverrides(builder, overrideConfig);

        // Apply override plugins if present. Override plugins are only ordered relative to each other, not
        // relative to constructor plugins that were already applied
        if (!overrideConfig.plugins().isEmpty()) {
            Map<Class<? extends ClientPlugin>, ClientPlugin> overridePlugins = new LinkedHashMap<>();
            for (var p : overrideConfig.plugins()) {
                overridePlugins.putIfAbsent(p.getClass(), p);
            }
            builder.applyFlattenedPlugins(collectPlugins(
                    overridePlugins,
                    builder.pluginPredicate,
                    builder.appliedPluginClasses));
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
        private Predicate<ClientPlugin> pluginPredicate = p -> true;
        private final Map<Class<? extends ClientPlugin>, ClientPlugin> plugins = new LinkedHashMap<>();
        // Mutable set that tracks which plugin classes have been applied to this builder
        private final Set<Class<? extends ClientPlugin>> appliedPluginClasses = new HashSet<>();

        public Builder() {
            plugins.put(DefaultPlugin.class, DefaultPlugin.INSTANCE);
        }

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
            builder.plugins.putAll(plugins);
            builder.pluginPredicate = pluginPredicate;
            builder.appliedPluginClasses.addAll(appliedPluginClasses);
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
            return this;
        }

        /**
         * Check if the message exchange of the transport is the same as the given message exchange.
         *
         * @param messageExchange Message exchange to check.
         * @return true if the transport is set and it uses the given message exchange.
         */
        public boolean isUsingMessageExchange(MessageExchange<?, ?> messageExchange) {
            return transport != null && transport.messageExchange().equals(messageExchange);
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
         * Add a plugin and its child plugins to the config.
         *
         * <p>Plugins are applied when the config is built. Duplicate plugins are applied only once, based on the
         * plugin class. Plugins are applied in a sorted {@link ClientPlugin.Phase} and insertion order.
         *
         * <p><strong>Deduplication behavior:</strong> Plugins are deduplicated by class, not instance.
         * This means {@code new MyPlugin()} called twice will only apply once. The first plugin added wins.
         *
         * <p><strong>Configuration reuse:</strong> When using {@code config.toBuilder().build()}, plugins
         * that were already applied will not re-apply, making configs reusable as templates.
         *
         * @param plugin Plugin to add.
         * @return the builder.
         * @see ClientPlugin
         * @see #pluginPredicate(Predicate)
         */
        public Builder addPlugin(ClientPlugin plugin) {
            plugins.putIfAbsent(plugin.getClass(), plugin);
            return this;
        }

        /**
         * Adds a predicate that can be used to refuse the application of plugins.
         *
         * <p>A plugin is only applied to a client if the predicate returns true. Child plugins returned from
         * {@link ClientPlugin#getChildPlugins()} are only applied if both the parent and child are accepted
         * by the predicate.
         *
         * <p><strong>Important:</strong> Only plugins that pass the predicate are tracked as "applied".
         * This means if a plugin is rejected in one config, it can be applied in a derived config if
         * the predicate changes to accept it:
         *
         * <pre>{@code
         * var config1 = ClientConfig.builder()
         *     .pluginPredicate(p -> !(p instanceof MyPlugin))  // Reject MyPlugin
         *     .addPlugin(new MyPlugin())
         *     .build();  // MyPlugin NOT actually applied
         *
         * var config2 = config1.toBuilder()
         *     .pluginPredicate(p -> true)  // Accept everything
         *     .build();  // MyPlugin WILL apply now
         * }</pre>
         *
         * @param pluginPredicate Predicate to apply. Plugins that don't satisfy the predicate are not applied.
         * @return the builder.
         * @see #addPluginPredicate(Predicate)
         * @see Client.Builder#disableAutoPlugins()
         */
        public Builder pluginPredicate(Predicate<ClientPlugin> pluginPredicate) {
            this.pluginPredicate = Objects.requireNonNull(pluginPredicate);
            return this;
        }

        /**
         * Add a plugin predicate to any already applied predicates.
         *
         * @param pluginPredicate Predicate to add.
         * @return the builder.
         */
        public Builder addPluginPredicate(Predicate<ClientPlugin> pluginPredicate) {
            return pluginPredicate(this.pluginPredicate.and(pluginPredicate));
        }

        /**
         * Get the plugin predicate of the builder.
         *
         * @return the plugin predicate.
         */
        public Predicate<ClientPlugin> pluginPredicate() {
            return pluginPredicate;
        }

        private void applyFlattenedPlugins(List<ClientPlugin> flattenedPlugins) {
            for (ClientPlugin plugin : flattenedPlugins) {
                LOGGER.debug("Applying plugin to ClientBuilder: ", plugin.getClass());
                plugin.configureClient(this);
            }
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
