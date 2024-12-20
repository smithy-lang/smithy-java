/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResolvers;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.plugins.DefaultPlugin;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.retries.api.RetryStrategy;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Base Smithy client class.
 */
public abstract class Client {

    private final ClientConfig config;
    private final ClientPipeline<?, ?> pipeline;
    private final TypeRegistry typeRegistry;
    private final ClientInterceptor interceptor;
    private final IdentityResolvers identityResolvers;
    private final RetryStrategy retryStrategy;

    protected Client(Builder<?, ?> builder) {
        ClientConfig.Builder configBuilder = builder.configBuilder();

        // Resolve the transport and apply it as a plugin before user-defined plugins, allowing user-defined plugins
        // to supersede and functionality of plugins applied by transports.
        configBuilder.resolveTransport();

        for (ClientPlugin plugin : builder.plugins) {
            configBuilder.applyPlugin(plugin);
        }

        this.config = configBuilder.build();

        this.pipeline = ClientPipeline.of(config.protocol(), config.transport());

        // TODO: Add an interceptor to throw service-specific exceptions (e.g., PersonDirectoryClientException).
        this.interceptor = ClientInterceptor.chain(config.interceptors());

        this.identityResolvers = IdentityResolvers.of(config.identityResolvers());

        this.typeRegistry = typeRegistry();

        if (config.retryStrategy() != null) {
            this.retryStrategy = config.retryStrategy();
        } else {
            // TODO: Pick a better default retry strategy.
            this.retryStrategy = RetryStrategy.noRetries();
        }
    }

    /**
     * Performs the actual RPC call.
     *
     * @param input       Input to send.
     * @param operation   The operation shape.
     * @param overrideConfig Configuration to override for the call.
     * @param <I>         Input shape.
     * @param <O>         Output shape.
     * @return Returns the deserialized output.
     */
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> call(
        I input,
        ApiOperation<I, O> operation,
        RequestOverrideConfig overrideConfig
    ) {
        // Create a copy of the type registry that adds the errors this operation can encounter.
        TypeRegistry operationRegistry = TypeRegistry.compose(operation.errorRegistry(), typeRegistry);

        ClientPipeline<?, ?> callPipeline;
        ClientInterceptor callInterceptor;
        IdentityResolvers callIdentityResolvers;
        ClientConfig callConfig;
        if (overrideConfig == null) {
            callConfig = config;
            callPipeline = pipeline;
            callInterceptor = interceptor;
            callIdentityResolvers = identityResolvers;
        } else {
            callConfig = config.withRequestOverride(overrideConfig);
            callPipeline = ClientPipeline.of(callConfig.protocol(), callConfig.transport());
            callInterceptor = ClientInterceptor.chain(config.interceptors());
            callIdentityResolvers = IdentityResolvers.of(config.identityResolvers());
        }

        var callBuilder = ClientCall.<I, O>builder();
        callBuilder.input = input;
        callBuilder.operation = operation;
        callBuilder.endpointResolver = callConfig.endpointResolver();
        callBuilder.context = Context.modifiableCopy(callConfig.context());
        callBuilder.interceptor = callInterceptor;
        callBuilder.supportedAuthSchemes.addAll(callConfig.supportedAuthSchemes());
        callBuilder.authSchemeResolver = callConfig.authSchemeResolver();
        callBuilder.identityResolvers = callIdentityResolvers;
        callBuilder.typeRegistry = operationRegistry;
        callBuilder.retryStrategy = retryStrategy;
        callBuilder.retryScope = callConfig.retryScope();

        return callPipeline.send(callBuilder.build());
    }

    /**
     * Type registry of error types to append to operation error registry.
     *
     * <p>Client implementations with implicit errors should override this implementation.
     *
     * @return Error type registry.
     */
    protected TypeRegistry typeRegistry() {
        return TypeRegistry.empty();
    };

    /**
     * @return the configuration in use by this client.
     */
    public ClientConfig config() {
        return config;
    }

