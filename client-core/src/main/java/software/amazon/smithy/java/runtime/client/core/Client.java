/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
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

    private final ClientConfig config;
    private final ClientPipeline<?, ?> pipeline;
    private final TypeRegistry typeRegistry;
    private final ClientInterceptor interceptor;
    private final IdentityResolvers identityResolvers;

    protected Client(Builder<?, ?> builder) {
        ClientConfig.Builder configBuilder = ClientConfig.builder();

        configBuilder.transport(builder.transport);
        configBuilder.protocol(builder.protocol);
        configBuilder.endpointResolver(builder.endpointResolver);
        builder.interceptors.forEach(configBuilder::addInterceptor);
        configBuilder.authSchemeResolver(builder.authSchemeResolver);
        builder.supportedAuthSchemes.forEach(configBuilder::putSupportedAuthSchemes);
        builder.identityResolvers.forEach(configBuilder::addIdentityResolver);

        // Initialize configBuilder's context with context provided to Client.Builder. This way these context keys are
        // available to plugins.
        builder.context.keys().forEachRemaining(key -> copyContext(key, builder.context, configBuilder));

        for (ClientPlugin plugin : builder.plugins) {
            plugin.configureClient(configBuilder);
        }

        // Note, the below was already done before applying plugins. However, if plugin is setting a (default) value
        // for a context key, context provided to Client.Builder should be considered as overriding any context
        // key/values provided by ClientPlugins.
        // TODO: This may be unnecessary if we assume plugins would use putIfAbsent().
        builder.context.keys().forEachRemaining(key -> copyContext(key, builder.context, configBuilder));

        this.config = configBuilder.build();

        this.pipeline = ClientPipeline.of(config.protocol(), config.transport());

        // TODO: Add an interceptor to throw service-specific exceptions (e.g., PersonDirectoryClientException).
        this.interceptor = ClientInterceptor.chain(config.interceptors());

        this.identityResolvers = IdentityResolvers.of(config.identityResolvers());

        this.typeRegistry = TypeRegistry.builder().build();
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
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> call(
        I input,
        ApiOperation<I, O> operation,
        Context context
    ) {
        // Create a copy of the type registry that adds the errors this operation can encounter.
        TypeRegistry operationRegistry = TypeRegistry.builder()
            .putAllTypes(typeRegistry, operation.typeRegistry())
            .build();

        Context mergedContext = merge(config.context(), context);

        var call = ClientCall.<I, O>builder()
            .input(input)
            .operation(operation)
            .endpointResolver(config.endpointResolver())
            .context(mergedContext)
            .interceptor(interceptor)
            .supportedAuthSchemes(config.supportedAuthSchemes())
            .authSchemeResolver(config.authSchemeResolver())
            .identityResolvers(identityResolvers)
            .errorCreator((c, id) -> {
                ShapeId shapeId = ShapeId.from(id);
                return operationRegistry.create(shapeId, ModeledApiException.class);
            })
            .build();

        return pipeline.send(call);
    }

    // TODO: Currently there is no concept of mutable v/s immutable parts of Context.
    //       We just merge the client's Context with the Context of the operation's call.
    private Context merge(Context clientContext, Context operationContext) {
        Context context = Context.create();
        clientContext.keys().forEachRemaining(key -> copyContext(key, clientContext, context));
        operationContext.keys().forEachRemaining(key -> copyContext(key, operationContext, context));
        return context;
    }

    private <T> void copyContext(Context.Key<T> key, Context src, Context dst) {
        dst.put(key, src.get(key));
    }

    private <T> void copyContext(Context.Key<T> key, Context src, ClientConfig.Builder dst) {
        dst.put(key, src.get(key));
    }

    /**
     * Static builder for Clients.
     *
     * @param <I> Client interface created by builder
     * @param <B> Implementing builder class
     */
    public static abstract class Builder<I, B extends Builder<I, B>> {
        private ClientTransport<?, ?> transport;
        private ClientProtocol<?, ?> protocol;
        private EndpointResolver endpointResolver;
        private final List<ClientInterceptor> interceptors = new ArrayList<>();
        private AuthSchemeResolver authSchemeResolver;
        private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        private final List<IdentityResolver<?>> identityResolvers = new ArrayList<>();
        private final Context context = Context.create();

        private final List<ClientPlugin> plugins = new ArrayList<>();

        /**
         * Set the transport used to send requests.
         *
         * @param transport Client transport used to send requests.
         * @return Returns the builder.
         */
        @SuppressWarnings("unchecked")
        public B transport(ClientTransport<?, ?> transport) {
            this.transport = transport;
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
         * <p> If the scheme ID is already supported, it will be replaced by the provided auth scheme.
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
         * Put a strongly typed configuration on the builder.
         *
         * @param key Configuration key.
         * @param value Value to associate with the key.
         * @return the builder.
         * @param <T> Value type.
         */
        // TODO: Naming: Should this method name say "what" it is putting, like putXYZ? put/putContext/putConfig?
        @SuppressWarnings("unchecked")
        public <T> B put(Context.Key<T> key, T value) {
            context.put(key, value);
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
