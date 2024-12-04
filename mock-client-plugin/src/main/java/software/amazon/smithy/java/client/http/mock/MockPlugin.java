/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.HttpJob;
import software.amazon.smithy.java.server.core.ProtocolResolver;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServerProtocolProvider;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Provides mocking support for clients by overriding the transport entirely and wrapping and delegating to the
 * underlying protocol of the client.
 *
 * <p>The client must use an HTTP protocol that uses {@link HttpMessageExchange}.
 *
 * <p>The client is mocked at both the protocol and transport layer to allow for mocking HTTP responses and to
 * translate pre-made output shapes into HTTP responses using a {@link ServerProtocol} found on the classpath.
 * To use this automatic translation feature, each ServerProtocol must be on the classpath.
 *
 * <p>The requests that were sent and their corresponding inputs can be queried using {@link #getRequests()}, and
 * later cleared using {@link #clearRequests()}.
 */
public final class MockPlugin implements ClientPlugin {

    // Used to pass the required context from the wrapped protocol to the wrapped transport.
    private static final Context.Key<CurrentRequest> CURRENT_REQUEST = Context.key("CURRENT_REQUEST");

    private static final Map<ShapeId, ServerProtocolProvider> SERVER_PROTOCOL_HANDLERS = ServiceLoader.load(
        ServerProtocolProvider.class,
        ProtocolResolver.class.getClassLoader()
    )
        .stream()
        .map(ServiceLoader.Provider::get)
        .collect(Collectors.toMap(ServerProtocolProvider::getProtocolId, Function.identity()));

    private final List<Function<MatcherRequest, MockedResult>> matchers = new ArrayList<>();
    private final List<MockedRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private final Service mockService;

    private MockPlugin(Builder builder) {
        this.mockService = new MockService();
        this.matchers.addAll(builder.matchers);
    }

    /**
     * Create a new MockPlugin using a builder.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configureClient(ClientConfig.Builder config) {
        config.protocol(new MockProtocol((ClientProtocol<HttpRequest, HttpResponse>) config.protocol()));
        config.transport(new MockTransport());
    }

    /**
     * Get the requests that have been intercepted by the plugin.
     *
     * @return the intercepted requests.
     */
    public List<MockedRequest> getRequests() {
        return List.copyOf(requests);
    }

    /**
     * Clear the received request list.
     */
    public void clearRequests() {
        requests.clear();
    }

    /**
     * Creates a MockPlugin.
     */
    public static final class Builder {
        private final List<Function<MatcherRequest, MockedResult>> matchers = new ArrayList<>();

        private Builder() {}

        /**
         * Create the plugin.
         *
         * @return the created MockPlugin.
         */
        public MockPlugin build() {
            return new MockPlugin(this);
        }

        /**
         * Add a matcher that mocks the response if the matcher returns a non-null value.
         *
         * <p>If null is returned, then subsequent matchers will attempt to match and intercept the request. If no
         * matcher intercepts the request, {@link NoSuchElementException} is thrown.
         *
         * @param matcher Matcher that returns a result or null based on the request.
         * @return the builder.
         */
        public Builder addMatcher(Function<MatcherRequest, MockedResult> matcher) {
            matchers.add(matcher);
            return this;
        }

        /**
         * Add a queue of mocked results to the plugin.
         *
         * <p>This will add a matcher that unconditionally attempts to return a result from the queue. It is added in
         * order relative to other matchers, meaning it can be used after more specific matchers, or even used one
         * queue after the other to drain multiple queues.
         *
         * @param resultQueue Queue to add.
         * @return the builder.
         */
        public Builder addQueue(MockQueue resultQueue) {
            matchers.add(request -> resultQueue.poll());
            return this;
        }
    }

    private final class MockProtocol implements ClientProtocol<HttpRequest, HttpResponse> {
        private final ClientProtocol<HttpRequest, HttpResponse> delegate;

        MockProtocol(ClientProtocol<HttpRequest, HttpResponse> delegate) {
            this.delegate = delegate;
        }

        @Override
        public ShapeId id() {
            return delegate.id();
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return delegate.messageExchange();
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
            ApiOperation<I, O> operation,
            I input,
            Context context,
            URI endpoint
        ) {
            var serviceOperation = Operation.of(
                operation.schema().id().getName(),
                (i, c) -> {
                    throw new UnsupportedOperationException();
                },
                operation,
                mockService
            );
            var currentRequest = new CurrentRequest(serviceOperation, new MockedRequest(input, null), delegate);
            context.put(CURRENT_REQUEST, currentRequest);
            return delegate.createRequest(operation, input, context, endpoint);
        }

        @Override
        public HttpRequest setServiceEndpoint(HttpRequest request, Endpoint endpoint) {
            return delegate.setServiceEndpoint(request, endpoint);
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
            ApiOperation<I, O> operation,
            Context context,
            TypeRegistry typeRegistry,
            HttpRequest request,
            HttpResponse response
        ) {
            return delegate.deserializeResponse(operation, context, typeRegistry, request, response);
        }
    }

    private final class MockTransport implements ClientTransport<HttpRequest, HttpResponse> {
        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }

        @Override
        public CompletableFuture<HttpResponse> send(Context context, HttpRequest request) {
            var currentRequest = context.expect(CURRENT_REQUEST);

            // Update the current context value with the potentially changed request.
            currentRequest = currentRequest.withMessage(request);
            context.put(CURRENT_REQUEST, currentRequest);

            // Track the request here rather than when the request is created because this method is called
            // more often than the request creation method when retries happen.
            requests.add(currentRequest.request());

            MockedResult result = null;
            var matcherRequest = new MatcherRequest(
                context,
                currentRequest.operation().getApiOperation(),
                currentRequest.request().input()
            );
            for (var matcher : matchers) {
                result = matcher.apply(matcherRequest);
                if (result != null) {
                    break;
                }
            }

            if (result == null) {
                throw new NoSuchElementException("No matcher matched input: " + currentRequest.request().input());
            }

            if (result instanceof MockedResult.Response res) {
                return CompletableFuture.completedFuture(res.response());
            } else if (result instanceof MockedResult.Error err) {
                return CompletableFuture.failedFuture(err.e());
            } else if (result instanceof MockedResult.Output o) {
                return replyWithMockOutput(currentRequest, o);
            } else {
                throw new IllegalStateException("Unknown result type: " + result.getClass().getName());
            }
        }
    }

    private CompletableFuture<HttpResponse> replyWithMockOutput(
        CurrentRequest currentRequest,
        MockedResult.Output output
    ) {
        var cRequest = currentRequest.request().request();
        var serverRequest = new software.amazon.smithy.java.server.core.HttpRequest(
            cRequest.headers(),
            cRequest.uri(),
            cRequest.method()
        );

        // Use the explicitly provided protocol if set, otherwise try to find the matching server protocol.
        var protocol = output.protocol();
        if (protocol == null) {
            protocol = detectServerProtocol(currentRequest.protocol().id());
        }

        serverRequest.setDataStream(cRequest.body());
        var response = new software.amazon.smithy.java.server.core.HttpResponse(HttpHeaders.ofModifiable());
        var job = new HttpJob(currentRequest.operation(), protocol, serverRequest, response);

        CompletableFuture<Void> future;
        if (output.output() instanceof RuntimeException e) {
            future = protocol.serializeError(job, e);
        } else {
            future = protocol.serializeOutput(job, output.output());
        }

        return future.thenApply(ignored -> {
            return HttpResponse.builder()
                .statusCode(response.getStatusCode())
                .headers(response.headers())
                .body(response.getSerializedValue())
                .build();
        });
    }

    private static ServerProtocol detectServerProtocol(ShapeId id) {
        var provider = SERVER_PROTOCOL_HANDLERS.get(id);
        if (provider == null) {
            throw new IllegalArgumentException("No server protocol could be found for " + id);
        }
        return provider.provideProtocolHandler(List.of());
    }
}
