/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;
import software.amazon.smithy.protocoltests.traits.AppliesTo;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;

/**
 * Provides client test cases for {@link HttpResponseTestCase}'s. See the {@link HttpClientResponseTests} annotation for
 * usage instructions.
 */
final class HttpClientResponseProtocolTestProvider extends ProtocolTestProvider<HttpClientResponseTests> {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpClientRequestProtocolTestProvider.class);

    @Override
    protected Class<HttpClientResponseTests> getAnnotationType() {
        return HttpClientResponseTests.class;
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
            for (var testCase : operation.responseTestCases()) {
                if (filter.skipTestCase(testCase, AppliesTo.CLIENT)) {
                    LOGGER.debug("Skipping testCase {}", testCase.getId());
                    continue;
                }

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
                var input = operationModel.inputBuilder().errorCorrection().build();
                var outputBuilder = operationModel.outputBuilder();
                new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                    .deserializeInto(outputBuilder);
                tests.add(
                    new ResponseTestInvocationContext(
                        testCase,
                        store.mockClient(),
                        operationModel,
                        input,
                        outputBuilder.build(),
                        overrideBuilder.build()
                    )
                );

            }
        }
        return tests.stream();
    }

    record ResponseTestInvocationContext(
        HttpResponseTestCase testCase,
        MockClient mockClient,
        ApiOperation apiOperation,
        SerializableStruct input,
        SerializableStruct output,
        RequestOverrideConfig overrideConfig
    ) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of((ProtocolTestParameterResolver) () -> {
                mockClient.clientRequest(input, apiOperation, overrideConfig);
                // No additional assertions are need if the request successfully completes.
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
            testCase.getBodyMediaType().ifPresent(mediaType -> headerMap.put("Content-Type", List.of(mediaType)));
            builder.headers(HttpHeaders.of(headerMap, (k, v) -> true));

            // Add request body if present;
            testCase.getBody().ifPresent(body -> {
                if (testCase.getBodyMediaType().isPresent()) {
                    builder.body(DataStream.ofString(body, testCase.getBodyMediaType().get()));
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
