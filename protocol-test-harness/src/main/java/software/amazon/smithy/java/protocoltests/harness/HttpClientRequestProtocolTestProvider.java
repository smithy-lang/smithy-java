/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.plugins.InjectIdempotencyTokenPlugin;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
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
    @SuppressWarnings("unchecked")
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
                                .flatMap(testCase -> {
                                    if (filter.skipOperation(operation.id()) || filter.skipTestCase(testCase)) {
                                        return Stream.of(new IgnoredTestCase(testCase.getId()));
                                    }
                                    // Run each request test through the codegen model and (when available) the
                                    // document-backed dynamic model.
                                    return TestModes.available(operation)
                                            .map(mode -> {
                                                var name = testCase.getId() + " [" + mode.label() + "]";
                                                if (filter.skipTestCase(testCase, mode)) {
                                                    return new IgnoredTestCase(name);
                                                }
                                                try {
                                                    return buildContext(store, operation, testCase, mode);
                                                } catch (RuntimeException e) {
                                                    return new FailedGenerationTestCase(name, e);
                                                }
                                            });
                                }));
    }

    @SuppressWarnings("unchecked")
    private TestTemplateInvocationContext buildContext(
            ProtocolTestExtension.SharedClientTestData store,
            HttpTestOperation operation,
            HttpRequestTestCase testCase,
            TestMode mode
    ) {
        var apiOperation = operation.operationModel(mode);
        var testProtocol = store.getProtocol(testCase.getProtocol());
        var testResolver = testCase.getAuthScheme().isEmpty()
                ? AuthSchemeResolver.NO_AUTH
                : (AuthSchemeResolver) p -> List.of(new AuthSchemeOption(testCase.getAuthScheme().get()));
        var testTransport = new TestTransport();

        var overrideBuilder = RequestOverrideConfig.builder()
                .protocol(testProtocol)
                .authSchemeResolver(testResolver);
        if (testCase.getHost().isPresent()) {
            overrideBuilder.endpointResolver(
                    EndpointResolver.staticEndpoint("https://" + testCase.getHost().get()));
        }

        var inputBuilder = apiOperation.inputBuilder();
        new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                .deserializeInto(inputBuilder);

        // Add fixed idempotency token provider for protocol tests.
        if (apiOperation.idempotencyTokenMember() != null) {
            overrideBuilder.putConfig(
                    InjectIdempotencyTokenPlugin.IDEMPOTENCY_TOKEN_PROVIDER,
                    "00000000-0000-4000-8000-000000000000");
        }

        return new RequestTestInvocationContext(
                testCase,
                mode,
                store.mockClient(),
                apiOperation,
                inputBuilder.build(),
                overrideBuilder.build(),
                testTransport);
    }

    record RequestTestInvocationContext(
            HttpRequestTestCase testCase,
            TestMode mode,
            MockClient mockClient,
            ApiOperation apiOperation,
            SerializableStruct input,
            RequestOverrideConfig overrideConfig,
            TestTransport testTransport) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId() + " [" + mode.label() + "]";
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
                            return DataStream.ofString(testCase.getBody().get(),
                                    testCase.getBodyMediaType().orElse(null));
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
                            // Bind this test's transport just before sending. This must happen at execution time, not
                            // when the invocation context is built: contexts for every test/mode are generated up
                            // front, so binding during generation would let the last one win and every test would
                            // capture the same request.
                            @SuppressWarnings("unchecked")
                            var placeholderTransport =
                                    (MockClient.PlaceHolderTransport<HttpRequest, HttpResponse>) mockClient
                                            .config()
                                            .transport();
                            placeholderTransport.setTransport(testTransport);
                            mockClient.clientRequest(input, apiOperation, overrideConfig);
                            var request = testTransport.getCapturedRequest();
                            Assertions.assertUriEquals(testCase, request.uri());
                            testCase.getResolvedHost()
                                    .ifPresent(resolvedHost -> Assertions.assertHostEquals(request, resolvedHost));
                            Assertions.assertHeadersEqual(request, testCase.getHeaders());
                            return request.body();
                        }
                    });
        }
    }

    private static final class TestTransport implements ClientTransport<HttpRequest, HttpResponse> {
        private static final HttpResponse exceptionalResponse = HttpResponse.create()
                .setStatusCode(555)
                .toUnmodifiable();

        private HttpRequest capturedRequest;

        public HttpRequest getCapturedRequest() {
            return capturedRequest;
        }

        @Override
        public HttpResponse send(Context context, HttpRequest request) {
            this.capturedRequest = request;
            return exceptionalResponse;
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
