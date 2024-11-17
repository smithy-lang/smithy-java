/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.*;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;
import software.amazon.smithy.java.runtime.http.api.HttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;

/**
 * Provides client test cases for {@link HttpRequestTestCase}'s. See the {@link HttpClientRequestTests} annotation for
 * usage instructions.
 */
final class HttpClientRequestProtocolTestProvider extends
    ProtocolTestProvider<HttpClientRequestTests, ProtocolTestExtension.SharedClientTestData> {

    @Override
    public Class<HttpClientRequestTests> getAnnotationType() {
        return HttpClientRequestTests.class;
    }

    @Override
    protected Class<ProtocolTestExtension.SharedClientTestData> getSharedTestDataType() {
        return ProtocolTestExtension.SharedClientTestData.class;
    }

    @Override
    protected Stream<TestTemplateInvocationContext> generateProtocolTests(
        ProtocolTestExtension.SharedClientTestData store,
        HttpClientRequestTests annotation,
        TestFilter filter
    ) {
        return store.operations()
            .stream()
            .flatMap(
                operation -> operation.requestTestCases()
                    .stream()
                    .map(testCase -> {
                        if (filter.skipOperation(operation.id()) || filter.skipTestCase(testCase)) {
                            return new IgnoredTestCase(testCase);
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
        Supplier<HttpRequest> requestSupplier
    ) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(
                new ParameterResolver() {
                    @Override
                    public boolean supportsParameter(
                        ParameterContext parameterContext,
                        ExtensionContext extensionContext
                    ) throws ParameterResolutionException {
                        return DataStream.class.isAssignableFrom(parameterContext.getParameter().getType())
                            && parameterContext.getIndex() == 0;
                    }

                    @Override
                    public Object resolveParameter(
                        ParameterContext parameterContext,
                        ExtensionContext extensionContext
                    ) throws ParameterResolutionException {
                        if (testCase.getBody().isEmpty()) {
                            return DataStream.ofEmpty();
                        }
                        // an `isBinary` property would be nice
                        if (ProtocolTestProvider.isBinaryMediaType(testCase.getBodyMediaType())) {
                            return DataStream.ofBytes(Base64.getDecoder().decode(testCase.getBody().get()));
                        }
                        return DataStream.ofString(testCase.getBody().get(), testCase.getBodyMediaType().orElse(null));
                    }
                },
                new ParameterResolver() {
                    @Override
                    public boolean supportsParameter(
                        ParameterContext parameterContext,
                        ExtensionContext extensionContext
                    ) throws ParameterResolutionException {
                        return DataStream.class.isAssignableFrom(parameterContext.getParameter().getType())
                            && parameterContext.getIndex() == 1;
                    }

                    @Override
                    public Object resolveParameter(
                        ParameterContext parameterContext,
                        ExtensionContext extensionContext
                    ) throws ParameterResolutionException {
                        mockClient.clientRequest(input, apiOperation, overrideConfig);
                        var request = requestSupplier.get();
                        Assertions.assertUriEquals(request.uri(), testCase.getUri());
                        testCase.getResolvedHost()
                            .ifPresent(resolvedHost -> Assertions.assertHostEquals(request, resolvedHost));
                        Assertions.assertHeadersEqual(request, testCase.getHeaders());
                        return request.body();
                    }
                }
            );
        }
    }

    private static final class TestTransport implements ClientTransport<HttpRequest, HttpResponse> {
        private static final HttpResponse exceptionalResponse = HttpResponse.builder()
            .statusCode(555)
            .build();

        private HttpRequest capturedRequest;

        public HttpRequest getCapturedRequest() {
            return capturedRequest;
        }

        @Override
        public CompletableFuture<HttpResponse> send(Context context, HttpRequest request) {
            this.capturedRequest = request;
            return CompletableFuture.completedFuture(exceptionalResponse);
        }

        @Override
        public Class<HttpRequest> requestClass() {
            return HttpRequest.class;
        }

        @Override
        public Class<HttpResponse> responseClass() {
            return HttpResponse.class;
        }
    }
}
