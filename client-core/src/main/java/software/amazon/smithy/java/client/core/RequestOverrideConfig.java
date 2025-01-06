/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.retries.api.RetryStrategy;

/**
 * An immutable representation of configuration overrides when invoking {@link Client#call}.
 *
 * <p>It has well-defined configuration elements that every {@link Client} needs. For extensible parts of a
 * {@link Client} that may need additional configuration, type safe configuration can be included using
 * {@link Context.Key}. It can also include {@link ClientPlugin}s that need to be applied.
 */
public final class RequestOverrideConfig {

    private final ClientProtocol<?, ?> protocol;
    private final EndpointResolver endpointResolver;
    private final List<ClientInterceptor> interceptors;
    private final List<AuthScheme<?, ?>> supportedAuthSchemes;
    private final AuthSchemeResolver authSchemeResolver;
    private final List<IdentityResolver<?>> identityResolvers;
    private final Context context;
    private final List<ClientPlugin> plugins;
    private final RetryStrategy retryStrategy;
    private final String retryScope;

    private RequestOverrideConfig(OverrideBuilder<?> builder) {
        this.protocol = builder.protocol;
        this.endpointResolver = builder.endpointResolver;
        this.interceptors = List.copyOf(builder.interceptors);
        this.supportedAuthSchemes = List.copyOf(builder.supportedAuthSchemes);
        this.authSchemeResolver = builder.authSchemeResolver;
        this.identityResolvers = List.copyOf(builder.identityResolvers);
        this.context = Context.unmodifiableCopy(builder.context);
        this.plugins = List.copyOf(builder.plugins);
        this.retryStrategy = builder.retryStrategy;
        this.retryScope = builder.retryScope;
    }

    ClientProtocol<?, ?> protocol() {
        return protocol;
    }

    EndpointResolver endpointResolver() {
        return endpointResolver;
    }

    List<ClientInterceptor> interceptors() {
        return interceptors;
    }

    List<AuthScheme<?, ?>> supportedAuthSchemes() {
        return supportedAuthSchemes;
    }

    AuthSchemeResolver authSchemeResolver() {
        return authSchemeResolver;
    }

    List<IdentityResolver<?>> identityResolvers() {
        return identityResolvers;
    }

    Context context() {
        return context;
    }

    List<ClientPlugin> plugins() {
        return plugins;
    }

    RetryStrategy retryStrategy() {
        return retryStrategy;
    }

    String retryScope() {
        return retryScope;
    }

    /**
     * Create a new builder to build {@link RequestOverrideConfig}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new builder using the same values as this object.
     *
     * @return the builder.
     */
    public Builder toBuilder() {
        Builder builder = builder()
                .protocol(protocol)
                .endpointResolver(endpointResolver)
                .authSchemeResolver(authSchemeResolver)
                .identityResolvers(identityResolvers)
                .retryStrategy(retryStrategy)
                .retryScope(retryScope);
        interceptors.forEach(builder::addInterceptor);
        supportedAuthSchemes.forEach(builder::putSupportedAuthSchemes);
        return builder;
    }

    /**
     * Static builder for {@link RequestOverrideConfig}.
     */
    public static final class Builder extends OverrideBuilder<Builder> {}

    /**
     * Abstract builder for {@link RequestOverrideConfig}.
     */
    public static abstract class OverrideBuilder<B extends OverrideBuilder<B>> implements ClientSetting<B> {
        /**
         * Creates the request override configuration.
         *
         * @return the created request override configuration.
         */
        public RequestOverrideConfig build() {
            return new RequestOverrideConfig(this);
        }

        private final List<ClientPlugin> plugins = new ArrayList<>();
        private ClientProtocol<?, ?> protocol;
        private EndpointResolver endpointResolver;
        private final List<ClientInterceptor> interceptors = new ArrayList<>();
        private AuthSchemeResolver authSchemeResolver;
        private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        private final List<IdentityResolver<?>> identityResolvers = new ArrayList<>();
        private final Context context = Context.create();
        private RetryStrategy retryStrategy;
        private String retryScope;

