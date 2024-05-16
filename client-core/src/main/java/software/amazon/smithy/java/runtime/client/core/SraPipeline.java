/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

/**
 * Handles the requests/response pipeline to turn an input into a request, emit the appropriate SRA interceptors,
 * auth resolution, send requests using a wire transport, deserialize responses, and error handling.
 *
 * <p>This class is typically used by SRA-compliant {@link ClientTransport}s to implement a transport.
 *
 * @param <I> Input to send.
 * @param <O> Output to deserialize.
 * @param <RequestT> Request type to send.
 * @param <ResponseT> Response type to receive.
 */
// TODO: Should more of this class be separated out into pieces (like resolving auth)?
// TODO: Implement the real SRA interceptors workflow and error handling.
public final class SraPipeline<I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> {

    private static final System.Logger LOGGER = System.getLogger(SraPipeline.class.getName());
    private static final URI UNRESOLVED;

    static {
        try {
            UNRESOLVED = new URI("/");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final ClientCall<I, O> call;
    private final ClientProtocol<RequestT, ResponseT> protocol;
    private final Context.Key<RequestT> requestKey;
    private final Context.Key<ResponseT> responseKey;
    private final Function<RequestT, CompletableFuture<ResponseT>> wireTransport;

    private SraPipeline(
        ClientCall<I, O> call,
        ClientProtocol<RequestT, ResponseT> protocol,
        Function<RequestT, CompletableFuture<ResponseT>> wireTransport
    ) {
        this.call = call;
        this.protocol = protocol;
        this.wireTransport = wireTransport;
        this.requestKey = protocol.requestKey();
        this.responseKey = protocol.responseKey();
    }

    public static <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> CompletableFuture<O> send(
        ClientCall<I, O> call,
        ClientProtocol<RequestT, ResponseT> protocol,
        Function<RequestT, CompletableFuture<ResponseT>> wireTransport
    ) {
        return new SraPipeline<>(call, protocol, wireTransport).send();
    }

    private CompletableFuture<O> send() {
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

        var identity = resolvedAuthScheme.identity();
        context.put(CallContext.IDENTITY, identity);

        // TODO: what to do with supportedAuthSchemes of an endpoint?
        Endpoint endpoint = resolveEndpoint(call);
        request = protocol.setServiceEndpoint(request, endpoint);

        request = resolvedAuthScheme.sign(request);

        interceptor.readAfterSigning(context, input, Context.value(requestKey, request));

        request = interceptor.modifyBeforeTransmit(context, input, Context.value(requestKey, request)).value();
        interceptor.readBeforeTransmit(context, input, Context.value(requestKey, request));

        RequestT finalRequest = request;

        return wireTransport.apply(finalRequest)
            .thenApply(response -> deserialize(call, finalRequest, response, interceptor));
    }

    @SuppressWarnings("unchecked")
    private <I extends SerializableShape, O extends SerializableShape> ResolvedScheme<?, RequestT> resolveAuthScheme(
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
        throw new SdkException("No auth scheme could be resolved for " + call.operation().schema().id());
    }

    private <IdentityT extends Identity> Optional<ResolvedScheme<IdentityT, RequestT>> createResolvedSchema(
        IdentityResolvers identityResolvers,
        AuthScheme<RequestT, IdentityT> authScheme,
        AuthSchemeOption option
    ) {
        return authScheme.identityResolver(identityResolvers).map(identityResolver -> {
            IdentityT identity = identityResolver.resolveIdentity(option.identityProperties());
            return new ResolvedScheme<>(option, authScheme, identity);
        });
    }

    private record ResolvedScheme<IdentityT extends Identity, RequestT>(
        AuthSchemeOption option,
        AuthScheme<RequestT, IdentityT> authScheme,
        IdentityT identity
    ) {
        public RequestT sign(RequestT request) {
            return authScheme.signer().sign(request, identity, option.signerProperties());
        }
    }

    private <I extends SerializableShape, O extends SerializableShape> O deserialize(
        ClientCall<I, O> call,
        RequestT request,
        ResponseT response,
        ClientInterceptor interceptor
    ) {
        var input = call.input();
        LOGGER.log(
            System.Logger.Level.TRACE,
            "Deserializing response with %s for %s: %s",
            protocol.getClass(),
            request,
            response
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

        interceptor.readBeforeDeserialization(
            context,
            input,
            Context.value(requestKey, request),
            Context.value(responseKey, response)
        );

        var shape = protocol.deserializeResponse(call, request, modifiedResponse);
        context.put(CallContext.OUTPUT, shape);
        Either<SdkException, O> result = Either.right(shape);

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
    }

    // TODO: Add more parameters here somehow from the caller.
    private <I extends SerializableShape, O extends SerializableShape> Endpoint resolveEndpoint(ClientCall<I, O> call) {
        var operation = call.operation().schema();
        var request = EndpointResolverParams.builder().operationName(operation.id().getName()).build();
        return call.endpointResolver().resolveEndpoint(request);
    }
}
