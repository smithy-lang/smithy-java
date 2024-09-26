/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.protocoltests.traits.AppliesTo;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;

/**
 * Provides client test cases for {@link HttpRequestTestCase}'s. See the {@link HttpClientRequestTests} annotation for
 * usage instructions.
 */
final class HttpClientRequestProtocolTestProvider extends ProtocolTestProvider<HttpClientRequestTests> {

    @Override
    public Class<HttpClientRequestTests> getAnnotationType() {
        return HttpClientRequestTests.class;
    }

    @Override
    protected Stream<TestTemplateInvocationContext> generateProtocolTests(
        ProtocolTestExtension.SharedTestData store,
        TestFilter filter
    ) {
        return store.operations()
            .stream()
            .filter(op -> !filter.skipOperation(op.id()))
            .flatMap(
                operation -> operation.requestTestCases()
                    .stream()
                    .filter(testCase -> !filter.skipTestCase(testCase, AppliesTo.CLIENT))
                    .map(testCase -> {
                        var testProtocol = store.getProtocol(testCase.getProtocol());
                        var testResolver = testCase.getAuthScheme().isEmpty()
                            ? AuthSchemeResolver.NO_AUTH
                            : (AuthSchemeResolver) p -> List.of(new AuthSchemeOption(testCase.getAuthScheme().get()));
                        var testTransport = new TestTransport();
                        var overrideBuilder = RequestOverrideConfig.builder()
                            .transport(testTransport)
                            .protocol(testProtocol)
                            .authSchemeResolver(testResolver);
                        if (testCase.getHost().isPresent()) {
                            overrideBuilder.endpointResolver(
                                EndpointResolver.staticEndpoint("https://" + testCase.getHost().get())
                            );
                        }

                        var inputBuilder = operation.operationModel().inputBuilder();
                        new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                            .deserializeInto(inputBuilder);

                        return new RequestTestInvocationContext(
                            testCase,
                            store.mockClient(),
                            operation.operationModel(),
                            inputBuilder.build(),
                            overrideBuilder.build(),
                            testTransport::getCapturedRequest
                        );
                    })
            );
    }

    record RequestTestInvocationContext(
        HttpRequestTestCase testCase,
        MockClient mockClient,
        ApiOperation apiOperation,
        SerializableStruct input,
        RequestOverrideConfig overrideConfig,
        Supplier<SmithyHttpRequest> requestSupplier
    ) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of((ProtocolTestParameterResolver) () -> {
                mockClient.clientRequest(input, apiOperation, overrideConfig);
                var request = requestSupplier.get();
                Assertions.assertUriEquals(request.uri(), testCase.getUri());
                testCase.getResolvedHost()
                    .ifPresent(resolvedHost -> Assertions.assertHostEquals(request, resolvedHost));
                Assertions.assertHeadersEqual(request, testCase.getHeaders());
                Assertions.assertJsonBodyEquals(request, testCase.getBody().orElse(""));
            });
        }
    }

    private static final class TestTransport implements ClientTransport<SmithyHttpRequest, SmithyHttpResponse> {
        private static final SmithyHttpResponse exceptionalResponse = SmithyHttpResponse.builder()
            .statusCode(555)
            .build();

        private SmithyHttpRequest capturedRequest;

        public SmithyHttpRequest getCapturedRequest() {
            return capturedRequest;
        }

        @Override
        public CompletableFuture<SmithyHttpResponse> send(Context context, SmithyHttpRequest request) {
            this.capturedRequest = request;
            return CompletableFuture.completedFuture(exceptionalResponse);
        }

        @Override
        public Class<SmithyHttpRequest> requestClass() {
            return SmithyHttpRequest.class;
        }

        @Override
        public Class<SmithyHttpResponse> responseClass() {
            return SmithyHttpResponse.class;
        }
    }
}
