/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import software.amazon.smithy.java.runtime.api.Endpoint;
import software.amazon.smithy.java.runtime.api.EndpointProviderRequest;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.Either;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

// TODO: actually implement the workflow for interceptors with error handling and retries.
public final class CallPipeline<RequestT, ResponseT> {

    private static final System.Logger LOGGER = System.getLogger(CallPipeline.class.getName());
    private static final URI UNRESOLVED;
    private final ClientProtocol<RequestT, ResponseT> protocol;

    static {
        try {
            UNRESOLVED = new URI("/");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public CallPipeline(ClientProtocol<RequestT, ResponseT> protocol) {
        this.protocol = protocol;
    }

    /**
     * Send the input and deserialize a response or throw errors.
     *
     * @param call Call to invoke.
     * @return Returns the deserialized response if successful.
     * @param <I> Input shape.
     * @param <O> Output shape.
     */
    public <I extends SerializableShape, O extends SerializableShape> O send(ClientCall<I, O> call) {
        var context = call.context();
        context.put(CallContext.INPUT, call.input());
        context.put(CallContext.OPERATION_SCHEMA, call.operation().schema());
        context.put(CallContext.INPUT_SCHEMA, call.operation().inputSchema());
        context.put(CallContext.OUTPUT_SCHEMA, call.operation().outputSchema());
        var timeout = context.get(CallContext.API_CALL_TIMEOUT);

        // Call the actual service in a virtual thread to support total-call timeout.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<O> result = executor.submit(() -> doSend(call));
            if (timeout == null) {
                return result.get();
            } else {
                return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private <I extends SerializableShape, O extends SerializableShape> O doSend(ClientCall<I, O> call) {
        ClientInterceptor interceptor = call.interceptor();
        var context = call.context();
        var input = call.input();
        var requestKey = protocol.requestKey();

        interceptor.readBeforeExecution(context, input);
        context.put(CallContext.INPUT, interceptor.modifyBeforeSerialization(context, input));

        interceptor.readBeforeSerialization(context, input);
        // Use the UNRESOLVED URI of "/" for now, and resolve the actual endpoint later.
        RequestT request = protocol.createRequest(call, UNRESOLVED);
        interceptor.readAfterSerialization(context, input, Context.value(requestKey, request));

        request = interceptor.modifyBeforeRetryLoop(context, input, Context.value(requestKey, request)).value();

        // Retry loop
        interceptor.readBeforeAttempt(context, input, Context.value(requestKey, request));

        // 8.b. Resolve auth scheme, sign, etc.
        request = interceptor.modifyBeforeSigning(context, input, Context.value(requestKey, request)).value();
        interceptor.readBeforeSigning(context, input, Context.value(requestKey, request));

        var resolvedIdentity = resolveIdentity(call, request);
        var identity = resolvedIdentity.identity();
        context.put(CallContext.IDENTITY, identity);

        // TODO: what to do with supportedAuthSchemes of an endpoint?
        Endpoint endpoint = resolveEndpoint(call);
        request = protocol.updateRequest(request, endpoint.uri());

        request = resolvedIdentity.sign(request);

        interceptor.readAfterSigning(context, input, Context.value(requestKey, request));

        request = interceptor.modifyBeforeTransmit(context, input, Context.value(requestKey, request)).value();
        interceptor.readBeforeTransmit(context, input, Context.value(requestKey, request));
        var response = protocol.sendRequest(call, request);
        return deserialize(call, request, response, interceptor);
    }

    @SuppressWarnings("unchecked")
    private <I extends SerializableShape, O extends SerializableShape> ResolvedScheme<?, RequestT> resolveIdentity(
            ClientCall<I, O> call,
            RequestT request) {
        var params = AuthSchemeResolver.paramsBuilder()
                .protocolId(protocol.id())
                .operationName(call.operation().schema().id().getName())
                // TODO: .properties(?)
                .build();
        var authSchemeOptions = call.authSchemeResolver().resolveAuthScheme(params);

        // Determine if the auth scheme option is actually supported.
        for (var authSchemeOption : authSchemeOptions) {
            for (var supportedAuthScheme : call.supportedAuthSchemes()) {
                if (supportedAuthScheme.schemeId().equals(authSchemeOption.schemeId())) {
                    if (supportedAuthScheme.requestType().isAssignableFrom(request.getClass())) {
                        AuthScheme<RequestT, ?> castAuthScheme = (AuthScheme<RequestT, ?>) supportedAuthScheme;
                        var resolved = createResolvedSchema(call.identityResolvers(), castAuthScheme, authSchemeOption)
                                .orElse(null);
                        if (resolved != null) {
                            return resolved;
                        }
                    }
                }
            }
        }

        // TODO: Is this right?
        throw new SdkException("No auth scheme could be resolved for " + call.operation().schema().id());
    }

    private <IdentityT extends Identity> Optional<ResolvedScheme<IdentityT, RequestT>> createResolvedSchema(
            IdentityResolvers identityResolvers,
            AuthScheme<RequestT, IdentityT> authScheme,
            AuthSchemeOption option) {
        return authScheme.identityResolver(identityResolvers).map(identityResolver -> {
            IdentityT identity = identityResolver.resolveIdentity(option.identityProperties());
            return new ResolvedScheme<>(option, authScheme, identity, authScheme.signer());
        });
    }

    private record ResolvedScheme<IdentityT extends Identity, RequestT>(AuthSchemeOption option,
            AuthScheme<RequestT, IdentityT> authScheme, IdentityT identity, Signer<RequestT, IdentityT> signer) {
        public RequestT sign(RequestT request) {
            return signer.sign(request, identity, option.signerProperties());
        }
    }
    private <I extends SerializableShape, O extends SerializableShape> O deserialize(ClientCall<I, O> call,
            RequestT request,
            ResponseT response,
            ClientInterceptor interceptor) {
        var input = call.input();
        var requestKey = protocol.requestKey();
        var responseKey = protocol.responseKey();
        LOGGER.log(System.Logger.Level.TRACE,
                () -> "Deserializing response with " + protocol.getClass() + " for " + request + ": " + response);

        Context context = call.context();

        interceptor.readAfterTransmit(context, input, Context.value(requestKey, request),
                Context.value(responseKey, response));

        ResponseT modifiedResponse = interceptor
                .modifyBeforeDeserialization(context, input, Context.value(requestKey, request),
                        Context.value(responseKey, response))
                .value();

        interceptor.readBeforeDeserialization(context, input, Context.value(requestKey, request),
                Context.value(responseKey, response));

        var shape = protocol.deserializeResponse(call, request, modifiedResponse);
        context.put(CallContext.OUTPUT, shape);
        Either<O, SdkException> result = Either.left(shape);

        interceptor.readAfterDeserialization(context, input, Context.value(requestKey, request),
                Context.value(responseKey, response), result);

        result = interceptor.modifyBeforeAttemptCompletion(context, input, Context.value(requestKey, request),
                Context.value(responseKey, response), result);

        interceptor.readAfterAttempt(context, input, Context.value(requestKey, request),
                Context.value(responseKey, response), result);

        // End of retry loop
        result = interceptor.modifyBeforeCompletion(context, input, Context.value(requestKey, request),
                Context.value(responseKey, response), result);

        interceptor.readAfterExecution(context, input, Context.value(requestKey, request),
                Context.value(responseKey, response), result);

        if (result.isLeft()) {
            return shape;
        } else {
            throw result.right();
        }
    }

    // TODO: Add more parameters here somehow from the caller.
    private <I extends SerializableShape, O extends SerializableShape> Endpoint resolveEndpoint(ClientCall<I, O> call) {
        var operation = call.operation().schema();
        var request = EndpointProviderRequest.builder().operationName(operation.id().getName()).build();
        return call.endpointProvider().resolveEndpoint(request);
    }
}
