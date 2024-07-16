/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointResolver;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;

/**
 * Contains the information needed to send a request from a client using a protocol.
 *
 * @param <I> Input to send.
 * @param <O> Output to return.
 */
public final class ClientCall<I extends SerializableStruct, O extends SerializableStruct> {

    private final I input;
    private final EndpointResolver endpointResolver;
    private final ApiOperation<I, O> operation;
    private final Context context;
    private final BiFunction<Context, String, Optional<ShapeBuilder<ModeledApiException>>> errorCreator;
    private final ClientInterceptor interceptor;
    private final AuthSchemeResolver authSchemeResolver;
    private final Map<String, AuthScheme<?, ?>> supportedAuthSchemes;
    private final IdentityResolvers identityResolvers;
    private final ExecutorService executor;

    private ClientCall(Builder<I, O> builder) {
        input = Objects.requireNonNull(builder.input, "input is null");
        operation = Objects.requireNonNull(builder.operation, "operation is null");
        context = Objects.requireNonNull(builder.context, "context is null");
        errorCreator = Objects.requireNonNull(builder.errorCreator, "errorCreator is null");
        endpointResolver = Objects.requireNonNull(builder.endpointResolver, "endpointResolver is null");
        interceptor = Objects.requireNonNull(builder.interceptor, "interceptor is null");
        authSchemeResolver = Objects.requireNonNull(builder.authSchemeResolver, "authSchemeResolver is null");
        identityResolvers = Objects.requireNonNull(builder.identityResolvers, "identityResolvers is null");
        supportedAuthSchemes = builder.supportedAuthSchemes.stream()
            .collect(Collectors.toMap(AuthScheme::schemeId, Function.identity()));
        //TODO fix this to not use a cached thread pool.
        executor = builder.executor == null ? Executors.newCachedThreadPool() : builder.executor;
    }

    /**
     * Create a ClientCall builder.
     *
     * @return Returns the created builder.
     * @param <I> Input type.
     * @param <O> Output type.
     */
    public static <I extends SerializableStruct, O extends SerializableStruct> Builder<I, O> builder() {
        return new Builder<>();
    }

    /**
     * Get the input of the operation.
     *
     * @return Return the operation input.
     */
    public I input() {
        return input;
    }

    /**
     * Get the operation definition.
     *
     * @return Returns the operation definition.
     */
    public ApiOperation<I, O> operation() {
        return operation;
    }

    /**
     * The endpoint resolver to use with the call.
     *
     * @return Returns the endpoint resolver.
     */
    public EndpointResolver endpointResolver() {
        return endpointResolver;
    }

    /**
     * Gets the client interceptor used by the call.
     *
     * @return Returns the client interceptor.
     */
    public ClientInterceptor interceptor() {
        return interceptor;
    }

    /**
     * Get the context of the call.
     *
     * @return Return the call context.
     */
    public Context context() {
        return context;
    }

    /**
     * Attempts to create a builder for a modeled error.
     *
     * <p>If this method returns null, a protocol must create an appropriate error based on protocol hints.
     *
     * @param context Context to pass to the creator.
     * @param shapeId Nullable ID of the error shape to create, if known. A string is used because sometimes the
     *                only information we have is just a name.
     * @return Returns the error deserializer if present.
     */
    public Optional<ShapeBuilder<ModeledApiException>> createExceptionBuilder(Context context, String shapeId) {
        return errorCreator.apply(context, shapeId);
    }

    /**
     * Get the resolver used to determine the authentication scheme for the client call.
     *
     * @return the resolver.
     */
    public AuthSchemeResolver authSchemeResolver() {
        return authSchemeResolver;
    }

    /**
     * Get the list of supported auth schemes.
     *
     * @return supported auth schemes.
     */
    public Map<String, AuthScheme<?, ?>> supportedAuthSchemes() {
        return supportedAuthSchemes;
    }

    /**
     * Get the IdentityResolvers used to find an identity resolver by type.
     *
     * @return the IdentityResolvers.
     */
    public IdentityResolvers identityResolvers() {
        return identityResolvers;
    }

    /**
     * Get the executor service to use with the call.
     *
     * @return the executor to use.
     */
    public ExecutorService executor() {
        return executor;
    }

