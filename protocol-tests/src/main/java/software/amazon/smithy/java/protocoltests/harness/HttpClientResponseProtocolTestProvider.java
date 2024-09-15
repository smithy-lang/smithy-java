/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
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
                    .map(testCase -> {
                        // Get specific values to use for this test case's context
                        var testProtocol = store.getProtocol(testCase.getProtocol());
                        var testResolver = testCase.getAuthScheme().isEmpty()
                            ? AuthSchemeResolver.NO_AUTH
                            : (AuthSchemeResolver) p -> List.of(new AuthSchemeOption(testCase.getAuthScheme().get()));
                        var testTransport = new TestTransport(testCase);
                        var overrideBuilder = RequestOverrideConfig.builder()
                            .transport(testTransport)
                            .protocol(testProtocol)
                            .authSchemeResolver(testResolver);
                        var input = operation.operationModel().inputBuilder().errorCorrection().build();
                        var outputBuilder = operation.operationModel().outputBuilder();
                        new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                            .deserializeInto(outputBuilder);
                        return new ResponseTestInvocationContext(
                            testCase,
                            store.mockClient(),
                            operation.operationModel(),
                            input,
                            outputBuilder.build(),
                            overrideBuilder.build(),
                            filter.skipOperation(operation.id()) || filter.skipTestCase(testCase, TestType.CLIENT)

                        );
                    })
            );
    }

    record ResponseTestInvocationContext(
        HttpResponseTestCase testCase,
        MockClient mockClient,
        ApiOperation apiOperation,
        SerializableStruct input,
        SerializableStruct output,
        RequestOverrideConfig overrideConfig,
        boolean shouldSkip
    ) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of((ProtocolTestParameterResolver) () -> {
                Assumptions.assumeFalse(shouldSkip);
                mockClient.clientRequest(input, apiOperation, overrideConfig);
                // No additional assertions are needed if the request successfully completes.
            });
        }
    }

    private record TestTransport(HttpResponseTestCase testCase) implements
        ClientTransport<SmithyHttpRequest, SmithyHttpResponse> {

        @Override
        public CompletableFuture<SmithyHttpResponse> send(Context context, SmithyHttpRequest request) {
            var builder = SmithyHttpResponse.builder()
                .httpVersion(SmithyHttpVersion.HTTP_1_1)
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
        public Class<SmithyHttpRequest> requestClass() {
            return SmithyHttpRequest.class;
        }

        @Override
        public Class<SmithyHttpResponse> responseClass() {
            return SmithyHttpResponse.class;
        }
    }
}