    /**
     * Builder for Clients and request overrides.
     *
     * <p><strong>Note:</strong> this class is implemented by code generated builders, but should not
     * be used outside of code generated classes.
     *
     * @param <I> Client interface created by builder
     * @param <B> Implementing builder class
     */
    @SmithyInternalApi
    public abstract static class Builder<I, B extends Builder<I, B>> implements ClientSetting<B> {

        private final ClientConfig.Builder configBuilder = ClientConfig.builder();
        private final List<ClientPlugin> plugins = new ArrayList<>();

        public Builder() {
            // Apply the default plugin by default, and do it before user-controlled plugins.
            // Applying this first allows user-defined plugins to make changes that potentially supersede defaults.
            configBuilder.applyPlugin(DefaultPlugin.INSTANCE);
        }

        /**
         * A ClientConfig.Builder available to subclasses to initialize in their constructors with any default
         * values, before any overrides are provided to this Client.Builder's methods.
         */
        protected ClientConfig.Builder configBuilder() {
            return configBuilder;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> B putConfig(Context.Key<V> key, V value) {
            configBuilder().putConfig(key, value);
            return (B) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> B putConfigIfAbsent(Context.Key<T> key, T value) {
            configBuilder().putConfigIfAbsent(key, value);
            return (B) this;
        }

        /**
         * Specify overrides to the configuration that should be used for clients created by this builder.
         *
         * @param config Client config to use for updated values.
         * @return Returns the builder.
         */
        @SuppressWarnings("unchecked")
        public B withConfiguration(ClientConfig config) {
            configBuilder.transport(config.transport())
                .protocol(config.protocol())
                .endpointResolver(config.endpointResolver())
                .authSchemeResolver(config.authSchemeResolver())
                .identityResolvers(config.identityResolvers());
            config.interceptors().forEach(configBuilder::addInterceptor);
            config.supportedAuthSchemes().forEach(configBuilder::putSupportedAuthSchemes);
            configBuilder.putAllConfig(config.context());
            return (B) this;
        }

        /**
         * Set the transport used to send requests.
         *
         * @param transport Client transport used to send requests.
         * @return Returns the builder.
         */
        @SuppressWarnings("unchecked")
        public B transport(ClientTransport<?, ?> transport) {
            this.configBuilder.transport(transport);
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
            this.configBuilder.protocol(protocol);
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
            this.configBuilder.endpointResolver(endpointResolver);
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
            this.configBuilder.addInterceptor(interceptor);
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
            this.configBuilder.authSchemeResolver(authSchemeResolver);
            return (B) this;
        }

        /**
         * Add supported auth schemes to the client that works in tandem with the {@link AuthSchemeResolver}.
         *
         * <p> If the scheme ID is already supported, it will be replaced by the provided auth scheme.
         *
         * @param authSchemes Auth schemes to add.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B putSupportedAuthSchemes(AuthScheme<?, ?>... authSchemes) {
            this.configBuilder.putSupportedAuthSchemes(authSchemes);
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
            this.configBuilder.addIdentityResolver(identityResolvers);
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
            this.configBuilder.identityResolvers(identityResolvers);
            return (B) this;
        }

        /**
         * Set the retry strategy to use with the client.
         *
         * <p>This should only be used to override the default retry strategy.
         *
         * @param retryStrategy Retry strategy to use.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B retryStrategy(RetryStrategy retryStrategy) {
            this.configBuilder.retryStrategy(retryStrategy);
            return (B) this;
        }

        /**
         * Set a custom retry scope to use with requests.
         *
         * @param retryScope Retry scope to set.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B retryScope(String retryScope) {
            this.configBuilder.retryScope(retryScope);
            return (B) this;
        }

        /**
         * Add a plugin to the client.
         *
         * @param plugin Plugin to add.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B addPlugin(ClientPlugin plugin) {
            plugins.add(plugin);
            return (B) this;
        }

        /**
         * Creates the client.
         *
         * @return the created client.
         */
        public abstract I build();
    }

    @SuppressWarnings("unchecked")
    protected static <E extends Throwable> E unwrapAndThrow(CompletionException e) throws E {
        Throwable cause = e.getCause();
        if (cause != null) {
            throw (E) cause;
        }
        throw (E) e;
    }
}
