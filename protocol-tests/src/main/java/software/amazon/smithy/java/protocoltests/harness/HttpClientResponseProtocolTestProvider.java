/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;

/**
 * Provides client test cases for {@link HttpResponseTestCase}'s. See the {@link HttpClientResponseTests} annotation for
 * usage instructions.
 */
final class HttpClientResponseProtocolTestProvider extends
        ProtocolTestProvider<HttpClientResponseTests, ProtocolTestExtension.SharedClientTestData> {

    @Override
    protected Class<HttpClientResponseTests> getAnnotationType() {
        return HttpClientResponseTests.class;
    }

    @Override
    protected Class<ProtocolTestExtension.SharedClientTestData> getSharedTestDataType() {
        return ProtocolTestExtension.SharedClientTestData.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Stream<TestTemplateInvocationContext> generateProtocolTests(
            ProtocolTestExtension.SharedClientTestData store,
            HttpClientResponseTests annotation,
            TestFilter filter
    ) {
        return store.operations()
                .stream()
                .flatMap(
                        operation -> operation.responseTestCases()
                                .stream()
                                .map(protocolTestCase -> {
                                    var testCase = protocolTestCase.responseTestCase();
                                    if (filter.skipOperation(operation.id()) || filter.skipTestCase(testCase)) {
                                        return new IgnoredTestCase(testCase);
                                    }
                                    boolean isErrorTestCase = protocolTestCase.isErrorTest();
                                    // Get specific values to use for this test case's context
                                    var testProtocol = store.getProtocol(testCase.getProtocol());
                                    var testResolver = testCase.getAuthScheme().isEmpty()
                                            ? AuthSchemeResolver.NO_AUTH
                                            : (AuthSchemeResolver) p -> List
                                                    .of(new AuthSchemeOption(testCase.getAuthScheme().get()));
                                    var testTransport = new TestTransport(testCase);

                                    var placeholderTransport =
                                            (MockClient.PlaceHolderTransport<HttpRequest, HttpResponse>) store
                                                    .mockClient()
                                                    .config()
                                                    .transport();
                                    placeholderTransport.setTransport(testTransport);

                                    var overrideBuilder = RequestOverrideConfig.builder()
                                            .protocol(testProtocol)
                                            .authSchemeResolver(testResolver);
                                    var input = operation.operationModel().inputBuilder().errorCorrection().build();
                                    var outputBuilder = protocolTestCase.outputBuilder().get();
                                    new ProtocolTestDocument(testCase.getParams(),
                                            testCase.getBodyMediaType().orElse(null))
                                            .deserializeInto(outputBuilder);
                                    return new ResponseTestInvocationContext(
                                            testCase,
                                            store.mockClient(),
                                            operation.operationModel(),
                                            input,
                                            outputBuilder.build(),
                                            overrideBuilder.build(),
                                            isErrorTestCase);
                                }));
    }

    record ResponseTestInvocationContext(
            HttpResponseTestCase testCase,
            MockClient mockClient,
            ApiOperation apiOperation,
            SerializableStruct input,
            SerializableStruct expectedOutput,
            RequestOverrideConfig overrideConfig,
            boolean isErrorTestCase) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of((ProtocolTestParameterResolver) () -> {
                SerializableStruct actualOutput;
                try {
                    actualOutput = mockClient.clientRequest(input, apiOperation, overrideConfig);
                    if (isErrorTestCase) {
                        fail("Expected an exception but got a successful response %s", actualOutput);
                    }
                } catch (Exception e) {
                    if (isErrorTestCase && e instanceof ModeledApiException mae) {
                        actualOutput = mae;
                    } else {
                        throw e;
                    }
                }
                assertThat(actualOutput)
                        .usingRecursiveComparison(ComparisonUtils.getComparisonConfig())
                        .isEqualTo(expectedOutput);
            });
        }
    }

    private record TestTransport(HttpResponseTestCase testCase) implements
            ClientTransport<HttpRequest, HttpResponse> {

        @Override
        public CompletableFuture<HttpResponse> send(Context context, HttpRequest request) {
            var builder = HttpResponse.builder()
                    .httpVersion(HttpVersion.HTTP_1_1)
                    .statusCode(testCase.getCode());

            // Add headers
            Map<String, List<String>> headerMap = new HashMap<>();
            for (var headerEntry : testCase.getHeaders().entrySet()) {
                headerMap.put(headerEntry.getKey(), List.of(headerEntry.getValue()));
            }
            testCase.getBodyMediaType().ifPresent(mediaType -> headerMap.put("content-type", List.of(mediaType)));
            builder.headers(HttpHeaders.of(headerMap));

            // Add request body if present;
            testCase.getBody().ifPresent(body -> {
                if (testCase.getBodyMediaType().isPresent()) {
                    var type = testCase.getBodyMediaType().get();
                    if (ProtocolTestProvider.isBinaryMediaType(type)) {
                        builder.body(DataStream.ofBytes(Base64.getDecoder().decode(body), type));
                    } else {
                        builder.body(DataStream.ofString(body, type));
                    }
                } else {
                    builder.body(DataStream.ofString(body));
                }
            });

            return CompletableFuture.completedFuture(builder.build());
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
