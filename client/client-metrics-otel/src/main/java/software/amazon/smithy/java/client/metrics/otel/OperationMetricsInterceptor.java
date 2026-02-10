/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.metrics.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.ArrayList;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.client.core.interceptors.CallHook;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Interceptor to instrument calls to emit metrics using OpenTelemetry.
 */
class OperationMetricsInterceptor implements ClientInterceptor {
    static final Context.Key<MetricsState> STATE = Context.key("smithy.operation.metrics.state");
    static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
    static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
    static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("error");
    private final OperationMetrics metrics;

    /**
     * Creates a new interceptor using the given metrics containing the needed meters to emit metrics.
     *
     * @param metrics The container of meters for the metrics to emit.
     */
    OperationMetricsInterceptor(OperationMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public ClientConfig modifyBeforeCall(CallHook<?, ?> hook) {
        var operationName = hook.operation().name();
        var serviceName = hook.operation().service().name();
        var attributes = Attributes.builder()
                .put(RPC_SERVICE, serviceName)
                .put(RPC_METHOD, operationName)
                .build();
        // Instrument endpoint resolver
        var endpointResolverDelegate = hook.config().endpointResolver();
        var endpointResolver = new MetricsCollectingEndpointResolver(endpointResolverDelegate, metrics, attributes);
        var state = new MetricsState(metrics, attributes);
        // Instrument identity resolvers
        var identityResolvers = hook.config().identityResolvers();
        var newIdentityResolvers = new ArrayList<IdentityResolver<?>>(identityResolvers.size());
        for (IdentityResolver<?> identityResolver : identityResolvers) {
            newIdentityResolvers.add(new MetricsCollectingIdentityResolver<>(identityResolver, metrics, attributes));
        }
        return hook.config()
                .toBuilder()
                .putConfig(STATE, state)
                .endpointResolver(endpointResolver)
                .identityResolvers(newIdentityResolvers)
                .build();
    }

    @Override
    public void readBeforeExecution(InputHook<?, ?> hook) {
        var state = hook.context().get(STATE);
        state.startCall();
    }

    @Override
    public void readAfterExecution(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var state = hook.context().get(STATE);
        state.endCall();
    }

    @Override
    public void readBeforeAttempt(RequestHook<?, ?, ?> hook) {
        var state = hook.context().get(STATE);
        state.startAttempt();
    }

    @Override
    public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var state = hook.context().get(STATE);
        state.endAttempt(error);
    }

    @Override
    public void readBeforeSerialization(InputHook<?, ?> hook) {
        var state = hook.context().get(STATE);
        state.startSerialize();
    }

    @Override
    public void readAfterSerialization(RequestHook<?, ?, ?> hook) {
        var state = hook.context().get(STATE);
        state.endSerialize();
    }

    @Override
    public void readBeforeDeserialization(ResponseHook<?, ?, ?, ?> hook) {
        var state = hook.context().get(STATE);
        state.startDeserialize();
    }

    @Override
    public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var state = hook.context().get(STATE);
        state.endDeserialize();
    }

    @Override
    public void readBeforeSigning(RequestHook<?, ?, ?> hook) {
        var state = hook.context().get(STATE);
        state.startSigning();
    }

    @Override
    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
        var state = hook.context().get(STATE);
        state.endSigning();
    }

    @Override
    public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
        if (hook.request() instanceof HttpRequest request) {
            var contentLength = request.contentLength();
            if (contentLength != null) {
                var state = hook.context().get(STATE);
                state.requestPayloadSize(contentLength);
            }
        }
    }

    @Override
    public void readAfterTransmit(ResponseHook<?, ?, ?, ?> hook) {
        if (hook.response() instanceof HttpResponse response) {
            var contentLength = response.contentLength();
            if (contentLength != null) {
                var state = hook.context().get(STATE);
                state.responsePayloadSize(contentLength);
            }
        }
    }

    static final class MetricsCollectingEndpointResolver implements EndpointResolver {
        private final EndpointResolver delegate;
        private final OperationMetrics metrics;
        private final Attributes attributes;

        MetricsCollectingEndpointResolver(EndpointResolver delegate, OperationMetrics metrics, Attributes attributes) {
            this.delegate = delegate;
            this.metrics = metrics;
            this.attributes = attributes;
        }

        @Override
        public Endpoint resolveEndpoint(EndpointResolverParams params) {
            var startNs = System.nanoTime();
            var result = delegate.resolveEndpoint(params);
            metrics.resolveEndpointDuration().record(elapsedSecondsSinceNs(startNs), attributes);
            return result;
        }
    }

    static final class MetricsCollectingIdentityResolver<I extends Identity> implements IdentityResolver<I> {
        private final IdentityResolver<I> delegate;
        private final OperationMetrics metrics;
        private final Attributes attributes;

        @SuppressWarnings("unchecked")
        MetricsCollectingIdentityResolver(
                IdentityResolver<?> delegate,
                OperationMetrics metrics,
                Attributes attributes
        ) {
            this.delegate = (IdentityResolver<I>) delegate;
            this.metrics = metrics;
            this.attributes = attributes;
        }

        @Override
        public IdentityResult<I> resolveIdentity(Context requestProperties) {
            var startNs = System.nanoTime();
            var result = delegate.resolveIdentity(requestProperties);
            metrics.resolveIdentityDuration().record(elapsedSecondsSinceNs(startNs), attributes);
            return result;
        }

        @Override
        public Class<I> identityType() {
            return delegate.identityType();
        }
    }

    static double elapsedSecondsSinceNs(long startNs) {
        var elapsedNs = System.nanoTime() - startNs;
        return elapsedNs / 1_000_000_000.0;
    }

    static final class MetricsState {
        private final OperationMetrics metrics;
        private final Attributes attributes;
        private long callStartNs;
        private long attemptStartNs;
        private long serializeStartNs;
        private long deserializeStartNs;
        private long signingStartNs;

        MetricsState(OperationMetrics metrics, Attributes attributes) {
            this.metrics = metrics;
            this.attributes = attributes;
        }

        void startCall() {
            this.callStartNs = System.nanoTime();
        }

        void endCall() {
            metrics.rpcCallDuration().record(elapsedSecondsSinceNs(callStartNs), attributes);
        }

        void startAttempt() {
            this.attemptStartNs = System.nanoTime();
        }

        void endAttempt(RuntimeException error) {
            metrics.rpcAttemptDuration().record(elapsedSecondsSinceNs(attemptStartNs), attributes);
            metrics.rpcAttempts().add(1L, attributes);
            if (error != null) {
                var attributesWithError = attributes.toBuilder()
                        .put(EXCEPTION_TYPE, error.getClass().getName())
                        .build();
                metrics.rpcErrors().add(1L, attributesWithError);
            }
        }

        void startSerialize() {
            serializeStartNs = System.nanoTime();
        }

        void endSerialize() {
            metrics.serializationDuration().record(elapsedSecondsSinceNs(serializeStartNs), attributes);
        }

        void startDeserialize() {
            deserializeStartNs = System.nanoTime();
        }

        void endDeserialize() {
            metrics.deserializationDuration().record(elapsedSecondsSinceNs(deserializeStartNs), attributes);
        }

        void startSigning() {
            signingStartNs = System.nanoTime();
        }

        void endSigning() {
            metrics.signingDuration().record(elapsedSecondsSinceNs(signingStartNs), attributes);
        }

        void requestPayloadSize(long size) {
            metrics.requestPayloadSize().record(size, attributes);
        }

        void responsePayloadSize(long size) {
            metrics.responsePayloadSize().record(size, attributes);
        }
    }
}
