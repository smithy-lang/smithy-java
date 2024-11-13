/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResult;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolverParams;
import software.amazon.smithy.java.runtime.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.InputHook;
import software.amazon.smithy.java.runtime.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.runtime.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.RetryToken;
import software.amazon.smithy.java.runtime.retries.api.TokenAcquisitionFailedException;

/**
 * Handles sending a {@link ClientCall} using a {@link ClientProtocol} and {@link ClientTransport}.
 *
 * <p>This class manages calling interceptors registered with the call.
 *
 * @param <RequestT>
 * @param <ResponseT>
 */
final class ClientPipeline<RequestT, ResponseT> {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(ClientPipeline.class);
    private static final URI UNRESOLVED;

    static {
        try {
            UNRESOLVED = new URI("/");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final ClientProtocol<RequestT, ResponseT> protocol;
    private final ClientTransport<RequestT, ResponseT> transport;

    /**
     * @param protocol Protocol used to serialize requests and deserialize responses.
     * @param transport Transport used to send requests and return responses.
     */
    ClientPipeline(ClientProtocol<RequestT, ResponseT> protocol, ClientTransport<RequestT, ResponseT> transport) {
        this.protocol = Objects.requireNonNull(protocol);
        this.transport = Objects.requireNonNull(transport);
    }

    /**
     * Attempt to create a ClientTransport from the given protocol and transport.
     *
     * @param protocol Protocol used to serialize requests and deserialize responses.
     * @param transport Transport used to send requests and return responses.
     * @throws IllegalStateException if the protocol and transport are incompatible.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static <RequestT, ResponseT> ClientPipeline<RequestT, ResponseT> of(
        ClientProtocol<?, ?> protocol,
        ClientTransport<?, ?> transport
    ) {
        validateProtocolAndTransport(protocol, transport);
        return new ClientPipeline(protocol, transport);
    }

    /**
     * Ensures that the given protocol and transport are compatible by comparing their request and response classes.
     *
     * @param protocol Protocol to check.
     * @param transport Transport to check.
     * @throws IllegalStateException if the protocol and transport use different request or response classes.
     */
    static void validateProtocolAndTransport(ClientProtocol<?, ?> protocol, ClientTransport<?, ?> transport) {
        if (protocol.requestClass() != transport.requestClass()) {
            throw new IllegalStateException("Protocol request != transport: " + protocol + " vs " + transport);
        } else if (protocol.responseClass() != transport.responseClass()) {
            throw new IllegalStateException("Protocol response != transport: " + protocol + " vs " + transport);
        }
    }

    <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> send(ClientCall<I, O> call) {
        var input = call.input;

        // 2. Interceptors: Invoke ReadBeforeExecution.
        var inputHook = new InputHook<>(call.context, input);
        call.interceptor.readBeforeExecution(inputHook);

        // 3. Interceptors: Invoke ModifyBeforeSerialization.
        input = call.interceptor.modifyBeforeSerialization(inputHook);
        inputHook = inputHook.withInput(input);

        // 4. Interceptors: Invoke ReadBeforeSerialization.
        call.interceptor.readBeforeSerialization(inputHook);

        // 5. Serialize the input message into a protocol request message.
        //    Use the UNRESOLVED URI of "/" for now, and resolve the actual endpoint later.
        RequestT request = protocol.createRequest(call.operation, input, call.context, UNRESOLVED);
        var requestHook = new RequestHook<>(call.context, input, request);

        // 6. Interceptors: Invoke ReadAfterSerialization.
        call.interceptor.readAfterSerialization(requestHook);

        // 7. Interceptors: Invoke ModifyBeforeRetryLoop.
        request = call.interceptor.modifyBeforeRetryLoop(requestHook);
        requestHook = requestHook.withRequest(request);

        return acquireRetryToken(call, requestHook);
    }

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> acquireRetryToken(
        ClientCall<I, O> call,
        RequestHook<I, RequestT> requestHook
    ) {
        try {
            // 8. RetryStrategy: Invoke AcquireRetryToken.
            //    Can potentially short-circuit the request.
            var result = call.retryStrategy.acquireInitialToken(new AcquireInitialTokenRequest(call.retryScope));
            call.retryToken = result.token();
            // Delay if the initial request is pre-emptively throttled.
            if (result.delay().toMillis() == 0) {
                return doSendOrRetry(call, requestHook);
            } else {
                return sendAfterDelay(result.delay(), call, requestHook, this::doSendOrRetry);
            }
        } catch (TokenAcquisitionFailedException e) {
            // Don't send the request if the circuit breaker prevents it.
            return CompletableFuture.failedFuture(e);
        }
    }

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> doSendOrRetry(
        ClientCall<I, O> call,
        RequestHook<I, RequestT> requestHook
    ) {
        var request = requestHook.request();

        // 8.a. Interceptors: Invoke ReadBeforeAttempt.
        call.interceptor.readBeforeAttempt(requestHook);

        // 8.b. Resolve auth scheme, sign, etc.
        request = call.interceptor.modifyBeforeSigning(requestHook);
        var finalHook = requestHook.withRequest(request);

        // 8.h. Interceptors: Invoke ReadBeforeSigning.
        call.interceptor.readBeforeSigning(finalHook);

        var resolvedAuthScheme = resolveAuthScheme(call, request);
        return resolvedAuthScheme.identity()
            .thenCompose(identityResult -> afterIdentity(call, finalHook, identityResult, resolvedAuthScheme));
    }

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> afterIdentity(
        ClientCall<I, O> call,
        RequestHook<I, RequestT> requestHook,
        IdentityResult<?> identityResult,
        ResolvedScheme<?, RequestT> resolvedAuthScheme
    ) {
        // This throws if no identity was found.
        var identity = identityResult.unwrap();
        call.context.put(CallContext.IDENTITY, identity);

        // TODO: what to do with supportedAuthSchemes of an endpoint?
        return resolveEndpoint(call)
            .thenApply(endpoint -> protocol.setServiceEndpoint(requestHook.request(), endpoint))
            .thenCompose(resolvedAuthScheme::sign)
            .thenApply(req -> {
                var updatedHook = requestHook.withRequest(req);
                call.interceptor.readAfterSigning(updatedHook);
                req = call.interceptor.modifyBeforeTransmit(updatedHook);
                // Track the used idempotency token, if any.
                setIdempotencyTokenContext(call, call.input);
                call.interceptor.readBeforeTransmit(updatedHook.withRequest(req));
                return req;
            })
            .thenCompose(
                finalRequest -> transport
                    .send(call.context, finalRequest)
                    .thenCompose(response -> deserialize(call, finalRequest, response, call.interceptor))
            );
    }

    // TODO: set a default token if it's missing.
    private static void setIdempotencyTokenContext(ClientCall<?, ?> call, SerializableStruct input) {
        var tokenMember = call.operation.idempotencyTokenMember();
        if (tokenMember != null) {
            var value = input.getMemberValue(tokenMember);
            if (value != null) {
                call.context.put(CallContext.IDEMPOTENCY_TOKEN, (String) value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <I extends SerializableStruct, O extends SerializableStruct> ResolvedScheme<?, RequestT> resolveAuthScheme(
        ClientCall<I, O> call,
        RequestT request
    ) {
        var params = AuthSchemeResolverParams.builder()
            .protocolId(protocol.id())
            .operation(call.operation)
            .context(Context.unmodifiableView(call.context))
            .build();
        var authSchemeOptions = call.authSchemeResolver.resolveAuthScheme(params);

        // Determine if the auth scheme option is actually supported.
        for (var authSchemeOption : authSchemeOptions) {
            AuthScheme<?, ?> authScheme = call.supportedAuthSchemes.get(authSchemeOption.schemeId());
            if (authScheme != null && authScheme.requestClass().isAssignableFrom(request.getClass())) {
                AuthScheme<RequestT, ?> castAuthScheme = (AuthScheme<RequestT, ?>) authScheme;
                var resolved = createResolvedSchema(
                    call.identityResolvers,
                    call.context,
                    castAuthScheme,
                    authSchemeOption
                );
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        var options = new StringJoiner(", ", "[", "]");
        for (var authSchemeOption : authSchemeOptions) {
            options.add(authSchemeOption.schemeId().toString());
        }
        throw new ApiException(
            "No auth scheme could be resolved for operation " + call.operation.schema().id()
                + "; protocol=" + protocol.id()
                + "; requestClass=" + request.getClass()
                + "; auth scheme options=" + options
        );
    }

    private <IdentityT extends Identity> ResolvedScheme<IdentityT, RequestT> createResolvedSchema(
        IdentityResolvers identityResolvers,
        Context context,
        AuthScheme<RequestT, IdentityT> authScheme,
        AuthSchemeOption option
    ) {
        var identityProperties = authScheme.getIdentityProperties(context).merge(option.identityPropertyOverrides());
        var signerProperties = authScheme.getSignerProperties(context).merge(option.signerPropertyOverrides());
        var identityResolver = authScheme.identityResolver(identityResolvers);
        if (identityResolver == null) {
            return null;
        }
        return new ResolvedScheme<>(signerProperties, authScheme, identityResolver.resolveIdentity(identityProperties));
    }

    private record ResolvedScheme<IdentityT extends Identity, RequestT>(
        AuthProperties signerProperties,
        AuthScheme<RequestT, IdentityT> authScheme,
        CompletableFuture<IdentityResult<IdentityT>> identity
    ) {
        public CompletableFuture<RequestT> sign(RequestT request) {
            return identity.thenCompose(identity -> {
                // Throws when no identity is found.
                var resolvedIdentity = identity.unwrap();
                return authScheme.signer().sign(request, resolvedIdentity, signerProperties);
            });
        }
    }

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<Endpoint> resolveEndpoint(
        ClientCall<I, O> call
    ) {
        var request = EndpointResolverParams.builder()
            .operation(call.operation)
            .inputValue(call.input)
            .context(Context.unmodifiableView(call.context))
            .build();
        return call.endpointResolver.resolveEndpoint(request);
    }

    @SuppressWarnings("unchecked")
    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserialize(
        ClientCall<I, O> call,
        RequestT request,
        ResponseT response,
        ClientInterceptor interceptor
    ) {
        var input = call.input;
        LOGGER.trace("Deserializing response with {} for {}:{}", protocol.getClass(), request, response);

        Context context = call.context;
        var responseHook = new ResponseHook<>(context, input, request, response);

        interceptor.readAfterTransmit(responseHook);

        ResponseT modifiedResponse = interceptor.modifyBeforeDeserialization(responseHook);
        responseHook = responseHook.withResponse(modifiedResponse);

        interceptor.readBeforeDeserialization(responseHook);

        return protocol.deserializeResponse(call.operation, context, call.typeRegistry, request, modifiedResponse)
            .handle((shape, thrown) -> {
                RuntimeException error = null;
                if (thrown instanceof CompletionException ce) {
                    thrown = ce.getCause() != null ? ce.getCause() : ce;
                }
                if (thrown != null) {
                    if (!(thrown instanceof RuntimeException)) {
                        return CompletableFuture.failedFuture(thrown);
                    }
                    error = (RuntimeException) thrown;
                }

                var outputHook = new OutputHook<>(context, input, request, response, shape);

                try {
                    interceptor.readAfterDeserialization(outputHook, error);
                    error = null;
                } catch (RuntimeException e) {
                    error = e;
                }

                try {
                    shape = interceptor.modifyBeforeAttemptCompletion(outputHook, error);
                    outputHook = outputHook.withOutput(shape);
                    // The expectation is that errors are re-thrown by hooks, or else they are disassociated.
                    error = null;
                } catch (RuntimeException e) {
                    error = e;
                }

                try {
                    interceptor.readAfterAttempt(outputHook, error);
                    error = null;
                } catch (RuntimeException e) {
                    error = e;
                }

                // 9.a If error is a retryable failure:
                if (error != null && !call.isRetryDisallowed()) {
                    try {
                        // If it's retryable, keep retrying and jump to step 8a.
                        var acquireRequest = new RefreshRetryTokenRequest(call.retryToken, error, null);
                        var acquireResult = call.retryStrategy.refreshRetryToken(acquireRequest);
                        return retry(call, request, acquireResult.token(), acquireResult.delay());
                    } catch (TokenAcquisitionFailedException tafe) {
                        // 9.b If InterceptorContext.response() is an unretryable failure, continue to step 10.
                        LOGGER.debug("Cannot acquire a retry token: {}", tafe);
                    }
                }

                // Clear out the retry token.
                var token = call.retryToken;
                call.retryToken = null;

                // 9.c.i If successful: RetryStrategy: Invoke RecordSuccess.
                if (error == null) {
                    try {
                        call.retryStrategy.recordSuccess(new RecordSuccessRequest(token));
                    } catch (RuntimeException e) {
                        error = e;
                    }
                }

                // 10. Interceptors: Invoke ModifyBeforeCompletion. (End of retry loop).
                try {
                    shape = interceptor.modifyBeforeCompletion(outputHook, error);
                    outputHook = outputHook.withOutput(shape);
                    error = null;
                } catch (RuntimeException e) {
                    error = e;
                }

                // TODO: 11. TraceProbe: Invoke DispatchEvents.

                // 12. Interceptors: Invoke ReadAfterExecution.
                interceptor.readAfterExecution(outputHook, error);

                return CompletableFuture.completedFuture(outputHook.output());
            })
            .thenCompose(o -> (CompletionStage<O>) o);
    }

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> retry(
        ClientCall<I, O> call,
        RequestT request,
        RetryToken retryToken,
        Duration after
    ) {
        // Associate the retry token with the call.
        call.retryToken = retryToken;
        // Adjust the current retry count on the context (e.g., protocols can use this to add retry headers).
        call.context.put(CallContext.RETRY_ATTEMPT, ++call.retryCount);

        var requestHook = new RequestHook<>(call.context, call.input, request);

        if (after.toMillis() == 0) {
            // Immediate retry.
            return doSendOrRetry(call, requestHook);
        } else {
            // Retry after a delay.
            return sendAfterDelay(after, call, requestHook, this::doSendOrRetry);
        }
    }

    private static <T, V, I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<T> sendAfterDelay(
        Duration after,
        ClientCall<I, O> call,
        V value,
        BiFunction<ClientCall<I, O>, V, CompletableFuture<T>> result
    ) {
        var millis = after.toMillis();
        if (millis <= 0) {
            throw new IllegalArgumentException("Send after delay duration is <= 0: " + after);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        SCHEDULER.schedule(() -> {
            try {
                result.apply(call, value).thenApply(future::complete);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, millis, TimeUnit.MILLISECONDS);
        return future;
    }
}
