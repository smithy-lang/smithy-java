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
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;

public abstract class Client<T extends Client<T>> {
    private final EndpointResolver endpointResolver;
    private final ClientTransport transport;
    private final TypeRegistry typeRegistry;
    private final ClientInterceptor interceptor;
    private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
    private final AuthSchemeResolver authSchemeResolver;
    private final IdentityResolvers identityResolvers;

    public Client(Builder<T> builder) {
        this.endpointResolver = Objects.requireNonNull(builder.endpointResolver, "endpointResolver is null");
        this.transport = new ApiCallTimeoutTransport(Objects.requireNonNull(builder.transport, "transport is null"));
        // TODO: Add an interceptor to throw service-specific exceptions (e.g., PersonDirectoryClientException).
        this.interceptor = ClientInterceptor.chain(builder.interceptors);

        // By default, support NoAuthAuthScheme
        AuthScheme<Object, Identity> noAuthAuthScheme = AuthScheme.noAuthAuthScheme();
        this.supportedAuthSchemes.add(noAuthAuthScheme);
        this.supportedAuthSchemes.addAll(builder.supportedAuthSchemes);

        // TODO: Better defaults? Require these?
        AuthSchemeResolver defaultAuthSchemeResolver = params -> List.of(
            new AuthSchemeOption(noAuthAuthScheme.schemeId(), null, null)
        );
        this.authSchemeResolver = Objects.requireNonNullElse(builder.authSchemeResolver, defaultAuthSchemeResolver);
        this.identityResolvers = IdentityResolvers.of(builder.identityResolvers);

        // Here is where you would register errors bound to the service on the registry.
        // ...
        this.typeRegistry = TypeRegistry.builder().build();
    }

    /**
     * Performs the actual RPC call.
     *
     * @param input       Input to send.
     * @param inputStream Any kind of data stream extracted from the input, or null.
     * @param eventStream The event stream extracted from the input, or null. TODO: Implement.
     * @param operation   The operation shape.
     * @param context     Context of the call.
     * @param <I>         Input shape.
     * @param <O>         Output shape.
     * @return Returns the deserialized output.
     */
    protected <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> call(
        I input,
        DataStream inputStream,
        Object eventStream,
        SdkOperation<I, O> operation,
        Context context
    ) {
        // Create a copy of the type registry that adds the errors this operation can encounter.
        TypeRegistry operationRegistry = TypeRegistry.builder()
            .putAllTypes(typeRegistry, operation.typeRegistry())
            .build();

        return transport.send(
            ClientCall.<I, O>builder()
                .input(input)
                .operation(operation)
                .endpointResolver(endpointResolver)
                .context(context)
                .requestDataStream(inputStream)
                .requestEventStream(eventStream)
                .interceptor(interceptor)
                .supportedAuthSchemes(supportedAuthSchemes)
                .authSchemeResolver(authSchemeResolver)
                .identityResolvers(identityResolvers)
                .errorCreator((c, id) -> {
                    ShapeId shapeId = ShapeId.from(id);
                    return operationRegistry.create(shapeId, ModeledSdkException.class);
                })
                .build()
        );
    }

    public static abstract class Builder<T extends Client<T>> {
        ClientTransport transport;
        EndpointResolver endpointResolver;
        final List<ClientInterceptor> interceptors = new ArrayList<>();
        AuthSchemeResolver authSchemeResolver;
        final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        final List<IdentityResolver<?>> identityResolvers = new ArrayList<>();

        /**
         * Set the protocol and transport to when calling the service.
         *
         * @param transport Client transport used to send requests.
         * @return Returns the builder.
         */
        public Builder<T> transport(ClientTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Set the resolver used to resolve endpoints.
         *
         * @param endpointResolver Endpoint resolver to use to resolve endpoints.
         * @return Returns the endpoint resolver.
         */
        public Builder<T> endpointResolver(EndpointResolver endpointResolver) {
            this.endpointResolver = endpointResolver;
            return this;
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder<T> endpoint(Endpoint endpoint) {
            return endpointResolver(EndpointResolver.staticEndpoint(endpoint));
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder<T> endpoint(URI endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder<T> endpoint(String endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Add an interceptor to the client.
         *
         * @param interceptor Interceptor to add.
         * @return the builder.
         */
        public Builder<T> addInterceptor(ClientInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        /**
         * Set the auth scheme resolver of the client.
         *
         * @param authSchemeResolver Auth scheme resolver to use.
         * @return the builder.
         */
        public Builder<T> authSchemeResolver(AuthSchemeResolver authSchemeResolver) {
            this.authSchemeResolver = authSchemeResolver;
            return this;
        }

        /**
         * Add supported auth schemes to the client that works in tandem with the {@link AuthSchemeResolver}.
         *
         * <p> If the scheme ID is already supported, it will be replaced by the provided auth scheme.
         *
         * @param authSchemes Auth schemes to add.
         * @return the builder.
         */
        public Builder<T> putSupportedAuthSchemes(AuthScheme<?, ?>... authSchemes) {
            supportedAuthSchemes.addAll(Arrays.asList(authSchemes));
            return this;
        }

        /**
         * Add identity resolvers to the client.
         *
         * @param identityResolvers Identity resolvers to add.
         * @return the builder.
         */
        public Builder<T> addIdentityResolver(IdentityResolver<?>... identityResolvers) {
            this.identityResolvers.addAll(Arrays.asList(identityResolvers));
            return this;
        }

        /**
         * Set the identity resolvers of the client.
         *
         * @param identityResolvers Identity resolvers to set.
         * @return the builder.
         */
        public Builder<T> identityResolvers(List<IdentityResolver<?>> identityResolvers) {
            this.identityResolvers.clear();
            this.identityResolvers.addAll(identityResolvers);
            return this;
        }

        /**
         * Creates the client.
         *
         * @return the created client.
         */
        public abstract T build();
    }
}
