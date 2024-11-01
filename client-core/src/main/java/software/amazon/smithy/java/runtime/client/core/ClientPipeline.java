/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
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

/**
 * Handles sending a {@link ClientCall} using a {@link ClientProtocol} and {@link ClientTransport}.
 *
 * <p>This class manages calling interceptors registered with the call.
 *
 * @param <RequestT>
 * @param <ResponseT>
 */
final class ClientPipeline<RequestT, ResponseT> {

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
    public ClientPipeline(
        ClientProtocol<RequestT, ResponseT> protocol,
        ClientTransport<RequestT, ResponseT> transport
    ) {
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
    public static <RequestT, ResponseT> ClientPipeline<RequestT, ResponseT> of(
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
    public static void validateProtocolAndTransport(ClientProtocol<?, ?> protocol, ClientTransport<?, ?> transport) {
        if (protocol.requestClass() != transport.requestClass()) {
            throw new IllegalStateException("Protocol request != transport: " + protocol + " vs " + transport);
        } else if (protocol.responseClass() != transport.responseClass()) {
            throw new IllegalStateException("Protocol response != transport: " + protocol + " vs " + transport);
        }
    }

    public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> send(
        ClientCall<I, O> call
    ) {
        var context = call.context();
        var input = call.input();
        var interceptor = call.interceptor();

        var inputHook = new InputHook<>(context, input);
        interceptor.readBeforeExecution(inputHook);
        input = interceptor.modifyBeforeSerialization(inputHook);
        inputHook = inputHook.withInput(input);

        interceptor.readBeforeSerialization(inputHook);
        // Use the UNRESOLVED URI of "/" for now, and resolve the actual endpoint later.
        RequestT request = protocol.createRequest(call.operation(), input, context, UNRESOLVED);
        var requestHook = new RequestHook<>(context, input, request);
        interceptor.readAfterSerialization(requestHook);

        request = interceptor.modifyBeforeRetryLoop(requestHook);
        requestHook = requestHook.withRequest(request);

        // Retry loop
        interceptor.readBeforeAttempt(requestHook);

        // 8.b. Resolve auth scheme, sign, etc.
        request = interceptor.modifyBeforeSigning(requestHook);
        var finalRequestHook = requestHook.withRequest(request);

        interceptor.readBeforeSigning(finalRequestHook);

        var resolvedAuthScheme = resolveAuthScheme(call, request);

        var preparedRequest = request;
        return resolvedAuthScheme.identity().thenCompose(identityResult -> {
            // This throws if no identity was found.
            var identity = identityResult.unwrap();
            context.put(CallContext.IDENTITY, identity);

            // TODO: what to do with supportedAuthSchemes of an endpoint?
            return resolveEndpoint(call)
                .thenApply(endpoint -> protocol.setServiceEndpoint(preparedRequest, endpoint))
                .thenCompose(resolvedAuthScheme::sign)
                .thenApply(req -> {
                    var reqHook = finalRequestHook.withRequest(req);
                    interceptor.readAfterSigning(reqHook);
                    req = interceptor.modifyBeforeTransmit(reqHook);
                    interceptor.readBeforeTransmit(reqHook.withRequest(req));
                    return req;
                })
                .thenCompose(finalRequest -> {
                    return transport.send(context, finalRequest).thenCompose(response -> {
                        return deserialize(call, finalRequest, response, call.interceptor());
                    });
                });
        });
    }

    @SuppressWarnings("unchecked")
    private <I extends SerializableStruct, O extends SerializableStruct> ResolvedScheme<?, RequestT> resolveAuthScheme(
        ClientCall<I, O> call,
        RequestT request
    ) {
        var params = AuthSchemeResolverParams.builder()
            .protocolId(protocol.id())
            .operation(call.operation())
            .context(Context.unmodifiableView(call.context()))
            .build();
        var authSchemeOptions = call.authSchemeResolver().resolveAuthScheme(params);

        // Determine if the auth scheme option is actually supported.
        for (var authSchemeOption : authSchemeOptions) {
            AuthScheme<?, ?> authScheme = call.supportedAuthSchemes().get(authSchemeOption.schemeId());
            if (authScheme != null && authScheme.requestClass().isAssignableFrom(request.getClass())) {
                AuthScheme<RequestT, ?> castAuthScheme = (AuthScheme<RequestT, ?>) authScheme;
                var resolved = createResolvedSchema(
                    call.identityResolvers(),
                    call.context(),
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
            "No auth scheme could be resolved for operation " + call.operation().schema().id()
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

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserialize(
        ClientCall<I, O> call,
        RequestT request,
        ResponseT response,
        ClientInterceptor interceptor
    ) {
        var input = call.input();
        LOGGER.trace("Deserializing response with {} for {}:{}", protocol.getClass(), request, response);

        Context context = call.context();
        var responseHook = new ResponseHook<>(context, input, request, response);

        interceptor.readAfterTransmit(responseHook);

        ResponseT modifiedResponse = interceptor.modifyBeforeDeserialization(responseHook);
        responseHook = responseHook.withResponse(modifiedResponse);

        interceptor.readBeforeDeserialization(responseHook);

        return protocol.deserializeResponse(call.operation(), context, call.typeRegistry(), request, modifiedResponse)
            .thenApply(shape -> {
                var outputHook = new OutputHook<>(context, input, request, response, shape);
                RuntimeException error = null;

                try {
                    interceptor.readAfterDeserialization(outputHook, null);
                } catch (RuntimeException e) {
                    error = e;
                }

                try {
                    shape = interceptor.modifyBeforeAttemptCompletion(outputHook, error);
                    outputHook = outputHook.withOutput(shape);
                } catch (RuntimeException e) {
                    error = e;
                }

                try {
                    interceptor.readAfterAttempt(outputHook, error);
                } catch (RuntimeException e) {
                    // TODO: If retryable, goto readBeforeAttempt, if not goto modifyBeforeCompletion.
                    error = e;
                }

                // End of retry loop
                try {
                    shape = interceptor.modifyBeforeCompletion(outputHook, error);
                    outputHook = outputHook.withOutput(shape);
                } catch (RuntimeException e) {
                    error = e;
                }

                interceptor.readAfterExecution(outputHook, error);

                return outputHook.output();
            });
    }

    private <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<Endpoint> resolveEndpoint(
        ClientCall<I, O> call
    ) {
        var request = EndpointResolverParams.builder()
            .operation(call.operation())
            .inputValue(call.input())
            .context(Context.unmodifiableView(call.context()))
            .build();
        return call.endpointResolver().resolveEndpoint(request);
    }
}
