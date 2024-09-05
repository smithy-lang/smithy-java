/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
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
    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpClientRequestProtocolTestProvider.class);

    @Override
    public Class<HttpClientRequestTests> getAnnotationType() {
        return HttpClientRequestTests.class;
    }

    @Override
    protected Stream<TestTemplateInvocationContext> generateProtocolTests(
        ProtocolTestExtension.SharedTestData store,
        TestFilter filter
    ) {
        List<TestTemplateInvocationContext> tests = new ArrayList<>();
        for (var operation : store.operations()) {
            if (filter.skipOperation(operation.id())) {
                LOGGER.debug("Skipping operation {}", operation.id());
                continue;
            }
            var operationModel = operation.operationModel();
            for (var testCase : operation.requestTestCases()) {
                if (filter.skipTestCase(testCase, AppliesTo.CLIENT)) {
                    LOGGER.debug("Skipping testCase {}", testCase.getId());
                    continue;
                }
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
                    overrideBuilder = overrideBuilder.endpoint("https://" + testCase.getHost().get());
                }

                var inputBuilder = operationModel.inputBuilder();
                new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                    .deserializeInto(inputBuilder);

                tests.add(
                    new RequestTestInvocationContext(
                        testCase,
                        store.mockClient(),
                        operationModel,
                        inputBuilder.build(),
                        overrideBuilder.build(),
                        testTransport::getCapturedRequest
                    )
                );
            }
        }
        return tests.stream();
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
                Assertions.assertBodyEquals(request, testCase.getBody().orElse(""));
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