        /**
         * Add a plugin to the request.
         *
         * @param plugin Plugin to add.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B addPlugin(ClientPlugin plugin) {
            plugins.add(Objects.requireNonNull(plugin, "plugin cannot be null"));
            return (B) this;
        }

        /**
         * Set the protocol to use when sending requests.
         *
         * @param protocol Client protocol used to send requests.
         * @return Returns the builder.
         */
        @SuppressWarnings("unchecked")
        public B protocol(ClientProtocol<?, ?> protocol) {
            this.protocol = protocol;
            return (B) this;
        }

        /**
         * Set the resolver used to resolve endpoints.
         *
         * @param endpointResolver Endpoint resolver to use to resolve endpoints.
         * @return Returns the endpoint resolver.
         */
        @SuppressWarnings("unchecked")
        public B endpointResolver(EndpointResolver endpointResolver) {
            this.endpointResolver = endpointResolver;
            return (B) this;
        }

        /**
         * Add an interceptor to the client.
         *
         * @param interceptor Interceptor to add.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B addInterceptor(ClientInterceptor interceptor) {
            interceptors.add(interceptor);
            return (B) this;
        }

        /**
         * Set the auth scheme resolver of the client.
         *
         * @param authSchemeResolver Auth scheme resolver to use.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B authSchemeResolver(AuthSchemeResolver authSchemeResolver) {
            this.authSchemeResolver = authSchemeResolver;
            return (B) this;
        }

        /**
         * Add supported auth schemes to the client that works in tandem with the {@link AuthSchemeResolver}.
         *
         * <p>If the scheme ID is already supported, it will be replaced by the provided auth scheme.
         *
         * @param authSchemes Auth schemes to add.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B putSupportedAuthSchemes(AuthScheme<?, ?>... authSchemes) {
            supportedAuthSchemes.addAll(Arrays.asList(authSchemes));
            return (B) this;
        }

        /**
         * Add identity resolvers to the client.
         *
         * @param identityResolvers Identity resolvers to add.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B addIdentityResolver(IdentityResolver<?>... identityResolvers) {
            this.identityResolvers.addAll(Arrays.asList(identityResolvers));
            return (B) this;
        }

        /**
         * Set the identity resolvers of the client.
         *
         * @param identityResolvers Identity resolvers to set.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B identityResolvers(List<IdentityResolver<?>> identityResolvers) {
            this.identityResolvers.clear();
            this.identityResolvers.addAll(identityResolvers);
            return (B) this;
        }

        /**
         * Put a strongly typed configuration on the builder. If a key was already present, it is overridden.
         *
         * @param key Configuration key.
         * @param value Value to associate with the key.
         * @return the builder.
         * @param <T> Value type.
         */
        @SuppressWarnings("unchecked")
        public <T> B putConfig(Context.Key<T> key, T value) {
            context.put(key, value);
            return (B) this;
        }

        /**
         * Put a strongly typed configuration on the builder, if not already present.
         *
         * @param key Configuration key.
         * @param value Value to associate with the key.
         * @return the builder.
         * @param <T> Value type.
         */
        @SuppressWarnings("unchecked")
        public <T> B putConfigIfAbsent(Context.Key<T> key, T value) {
            context.putIfAbsent(key, value);
            return (B) this;
        }

        /**
         * Set a retry strategy to use.
         *
         * @param retryStrategy Retry strategy to use.
         * @return the builder.
         * @see #retryStrategy(RetryStrategy)
         */
        @SuppressWarnings("unchecked")
        public B retryStrategy(RetryStrategy retryStrategy) {
            this.retryStrategy = retryStrategy;
            return (B) this;
        }

        /**
         * Set a retry scope to use with retries.
         *
         * @param retryScope The retry scope to set (e.g., an ARN).
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B retryScope(String retryScope) {
            this.retryScope = retryScope;
            return (B) this;
        }
    }
}
