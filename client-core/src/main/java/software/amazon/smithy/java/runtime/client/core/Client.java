/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointResolver;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

public abstract class Client {

    private static final AuthScheme<Object, Identity> NO_AUTH_AUTH_SCHEME = AuthScheme.noAuthAuthScheme();
    private static final AuthSchemeResolver DEFAULT_AUTH_SCHEME_RESOLVER = params -> List.of(
        new AuthSchemeOption(NO_AUTH_AUTH_SCHEME.schemeId(), null, null)
    );

    private final ClientConfig config;
    private final ClientPipeline<?, ?> pipeline;
    private final TypeRegistry typeRegistry;
    private final ClientInterceptor interceptor;
    private final IdentityResolvers identityResolvers;

    protected Client(Builder<?, ?> builder) {
        ClientConfig.Builder configBuilder = builder.configBuilder;
        for (ClientPlugin plugin : builder.plugins) {
            plugin.configureClient(configBuilder);
        }
        this.config = configBuilder.build();

        // After config is built, validate it
        validate(this.config);

        // Save some derived objects from configuration, so it doesn't have to be calculated each time.
        this.pipeline = ClientPipeline.of(config.protocol(), config.transport());
        // TODO: Add an interceptor to throw service-specific exceptions (e.g., PersonDirectoryClientException).
        this.interceptor = ClientInterceptor.chain(config.interceptors());
        this.identityResolvers = IdentityResolvers.of(config.identityResolvers());

        this.typeRegistry = TypeRegistry.builder().build();
    }

    private void validate(ClientConfig config) {
        ClientPipeline.validateProtocolAndTransport(config.protocol(), config.transport());
        Objects.requireNonNull(config.endpointResolver(), "endpointResolver is null");
    }

    /**
     * Performs the actual RPC call.
     *
     * @param input       Input to send.
     * @param operation   The operation shape.
     * @param context     Context of the call.
     * @param <I>         Input shape.
     * @param <O>         Output shape.
     * @return Returns the deserialized output.
     */
    @Deprecated // TODO: update usages to use the other call() signature
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> call(
            I input,
            ApiOperation<I, O> operation,
            Context context
    ) {
        ClientConfig.Builder configBuilder = ClientConfig.builder();
        context.keys().forEachRemaining(key -> copyContext(key, context, configBuilder));
        return call(input, operation, configBuilder.build());
    }

    /**
     * Performs the actual RPC call.
     *
     * @param input       Input to send.
     * @param operation   The operation shape.
     * @param overridePlugins Plugins to apply for the cal.
     * @param <I>         Input shape.
     * @param <O>         Output shape.
     * @return Returns the deserialized output.
     */
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> call(
            I input,
            ApiOperation<I, O> operation,
            ClientPlugin... overridePlugins
    ) {
        return call(input, operation, null, overridePlugins);
    }

    /**
     * Performs the actual RPC call.
     *
     * @param input       Input to send.
     * @param operation   The operation shape.
     * @param overrideConfig Configuration to override for the call.
     * @param overridePlugins Plugins to apply for the cal.
     * @param <I>         Input shape.
     * @param <O>         Output shape.
     * @return Returns the deserialized output.
     */
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> call(
            I input,
            ApiOperation<I, O> operation,
            ClientConfig overrideConfig,
            ClientPlugin... overridePlugins
    ) {
        // Create a copy of the type registry that adds the errors this operation can encounter.
        TypeRegistry operationRegistry = TypeRegistry.builder()
            .putAllTypes(typeRegistry, operation.typeRegistry())
            .build();

        ClientPipeline<?, ?> callPipeline;
        ClientInterceptor callInterceptor;
        IdentityResolvers callIdentityResolvers;
        ClientConfig callConfig;
        if (overrideConfig == null & overridePlugins.length == 0) {
            callConfig = config;
            callPipeline = pipeline;
            callInterceptor = interceptor;
            callIdentityResolvers = identityResolvers;
        } else {
            callConfig = resolveConfig(overrideConfig, overridePlugins);
            validate(callConfig);
            callPipeline = ClientPipeline.of(callConfig.protocol(), callConfig.transport());
            callInterceptor = ClientInterceptor.chain(config.interceptors());
            callIdentityResolvers = IdentityResolvers.of(config.identityResolvers());
        }

        var call = ClientCall.<I, O>builder()
            .input(input)
            .operation(operation)
            .endpointResolver(callConfig.endpointResolver())
            .context(callConfig.context())
            .interceptor(callInterceptor)
            .supportedAuthSchemes(callConfig.supportedAuthSchemes())
            .authSchemeResolver(callConfig.authSchemeResolver())
            .identityResolvers(callIdentityResolvers)
            .errorCreator((c, id) -> {
                ShapeId shapeId = ShapeId.from(id);
                return operationRegistry.create(shapeId, ModeledApiException.class);
            })
            .build();

        return callPipeline.send(call);
    }

