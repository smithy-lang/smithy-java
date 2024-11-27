/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A mock client used to execute protocol tests.
 */
final class MockClient extends Client {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(MockClient.class);

    private MockClient(Builder builder) {
        super(builder);
    }

    /**
     * Executes a client request, ignoring any server-side failures.
     */
    public <I extends SerializableStruct, O extends SerializableStruct> O clientRequest(
        I input,
        ApiOperation<I, O> operation,
        RequestOverrideConfig overrideConfig
    ) {
        try {
            return call(input, operation, overrideConfig).exceptionallyCompose(exc -> {
                if (exc instanceof CompletionException ce
                    && ce.getCause() instanceof ApiException apiException
                    && apiException.getFault().equals(ApiException.Fault.SERVER)
                ) {
                    LOGGER.debug("Ignoring expected exception", apiException);
                    return CompletableFuture.completedFuture(null);
                } else {
                    LOGGER.error("Encountered Unexpected exception", exc);
                    return CompletableFuture.failedFuture(exc);
                }
            }).join();
        } catch (CompletionException e) {
            throw unwrap(e);
        }
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder extends Client.Builder<MockClient, Builder> {
        @Override
        public MockClient build() {
            configBuilder().protocol(new PlaceHolderProtocol<>(HttpRequest.class, HttpResponse.class));
            configBuilder().transport(new PlaceHolderTransport<>(HttpRequest.class, HttpResponse.class));
            configBuilder().authSchemeResolver(AuthSchemeResolver.NO_AUTH);
            configBuilder().endpointResolver(EndpointResolver.staticEndpoint("http://example.com"));
            return new MockClient(this);
        }
    }

    /**
     * Placeholder protocol that allows us to instantiate a client, but that we expect to override on each request.
     */
    private record PlaceHolderProtocol<Req, Res>(Class<Req> requestClass, Class<Res> responseClass) implements
        ClientProtocol<Req, Res> {
        @Override
        public String id() {
            return "placeholder";
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> Req createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            URI endpoint
        ) {
            throw new UnsupportedOperationException("Placeholder protocol must be overridden");
        }

        @Override
        public Req setServiceEndpoint(Req request, Endpoint endpoint) {
            throw new UnsupportedOperationException("Placeholder protocol must be overridden");
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            Req request,
            Res res
        ) {
            throw new UnsupportedOperationException("Placeholder protocol must be overridden");
        }
    }

    /**
     * Placeholder transport that allows us to instantiate a client, but that we expect to override on each request.
     */
    static final class PlaceHolderTransport<Req, Res> implements ClientTransport<Req, Res> {

        private ClientTransport<Req, Res> transport;
        private final Class<Req> req;
        private final Class<Res> res;

        public PlaceHolderTransport(Class<Req> req, Class<Res> res) {
            this.req = req;
            this.res = res;
        }

        public void setTransport(ClientTransport<Req, Res> transport) {
            this.transport = transport;
        }

        private ClientTransport<Req, Res> getTransport() {
            return Objects.requireNonNull(transport, "Placeholder transport must be overridden");
        }

        @Override
        public CompletableFuture<Res> send(Context context, Req request) {
            return getTransport().send(context, request);
        }

        @Override
        public Class<Req> requestClass() {
            return req;
        }

        @Override
        public Class<Res> responseClass() {
            return res;
        }
    }
}
