/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import software.amazon.smithy.java.runtime.api.EndpointProvider;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.DataStream;

/**
 * Contains the information needed to send a request from a client using a protocol.
 *
 * @param <I> Input to send.
 * @param <O> Output to return.
 */
public final class ClientCall<I extends SerializableShape, O extends SerializableShape> {

    private final I input;
    private final EndpointProvider endpointProvider;
    private final SdkOperation<I, O> operation;
    private final Context context;
    private final BiFunction<Context, String, Optional<ShapeBuilder<ModeledSdkException>>> errorCreator;
    private final ClientInterceptor interceptor;
    private final DataStream requestInputStream;
    private final AuthSchemeResolver authSchemeResolver;
    private final List<AuthScheme<?, ?>> supportedAuthSchemes;
    private final IdentityResolvers identityResolvers;
    private final Object requestEventStream;
    private final ExecutorService executor;

    private ClientCall(Builder<I, O> builder) {
        input = Objects.requireNonNull(builder.input, "input is null");
        operation = Objects.requireNonNull(builder.operation, "operation is null");
        context = Objects.requireNonNull(builder.context, "context is null");
        errorCreator = Objects.requireNonNull(builder.errorCreator, "errorCreator is null");
        endpointProvider = Objects.requireNonNull(builder.endpointProvider, "endpointProvider is null");
        interceptor = Objects.requireNonNull(builder.interceptor, "interceptor is null");
        authSchemeResolver = Objects.requireNonNull(builder.authSchemeResolver, "authSchemeResolver is null");
        identityResolvers = Objects.requireNonNull(builder.identityResolvers, "identityResolvers is null");
        supportedAuthSchemes = builder.supportedAuthSchemes;
        requestInputStream = builder.requestInputStream;
        requestEventStream = builder.requestEventStream;
        executor = builder.executor == null ? Executors.newVirtualThreadPerTaskExecutor() : builder.executor;

        // Initialize the context.
        context.put(CallContext.INPUT, input());
        context.put(CallContext.OPERATION_SCHEMA, operation().schema());
        context.put(CallContext.INPUT_SCHEMA, operation().inputSchema());
        context.put(CallContext.OUTPUT_SCHEMA, operation().outputSchema());
        context.put(CallContext.CLIENT_INTERCEPTOR, interceptor());
    }

    /**
     * Create a ClientCall builder.
     *
     * @return Returns the created builder.
     * @param <I> Input type.
     * @param <O> Output type.
     */
    public static <I extends SerializableShape, O extends SerializableShape> Builder<I, O> builder() {
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
    public SdkOperation<I, O> operation() {
        return operation;
    }

    /**
     * The endpoint provider to use with the call.
     *
     * @return Returns the endpoint provider.
     */
    public EndpointProvider endpointProvider() {
        return endpointProvider;
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
     * Contains the optionally present input stream.
     *
     * @return the optionally present input stream to send in a request.
     */
    public Optional<DataStream> requestInputStream() {
        return Optional.ofNullable(requestInputStream);
    }

    /**
     * Contains the optionally present event stream of the input shape.
     *
     * <p>TODO: Implement event streams.
     * @return the optionally present input event stream.
     */
    public Optional<Object> requestEventStream() {
        return Optional.empty();
    }

    /**
     * Create a builder for the output of the operation.
     *
     * @param context Context to pass to the creator.
     * @param shapeId Nullable ID of the error shape to create, if known.
     * @return Returns the created output builder.
     */
    public ShapeBuilder<O> createOutputBuilder(Context context, String shapeId) {
        // TODO: Allow customizing this if needed.
        return operation().outputBuilder();
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
    public Optional<ShapeBuilder<ModeledSdkException>> createExceptionBuilder(Context context, String shapeId) {
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
    public List<AuthScheme<?, ?>> supportedAuthSchemes() {
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
    public static final class Builder<I extends SerializableShape, O extends SerializableShape> {

        private I input;
        private EndpointProvider endpointProvider;
        private SdkOperation<I, O> operation;
        private Context context;
        private BiFunction<Context, String, Optional<ShapeBuilder<ModeledSdkException>>> errorCreator;
        private ClientInterceptor interceptor = ClientInterceptor.NOOP;
        private DataStream requestInputStream;
        private AuthSchemeResolver authSchemeResolver;
        private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        private IdentityResolvers identityResolvers;
        private Object requestEventStream;
        private ExecutorService executor;

        private Builder() {
        }

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
        public Builder<I, O> operation(SdkOperation<I, O> operation) {
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
            BiFunction<Context, String, Optional<ShapeBuilder<ModeledSdkException>>> errorCreator
        ) {
            this.errorCreator = errorCreator;
            return this;
        }

        /**
         * Set the endpoint provider used to resolve endpoints for the call.
         *
         * @param endpointProvider Endpoint provider to set.
         * @return Returns the builder.
         */
        public Builder<I, O> endpointProvider(EndpointProvider endpointProvider) {
            this.endpointProvider = endpointProvider;
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
         * Set the request input stream of the call.
         *
         * @param requestInputStream Input stream to set.
         * @return the builder.
         */
        public Builder<I, O> requestInputStream(DataStream requestInputStream) {
            if (requestInputStream != null) {
                this.requestInputStream = requestInputStream;
                this.requestEventStream = null;
            }
            return this;
        }

        /**
         * Set the request event stream of the call.
         *
         * @param requestEventStream Event stream to set.
         * @return the builder.
         */
        public Builder<I, O> requestEventStream(Object requestEventStream) {
            if (requestEventStream != null) {
                this.requestEventStream = requestEventStream;
                this.requestInputStream = null;
            }
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