    private ClientConfig resolveConfig(ClientConfig overrideConfig, ClientPlugin[] overridePlugins) {
        ClientConfig.Builder configBuilder = config.toBuilder();
        if (overrideConfig != null) {
            applyOverrides(configBuilder, overrideConfig);
        }
        for (ClientPlugin plugin : overridePlugins) {
            plugin.configureClient(configBuilder);
        }
        return configBuilder.build();
    }

    private void applyOverrides(ClientConfig.Builder configBuilder, ClientConfig overrideConfig) {
        if (overrideConfig.transport() != null) {
            configBuilder.transport(overrideConfig.transport());
        }
        if (overrideConfig.protocol() != null) {
            configBuilder.protocol(overrideConfig.protocol());
        }
        if (overrideConfig.endpointResolver() != null) {
            configBuilder.endpointResolver(overrideConfig.endpointResolver());
        }
        if (overrideConfig.interceptors() != null) {
            overrideConfig.interceptors().forEach(configBuilder::addInterceptor);
        }
        if (overrideConfig.authSchemeResolver() != null) {
            configBuilder.authSchemeResolver(overrideConfig.authSchemeResolver());
        }
        if (overrideConfig.supportedAuthSchemes() != null) {
            overrideConfig.supportedAuthSchemes().forEach(configBuilder::putSupportedAuthSchemes);
        }
        if (overrideConfig.identityResolvers() != null) {
            configBuilder.identityResolvers(overrideConfig.identityResolvers());
        }

        // TODO: Currently there is no concept of mutable v/s immutable parts of Context.
        //       We just merge the client's Context with the Context of the operation's call.
        overrideConfig.context().keys().forEachRemaining(key -> copyContext(key, overrideConfig.context(), configBuilder));
    }

    private <T> void copyContext(Context.Key<T> key, Context src, ClientConfig.Builder dst) {
        dst.putConfig(key, src.get(key));
    }

    /**
     * Static builder for Clients.
     *
     * @param <I> Client interface created by builder
     * @param <B> Implementing builder class
     */
    public static abstract class Builder<I, B extends Builder<I, B>> {

        /**
         * An empty ClientConfig.Builder available to subclasses to initialize in their constructors with any default
         * values, before any overrides are provided to this Client.Builder's methods.
         */
        protected final ClientConfig.Builder configBuilder = ClientConfig.builder()
            .putSupportedAuthSchemes(NO_AUTH_AUTH_SCHEME)
            .authSchemeResolver(DEFAULT_AUTH_SCHEME_RESOLVER);

        private final List<ClientPlugin> plugins = new ArrayList<>();

        /**
         * Set the transport used to send requests.
         *
         * @param transport Client transport used to send requests.
         * @return Returns the builder.
         */
        @SuppressWarnings("unchecked")
        public B transport(ClientTransport<?, ?> transport) {
            this.configBuilder.transport(Objects.requireNonNull(transport, "transport is null"));
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
            this.configBuilder.protocol(Objects.requireNonNull(protocol, "protocol is null"));
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
            this.configBuilder.endpointResolver(Objects.requireNonNull(endpointResolver, "endpointResolver is null"));
            return (B) this;
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public B endpoint(Endpoint endpoint) {
            return endpointResolver(EndpointResolver.staticEndpoint(endpoint));
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public B endpoint(URI endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public B endpoint(String endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Add an interceptor to the client.
         *
         * @param interceptor Interceptor to add.
         * @return the builder.
         */
        @SuppressWarnings("unchecked")
        public B addInterceptor(ClientInterceptor interceptor) {
            this.configBuilder.addInterceptor(Objects.requireNonNull(interceptor, "interceptor is null"));
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
            this.configBuilder.authSchemeResolver(
                Objects.requireNonNull(authSchemeResolver, "authSchemeResolver is null")
            );
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
         * Put a strongly typed configuration on the builder.
         *
         * @param key Configuration key.
         * @param value Value to associate with the key.
         * @return the builder.
         * @param <T> Value type.
         */
        @SuppressWarnings("unchecked")
        public <T> B putConfig(Context.Key<T> key, T value) {
            this.configBuilder.putConfig(key, value);
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
            this.configBuilder.putConfigIfAbsent(key, value);
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
            plugins.add(Objects.requireNonNull(plugin, "plugin cannot be null"));
            return (B) this;
        }

        /**
         * Creates the client.
         *
         * @return the created client.
         */
        public abstract I build();
    }
}
