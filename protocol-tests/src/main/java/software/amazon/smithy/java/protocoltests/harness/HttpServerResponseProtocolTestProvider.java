/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;

public class HttpServerResponseProtocolTestProvider extends
    ProtocolTestProvider<HttpServerResponseTests, ProtocolTestExtension.SharedServerTestData> {

    @Override
    protected Class<HttpServerResponseTests> getAnnotationType() {
        return HttpServerResponseTests.class;
    }

    @Override
    protected Class<ProtocolTestExtension.SharedServerTestData> getSharedTestDataType() {
        return ProtocolTestExtension.SharedServerTestData.class;
    }

    @Override
    protected Stream<TestTemplateInvocationContext> generateProtocolTests(
        ProtocolTestExtension.SharedServerTestData testData,
        HttpServerResponseTests annotation,
        TestFilter filter
    ) {
        var mockClient = ServerTestClient.get(testData.endpoint());
        List<TestTemplateInvocationContext> invocationContexts = new ArrayList<>();
        for (var serverTestOperation : testData.testOperations()) {
            var testOperation = serverTestOperation.operation();
            boolean shouldSkip = filter.skipOperation(testOperation.id());
            for (var testCase : testOperation.responseTestCases()) {
                Map<String, List<String>> headers = new HashMap<>();
                headers.put("x-protocol-test-protocol-id", List.of(testCase.getProtocol().toString()));
                headers.put("x-protocol-test-service", List.of(testOperation.serviceId().toString()));
                headers.put("x-protocol-test-operation", List.of(testOperation.id().toString()));
                headers.put("content-length", List.of("0"));
                headers.put("content-type", List.of("application/json"));

                var request = SmithyHttpRequest.builder()
                    .httpVersion(SmithyHttpVersion.HTTP_1_1)
                    .body(DataStream.ofBytes(new byte[0]))
                    .uri(testData.endpoint())
                    .headers(HttpHeaders.of(headers))
                    .method("POST")
                    .build();
                invocationContexts.add(
                    new ServerResponseInvocationContext(
                        testCase,
                        (ApiOperation<SerializableStruct, SerializableStruct>) testOperation.operationModel(),
                        serverTestOperation.mockOperation(),
                        mockClient,
                        request,
                        shouldSkip || filter.skipTestCase(testCase)
                    )
                );
            }

        }
        return invocationContexts.stream();
    }

    private record ServerResponseInvocationContext(
        HttpResponseTestCase testCase,
        ApiOperation<SerializableStruct, SerializableStruct> operationModel,
        MockOperation mockOperation,
        ServerTestClient client,
        SmithyHttpRequest request,
        boolean shouldSkip
    ) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(
                (ExecutionCondition) context -> shouldSkip
                    ? ConditionEvaluationResult.disabled("Skipping filtered test")
                    : ConditionEvaluationResult.enabled(""),
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
                        System.out.println(testCase.getId());
                        var outputBuilder = operationModel.outputBuilder();
                        new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                            .deserializeInto(outputBuilder);
                        mockOperation.setResponse(outputBuilder.build());
                        SmithyHttpResponse response = client.sendRequest(request);
                        Assertions.assertHeadersEqual(response, testCase.getHeaders());
                        assertEquals(testCase.getCode(), response.statusCode());
                        return response.body();
                    }
                }
            );
        }
    }
}
