/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;

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
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder extends Client.Builder<MockClient, Builder> {
        @Override
        public MockClient build() {
            configBuilder().protocol(new PlaceHolderProtocol());
            configBuilder().transport(new PlaceHolderTransport());
            configBuilder().authSchemeResolver(AuthSchemeResolver.NO_AUTH);
            configBuilder().endpointResolver(EndpointResolver.staticEndpoint("http://example.com"));
            return new MockClient(this);
        }
    }

    /**
     * Placeholder protocol that allows us to instantiate a client, but that we expect to override on each request.
     */
    private static final class PlaceHolderProtocol implements ClientProtocol<Object, Object> {
        private PlaceHolderProtocol() {
        }

        @Override
        public String id() {
            return "placeholder";
        }

        @Override
        public Class<Object> requestClass() {
            return Object.class;
        }

        @Override
        public Class<Object> responseClass() {
            return Object.class;
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> Object createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            URI endpoint
        ) {
            throw new UnsupportedOperationException("Placeholder protocol must be overridden");
        }

        @Override
        public Object setServiceEndpoint(Object request, Endpoint endpoint) {
            throw new UnsupportedOperationException("Placeholder protocol must be overridden");
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            Object request,
            Object response
        ) {
            throw new UnsupportedOperationException("Placeholder protocol must be overridden");
        }
    }

    /**
     * Placeholder transport that allows us to instantiate a client, but that we expect to override on each request.
     */
    private static final class PlaceHolderTransport implements ClientTransport<Object, Object> {

        @Override
        public CompletableFuture<Object> send(Context context, Object request) {
            throw new UnsupportedOperationException("Placeholder transport must be overridden");
        }

        @Override
        public Class<Object> requestClass() {
            return Object.class;
        }

        @Override
        public Class<Object> responseClass() {
            return Object.class;
        }
    }
}
