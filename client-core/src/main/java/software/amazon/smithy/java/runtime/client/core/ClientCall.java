/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.retries.api.RetryStrategy;
import software.amazon.smithy.java.runtime.retries.api.RetryToken;
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
    final EndpointResolver endpointResolver;
    final ApiOperation<I, O> operation;
    final Context context;
    final TypeRegistry typeRegistry;
    final ClientInterceptor interceptor;
    final AuthSchemeResolver authSchemeResolver;
    final Map<ShapeId, AuthScheme<?, ?>> supportedAuthSchemes;
    final IdentityResolvers identityResolvers;
    final ExecutorService executor;

    final RetryStrategy retryStrategy;
    final String retryScope;
    RetryToken retryToken;
    int retryCount = 0;

    private ClientCall(Builder<I, O> builder) {
        input = Objects.requireNonNull(builder.input, "input is null");
        operation = Objects.requireNonNull(builder.operation, "operation is null");
        context = Objects.requireNonNull(builder.context, "context is null");
        typeRegistry = Objects.requireNonNull(builder.typeRegistry, "typeRegistry is null");
        endpointResolver = Objects.requireNonNull(builder.endpointResolver, "endpointResolver is null");
        interceptor = Objects.requireNonNullElse(builder.interceptor, ClientInterceptor.NOOP);
        authSchemeResolver = Objects.requireNonNull(builder.authSchemeResolver, "authSchemeResolver is null");
        identityResolvers = Objects.requireNonNull(builder.identityResolvers, "identityResolvers is null");
        supportedAuthSchemes = builder.supportedAuthSchemes.stream()
            .collect(Collectors.toMap(AuthScheme::schemeId, Function.identity(), (key1, key2) -> key1));

        // Retries
        retryStrategy = Objects.requireNonNull(builder.retryStrategy, "retryStrategy is null");
        retryScope = Objects.requireNonNullElse(builder.retryScope, "");
        context.put(CallContext.RETRY_MAX, retryStrategy.maxAttempts());

        //TODO fix this to not use a cached thread pool.
        executor = builder.executor == null ? Executors.newCachedThreadPool() : builder.executor;
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
        if (inputStream.type() == ShapeType.STRING || inputStream.type() == ShapeType.BLOB) {
            // Only tell the call that retries are disallowed if the stream is not replayable.
            var stream = (DataStream) input.getMemberValue(inputStream);
            return !stream.isReplayable();
        }
        return false;
    }

    static <I extends SerializableStruct, O extends SerializableStruct> Builder<I, O> builder() {
        return new Builder<>();
    }

    static final class Builder<I extends SerializableStruct, O extends SerializableStruct> {
        I input;
        EndpointResolver endpointResolver;
        ApiOperation<I, O> operation;
        Context context;
        TypeRegistry typeRegistry;
        ClientInterceptor interceptor;
        AuthSchemeResolver authSchemeResolver;
        final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        IdentityResolvers identityResolvers;
        ExecutorService executor;
        RetryStrategy retryStrategy;
        String retryScope = "";

        private Builder() {}

        ClientCall<I, O> build() {
            return new ClientCall<>(this);
        }
    }
}
