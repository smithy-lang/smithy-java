/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.event.Frame;
import software.amazon.smithy.java.core.serde.event.ProtocolEventStreamWriter;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.retries.api.RetryStrategy;
import software.amazon.smithy.java.retries.api.RetryToken;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Contains the information needed to send a request from a client using a protocol.
 *
 * @param <I> Input to send.
 * @param <O> Output to return.
 */
final class ClientCall<I extends SerializableStruct, O extends SerializableStruct> {

    final I input;
    final ApiOperation<I, O> operation;
    final Context context;
    final TypeRegistry typeRegistry;
    final ClientInterceptor interceptor;
    final EndpointResolver endpointResolver;
    final AuthSchemeResolver authSchemeResolver;
    final Map<ShapeId, AuthScheme<?, ?>> supportedAuthSchemes;
    final IdentityResolvers identityResolvers;
    final ProtocolEventStreamWriter<SerializableStruct, SerializableStruct, Frame<?>> eventStreamWriter;
    final RetryStrategy retryStrategy;
    final String retryScope;
    final ClientPipeline<?, ?> pipeline;
    RetryToken retryToken;
    int attemptCount = 1;

    ClientCall(
            I input,
            ApiOperation<I, O> operation,
            ClientConfig callConfig,
            ClientPipeline<?, ?> pipeline,
            ClientInterceptor interceptor,
            IdentityResolvers identityResolvers,
            TypeRegistry typeRegistry,
            RetryStrategy retryStrategy
    ) {
        this.input = Objects.requireNonNull(input, "input is null");
        this.operation = Objects.requireNonNull(operation, "operation is null");
        Objects.requireNonNull(callConfig, "callConfig is null");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline is null");
        this.interceptor = Objects.requireNonNullElse(interceptor, ClientInterceptor.NOOP);
        this.identityResolvers = Objects.requireNonNull(identityResolvers, "identityResolvers is null");
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry is null");
        this.retryStrategy = Objects.requireNonNull(retryStrategy, "retryStrategy is null");

        // Copy-on-write overlay over the client's immutable config context instead of an eager deep
        // copy
        this.context = Context.perCallOverlay(callConfig.context());
        this.endpointResolver = Objects.requireNonNull(callConfig.endpointResolver(), "endpointResolver is null");
        this.authSchemeResolver = Objects.requireNonNull(callConfig.authSchemeResolver(), "authSchemeResolver is null");
        this.retryScope = Objects.requireNonNullElse(callConfig.retryScope(), "");
        this.supportedAuthSchemes = callConfig.supportedAuthSchemesById();
        this.eventStreamWriter = operation.inputEventBuilderSupplier() != null
                ? ProtocolEventStreamWriter.of(input.getMemberValue(operation.inputStreamMember()))
                : null;

        this.context.put(CallContext.ENDPOINT_RESOLVER, endpointResolver);
        this.context.put(CallContext.RETRY_MAX, retryStrategy.maxAttempts());
    }

    /**
     * Copy constructor that swaps the input and recomputes the event stream writer.
     *
     * <p>The new call shares the source call's context (mutable), pipeline, interceptors, and all other
     * resolved configuration. Retry state is shared too — decorators run before the retry loop starts,
     * so {@code attemptCount}/{@code retryToken} are at their initial values either way.
     */
    ClientCall(ClientCall<I, O> source, I newInput) {
        this.input = Objects.requireNonNull(newInput, "input is null");
        this.operation = source.operation;
        this.context = source.context;
        this.typeRegistry = source.typeRegistry;
        this.interceptor = source.interceptor;
        this.endpointResolver = source.endpointResolver;
        this.authSchemeResolver = source.authSchemeResolver;
        this.supportedAuthSchemes = source.supportedAuthSchemes;
        this.identityResolvers = source.identityResolvers;
        this.retryStrategy = source.retryStrategy;
        this.retryScope = source.retryScope;
        this.pipeline = source.pipeline;
        this.eventStreamWriter = operation.inputEventBuilderSupplier() != null
                ? ProtocolEventStreamWriter.of(newInput.getMemberValue(operation.inputStreamMember()))
                : null;
    }

    /**
     * Set the retry token for the call, keeping {@link CallContext#RETRY_TOKEN} in sync so interceptors observe the
     * token in effect for the current attempt.
     *
     * @param retryToken the token to associate with the call, or {@code null} once the token is released.
     */
    void setRetryToken(RetryToken retryToken) {
        this.retryToken = retryToken;
        this.context.put(CallContext.RETRY_TOKEN, retryToken);
    }

    /**
     * Check if a retry is disallowed for this call.
     *
     * <p>Currently only looks at whether a non-replayable stream is used in the input.
     *
     * @return true if retries are disallowed.
     */
    boolean isRetryDisallowed() {
        var inputStream = operation.inputStreamMember();
        if (inputStream != null && inputStream.type() != ShapeType.UNION) {
            // Only tell the call that retries are disallowed if the stream is not replayable.
            DataStream stream = input.getMemberValue(inputStream);
            return !stream.isReplayable();
        }
        return false;
    }
}