    /**
     * Builds the default implementation of a client call.
     *
     * @param <I> Input to send.
     * @param <O> Expected output.
     */
    public static final class Builder<I extends SerializableStruct, O extends SerializableStruct> {

        private I input;
        private EndpointResolver endpointResolver;
        private ApiOperation<I, O> operation;
        private Context context;
        private BiFunction<Context, String, Optional<ShapeBuilder<ModeledApiException>>> errorCreator;
        private ClientInterceptor interceptor = ClientInterceptor.NOOP;
        private AuthSchemeResolver authSchemeResolver;
        private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        private IdentityResolvers identityResolvers;
        private ExecutorService executor;

        private Builder() {}

        /**
         * Set the input of the call.
         *
         * @param input Input to set.
         * @return Returns the builder.
         */
        public Builder<I, O> input(I input) {
            this.input = input;
            return this;
        }

        /**
         * Set the operation schema.
         *
         * @param operation Operation to call.
         * @return Returns the builder.
         */
        public Builder<I, O> operation(ApiOperation<I, O> operation) {
            this.operation = operation;
            return this;
        }

        /**
         * Sets the context of the call.
         *
         * @param context Context to use.
         * @return Returns the builder.
         */
        public Builder<I, O> context(Context context) {
            this.context = context;
            return this;
        }

        /**
         * Sets a supplier used to create an error based on the context and extracted shape ID.
         *
         * <p>If the supplier returns null or no supplier is provided, the protocol will create an error based on
         * protocol hints (e.g., HTTP status codes).
         *
         * @param errorCreator Error supplier to create the builder for an error.
         * @return Returns the builder.
         */
        public Builder<I, O> errorCreator(
            BiFunction<Context, String, Optional<ShapeBuilder<ModeledApiException>>> errorCreator
        ) {
            this.errorCreator = errorCreator;
            return this;
        }

        /**
         * Set the endpoint resolver used to resolve endpoints for the call.
         *
         * @param endpointResolver Endpoint resolver to set.
         * @return Returns the builder.
         */
        public Builder<I, O> endpointResolver(EndpointResolver endpointResolver) {
            this.endpointResolver = endpointResolver;
            return this;
        }

        /**
         * Set the interceptor to use with this call.
         *
         * @param interceptor Interceptor to use.
         * @return Returns the builder.
         */
        public Builder<I, O> interceptor(ClientInterceptor interceptor) {
            this.interceptor = Objects.requireNonNullElse(interceptor, ClientInterceptor.NOOP);
            return this;
        }

        /**
         * Set the auth scheme resolver of the call.
         *
         * @param authSchemeResolver Auth scheme resolver to set.
         * @return the builder.
         */
        public Builder<I, O> authSchemeResolver(AuthSchemeResolver authSchemeResolver) {
            this.authSchemeResolver = authSchemeResolver;
            return this;
        }

        /**
         * Sets the list of supported auth schemes for the call.
         *
         * @param supportedAuthSchemes Supported auth schemes.
         * @return the builder.
         */
        public Builder<I, O> supportedAuthSchemes(List<AuthScheme<?, ?>> supportedAuthSchemes) {
            this.supportedAuthSchemes.clear();
            supportedAuthSchemes.forEach(this::addSupportedAuthScheme);
            return this;
        }

        /**
         * Adds a supported auth scheme.
         *
         * @param supportedAuthScheme Supported scheme to add.
         * @return the builder.
         */
        public Builder<I, O> addSupportedAuthScheme(AuthScheme<?, ?> supportedAuthScheme) {
            supportedAuthSchemes.add(Objects.requireNonNull(supportedAuthScheme));
            return this;
        }

        /**
         * Set the identity resolvers of the call.
         *
         * @param identityResolvers Identity resolvers to set.
         * @return the builder.
         */
        public Builder<I, O> identityResolvers(IdentityResolvers identityResolvers) {
            this.identityResolvers = identityResolvers;
            return this;
        }

        /**
         * Set the ExecutorService to use with the call.
         *
         * @param executor Executor to use.
         * @return ths builder.
         */
        public Builder<I, O> executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Create the call.
         *
         * @return the created call.
         * @throws NullPointerException when required values are missing.
         */
        public ClientCall<I, O> build() {
            return new ClientCall<>(this);
        }
    }
}
