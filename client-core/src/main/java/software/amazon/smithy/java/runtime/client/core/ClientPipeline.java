/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointResolverParams;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.Either;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * Handles sending a {@link ClientCall} using a {@link ClientProtocol} and {@link ClientTransport}.
 *
 * <p>This class manages calling interceptors registered with the call.
 *
 * @param <RequestT>
 * @param <ResponseT>
 */
public final class ClientPipeline<RequestT, ResponseT> {

    private static final System.Logger LOGGER = System.getLogger(ClientPipeline.class.getName());
    private static final URI UNRESOLVED;

    static {
        try {
            UNRESOLVED = new URI("/");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final ClientProtocol<RequestT, ResponseT> protocol;
    private final ClientTransport transport;
    private final Context.Key<RequestT> requestKey;
    private final Context.Key<ResponseT> responseKey;

    private ClientPipeline(
        ClientProtocol<RequestT, ResponseT> protocol,
        ClientTransport transport
    ) {
        this.protocol = Objects.requireNonNull(protocol);
        this.transport = Objects.requireNonNull(transport);
        this.requestKey = protocol.requestKey();
        this.responseKey = protocol.responseKey();
    }

    public static <RequestT, ResponseT> ClientPipeline<RequestT, ResponseT> of(
        ClientProtocol<RequestT, ResponseT> protocol,
        ClientTransport transport
    ) {
        validateProtocolAndTransport(protocol, transport);
        return new ClientPipeline<>(protocol, transport);
    }

    /**
     * Ensures that the given protocol and transport are compatible by comparing their request and response types.
     *
     * @param protocol Protocol to check.
     * @param transport Transport to check.
     * @throws IllegalStateException if the protocol and transport use different request or response types.
     */
    public static void validateProtocolAndTransport(ClientProtocol<?, ?> protocol, ClientTransport transport) {
        if (protocol.requestKey() != transport.requestKey()) {
            throw new IllegalStateException("Protocol request key != transport: " + protocol + " vs " + transport);
        } else if (protocol.responseKey() != transport.responseKey()) {
            throw new IllegalStateException("Protocol response key != transport: " + protocol + " vs " + transport);
        }
    }

    public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> send(
        ClientCall<I, O> call
    ) {
        var context = call.context();
        var input = call.input();
        var interceptor = call.interceptor();

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

        var resolvedAuthScheme = resolveAuthScheme(call, request);

        resolvedAuthScheme.identity().whenComplete((identity, e) -> {
            // TODO: Handle if e is not null
            context.put(CallContext.IDENTITY, identity);
        });

        // TODO: what to do with supportedAuthSchemes of an endpoint?
        RequestT reqBeforeEndpointResolution = request;
        return resolveEndpoint(call)
            .thenApply(endpoint -> protocol.setServiceEndpoint(reqBeforeEndpointResolution, endpoint))
            .thenCompose(resolvedAuthScheme::sign)
            .thenApply(req -> {
                interceptor.readAfterSigning(context, input, Context.value(requestKey, req));

                req = interceptor.modifyBeforeTransmit(context, input, Context.value(requestKey, req)).value();
                interceptor.readBeforeTransmit(context, input, Context.value(requestKey, req));
                call.context().put(requestKey, req);
                return req;
            })
            .thenCompose(finalRequest -> transport.send(call).thenCompose(ignore -> {
                var response = call.context().expect(responseKey);
                return deserialize(call, call.context().expect(requestKey), response, call.interceptor());
            }));
    }

    @SuppressWarnings("unchecked")
    private <I extends SerializableStruct, O extends SerializableStruct> ResolvedScheme<?, RequestT> resolveAuthScheme(
        ClientCall<I, O> call,
        RequestT request
    ) {
        var params = AuthSchemeResolver.paramsBuilder()
            .protocolId(protocol.id())
            .operationName(call.operation().schema().id().getName())
            // TODO: .properties(?)
            .build();
        var authSchemeOptions = call.authSchemeResolver().resolveAuthScheme(params);

        // Determine if the auth scheme option is actually supported.
        for (var authSchemeOption : authSchemeOptions) {
            AuthScheme<?, ?> authScheme = call.supportedAuthSchemes().get(authSchemeOption.schemeId());
            if (authScheme != null && authScheme.requestClass().isAssignableFrom(request.getClass())) {
                AuthScheme<RequestT, ?> castAuthScheme = (AuthScheme<RequestT, ?>) authScheme;
                var resolved = createResolvedSchema(call.identityResolvers(), castAuthScheme, authSchemeOption)
                    .orElse(null);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        // TODO: Build more useful error message (to log also) indicating why some schemes were not used.
        throw new ApiException("No auth scheme could be resolved for " + call.operation().schema().id());
    }

    private <IdentityT extends Identity> Optional<ResolvedScheme<IdentityT, RequestT>> createResolvedSchema(
        IdentityResolvers identityResolvers,
        AuthScheme<RequestT, IdentityT> authScheme,
        AuthSchemeOption option
    ) {
        return authScheme.identityResolver(identityResolvers).map(identityResolver -> {
            CompletableFuture<IdentityT> identity = identityResolver.resolveIdentity(option.identityProperties());
            return new ResolvedScheme<>(option, authScheme, identity);
        });
    }

    private record ResolvedScheme<IdentityT extends Identity, RequestT>(
        AuthSchemeOption option,
        AuthScheme<RequestT, IdentityT> authScheme,
        CompletableFuture<IdentityT> identity
    ) {
        public CompletableFuture<RequestT> sign(RequestT request) {
            return identity.thenApply(
                identity -> authScheme.signer().sign(request, identity, option.signerProperties())
            );
        }
    }

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserialize(
        ClientCall<I, O> call,
        RequestT request,
        ResponseT response,
        ClientInterceptor interceptor
    ) {
        var input = call.input();
        LOGGER.log(
            System.Logger.Level.TRACE,
            () -> "Deserializing response with" + protocol.getClass() + " for " + request + ": " + response
        );

        Context context = call.context();

        interceptor.readAfterTransmit(
            context,
            input,
            Context.value(requestKey, request),
            Context.value(responseKey, response)
        );

        ResponseT modifiedResponse = interceptor
            .modifyBeforeDeserialization(
                context,
                input,
                Context.value(requestKey, request),
                Context.value(responseKey, response)
            )
            .value();
        context.put(responseKey, modifiedResponse);

        interceptor.readBeforeDeserialization(
            context,
            input,
            Context.value(requestKey, request),
            Context.value(responseKey, response)
        );

        return protocol.deserializeResponse(call, request, modifiedResponse)
            .thenApply(shape -> {
                context.put(CallContext.OUTPUT, shape);
                Either<ApiException, O> result = Either.right(shape);

                interceptor.readAfterDeserialization(
                    context,
                    input,
                    Context.value(requestKey, request),
                    Context.value(responseKey, response),
                    result
                );

                result = interceptor.modifyBeforeAttemptCompletion(
                    context,
                    input,
                    Context.value(requestKey, request),
                    Context.value(responseKey, response),
                    result
                );

                interceptor.readAfterAttempt(
                    context,
                    input,
                    Context.value(requestKey, request),
                    Context.value(responseKey, response),
                    result
                );

                // End of retry loop
                result = interceptor.modifyBeforeCompletion(
                    context,
                    input,
                    Context.value(requestKey, request),
                    Context.value(responseKey, response),
                    result
                );

                interceptor.readAfterExecution(
                    context,
                    input,
                    Context.value(requestKey, request),
                    Context.value(responseKey, response),
                    result
                );

                if (result.isRight()) {
                    return result.right();
                } else {
                    throw result.left();
                }
            });
    }

    // TODO: Add more parameters here somehow from the caller.
    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<Endpoint> resolveEndpoint(
        ClientCall<I, O> call
    ) {
        var operation = call.operation().schema();
        var request = EndpointResolverParams.builder().operationName(operation.id().getName()).build();
        return call.endpointResolver().resolveEndpoint(request);
    }
}
