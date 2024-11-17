/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.assertj.core.api.Assertions.assertThat;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;
import software.amazon.smithy.java.runtime.http.api.HttpVersion;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;

public class HttpServerRequestProtocolTestProvider extends
    ProtocolTestProvider<HttpServerRequestTests, ProtocolTestExtension.SharedServerTestData> {

    @Override
    protected Class<HttpServerRequestTests> getAnnotationType() {
        return HttpServerRequestTests.class;
    }

    @Override
    protected Class<ProtocolTestExtension.SharedServerTestData> getSharedTestDataType() {
        return ProtocolTestExtension.SharedServerTestData.class;

    }

    @Override
    protected Stream<TestTemplateInvocationContext> generateProtocolTests(
        ProtocolTestExtension.SharedServerTestData testData,
        HttpServerRequestTests annotation,
        TestFilter testFilter
    ) {
        var mockClient = ServerTestClient.get(testData.endpoint());
        List<TestTemplateInvocationContext> invocationContexts = new ArrayList<>();
        for (var serverTestOperation : testData.testOperations()) {
            var testOperation = serverTestOperation.operation();
            boolean shouldSkip = testFilter.skipOperation(testOperation.id());
            for (var testCase : testOperation.requestTestCases()) {
                if (shouldSkip || testFilter.skipTestCase(testCase)) {
                    invocationContexts.add(new IgnoredTestCase(testCase));
                    continue;
                }
                var createUri = createUri(testData.endpoint(), testCase.getUri(), testCase.getQueryParams());
                var headers = createHeaders(testCase.getHeaders());
                Function<String, byte[]> mapper;
                if (testCase.getBodyMediaType().map(ProtocolTestProvider::isBinaryMediaType).orElse(false)) {
                    mapper = Base64.getDecoder()::decode;
                } else {
                    mapper = String::getBytes;
                }
                var request = HttpRequest.builder()
                    .uri(createUri)
                    .body(DataStream.ofBytes(testCase.getBody().map(mapper).orElse(new byte[0])))
                    .httpVersion(HttpVersion.HTTP_1_1)
                    .method(testCase.getMethod())
                    .headers(headers)
                    .build();
                invocationContexts.add(
                    new ServerRequestInvocationContext(
                        testCase,
                        (ApiOperation<SerializableStruct, SerializableStruct>) testOperation.operationModel(),
                        serverTestOperation.mockOperation(),
                        mockClient,
                        request
                    )
                );
            }
        }
        return invocationContexts.stream();
    }

    private static URI createUri(URI endpoint, String testUri, List<String> queryParams) {
        var path = endpoint.getPath();
        if (testUri != null && !testUri.isEmpty()) {
            if (path.endsWith("/") && testUri.startsWith("/")) {
                path = path + testUri.substring(1);
            } else {
                path = path + testUri;
            }
        }
        String query;
        if (queryParams == null || queryParams.isEmpty()) {
            query = "";
        } else {
            query = "?" + String.join("&", queryParams);
        }
        try {
            //Can't pass query params directly because Java URL encoding double encodes
            return URI.create(
                new URI(
                    endpoint.getScheme(),
                    endpoint.getUserInfo(),
                    endpoint.getHost(),
                    endpoint.getPort(),
                    path,
                    null,
                    null
                ) + query
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpHeaders createHeaders(Map<String, String> headers) {
        Map<String, List<String>> headerMap = new HashMap<>();
        for (var headerEntry : headers.entrySet()) {
            var key = headerEntry.getKey();
            var value = headerEntry.getValue();
            if (key.equalsIgnoreCase("x-timestamplist")
                || key.equalsIgnoreCase("x-memberhttpdate")
                || key.equalsIgnoreCase("x-defaultformat")
                || key.equalsIgnoreCase("x-targethttpdate")) {
                List<String> result = new ArrayList<>();
                String[] split = value.split(", ");
                for (int i = 0; i < split.length; i += 2) {
                    result.add(split[i] + ", " + split[i + 1]);
                }
                headerMap.put(key, result);
                continue;
            }
            if (!key.equalsIgnoreCase("x-stringlist")) {
                headerMap.put(key, List.of(value.split(", *")));
                continue;
            }
            var retVal = new ArrayList<String>();
            StringBuilder buffer = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (isHeaderDelimiter(c)) {
                    if (c == '"') {
                        if (inQuotes) {
                            retVal.add(buffer.toString().trim());
                            buffer = new StringBuilder();
                        }
                        inQuotes = !inQuotes;
                        continue;
                    }

                    if (c == '\\' && inQuotes && i + 1 < value.length()) {
                        buffer.append(value.charAt(++i));
                        continue;
                    }

                    if (inQuotes) {
                        buffer.append(c);
                        continue;
                    }

                    var next = buffer.toString().trim();
                    if (!next.isEmpty()) {
                        retVal.add(next);
                    }
                    buffer = new StringBuilder();
                } else {
                    buffer.append(c);
                }
            }
            if (!buffer.isEmpty()) {
                retVal.add(buffer.toString().trim());
            }
            headerMap.put(key, retVal);
        }
        return HttpHeaders.of(headerMap);
    }

    private record ServerRequestInvocationContext(
        HttpRequestTestCase testCase,
        ApiOperation<SerializableStruct, SerializableStruct> operationModel,
        MockOperation mockOperation,
        ServerTestClient client,
        HttpRequest request
    ) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return testCase.getId();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(
                new ProtocolTestParameterResolver() {

                    @Override
                    @SuppressFBWarnings(
                        value = "DE_MIGHT_IGNORE",
                        justification = "We need to swallow all exceptions from the client because we are only validating requests."
                    )
                    public void test() {
                        mockOperation.reset();
                        var response = operationModel.outputBuilder()
                            .errorCorrection()
                            .build();
                        mockOperation.setResponse(response);
                        try {
                            client.sendRequest(request);
                        } catch (Exception ignored) {}
                        var inputBuilder = operationModel.inputBuilder();
                        new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                            .deserializeInto(inputBuilder);
                        // Compare as documents so any datastream members are correctly compared.
                        assertThat((Object) mockOperation.getRequest())
                            // Compare objects by field
                            .usingRecursiveComparison(
                                ComparisonUtils.getComparisonConfig()
                            )
                            .isEqualTo(inputBuilder.build());
                    }
                }
            );
        }
    }

    private static final Set<Character> HEADER_DELIMS = Set.of(
        '"',
        '(',
        ')',
        ',',
        '/',
        ':',
        ';',
        '<',
        '=',
        '>',
        '?',
        '@',
        '[',
        '\\',
        ']',
        '{',
        '}'
    );

    static boolean isHeaderDelimiter(char c) {
        return HEADER_DELIMS.contains(c);
    }

}
