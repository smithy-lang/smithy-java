/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.model.traits.CorsTrait;

public class CorsHeadersTest {

    record TestCase(
            String name,
            String configuredOrigin,
            String requestOrigin,
            boolean shouldHaveHeaders,
            String expectedAllowOrigin) {}

    private static Stream<TestCase> corsTestCases() {
        return Stream.of(
                new TestCase(
                        "No request origin header",
                        "http://allowed-origin.com",
                        null,
                        false,
                        null),
                new TestCase(
                        "Not allowed origin",
                        "http://allowed-origin.com",
                        "http://not-allowed-origin.com",
                        false,
                        null),
                new TestCase(
                        "Multiple allowed origins",
                        "http://allowed-origin.com,http://other-allowed-origin.com",
                        "http://allowed-origin.com",
                        true,
                        "http://allowed-origin.com"),
                new TestCase(
                        "Wildcard origin",
                        "*",
                        "http://any-origin.com",
                        true,
                        "http://any-origin.com"),
                new TestCase(
                        "Exact match origin",
                        "http://allowed-origin.com",
                        "http://allowed-origin.com",
                        true,
                        "http://allowed-origin.com"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corsTestCases")
    void testCorsHeaders(TestCase testCase) throws URISyntaxException {
        // Create operation with configured CORS origin
        Operation testOperation = Operation.of(
                "TestOperation",
                (input, context) -> new TestStructs.TestOutput(),
                new TestStructs.TestApiOperation(new ApiService() {
                    @Override
                    public Schema schema() {
                        return Schema.structureBuilder(
                                CorsTrait.ID,
                                CorsTrait.builder().origin(testCase.configuredOrigin).build()).build();
                    }
                }),
                new TestStructs.TestService());

        // Create request headers
        Map<String, List<String>> headerMap =
                testCase.requestOrigin != null ? Map.of("Origin", List.of(testCase.requestOrigin)) : Map.of();
        HttpHeaders requestHeaders = HttpHeaders.of(headerMap);

        // Create request
        HttpRequest request = new HttpRequest(
                requestHeaders,
                new URI("http://test.com"),
                "GET");

        // Create response and job
        HttpResponse response = new HttpResponse(new TestStructs.TestModifiableHttpHeaders());
        ServerProtocol protocol = new TestStructs.TestServerProtocol(List.of());
        HttpJob job = new HttpJob(testOperation, protocol, request, response);

        // Apply CORS headers
        CorsHeaders.addCorsHeaders(job);

        // Verify headers
        if (testCase.shouldHaveHeaders) {
            assertContainsHeader(job.response().headers(), testCase.expectedAllowOrigin);
        } else {
            assertDoNotContainsHeader(job.response().headers());
        }
    }

    private void assertContainsHeader(ModifiableHttpHeaders responseHeaders, String allowedOrigin) {
        assertTrue(responseHeaders.hasHeader("Access-Control-Allow-Methods"));
        assertTrue(responseHeaders.hasHeader("Access-Control-Allow-Headers"));
        assertTrue(responseHeaders.hasHeader("Access-Control-Max-Age"));
        assertTrue(responseHeaders.hasHeader("Access-Control-Allow-Origin"));
        assertTrue(responseHeaders.allValues("Access-Control-Allow-Origin").contains(allowedOrigin));
    }

    private void assertDoNotContainsHeader(ModifiableHttpHeaders responseHeaders) {
        assertFalse(responseHeaders.hasHeader("Access-Control-Allow-Methods"));
        assertFalse(responseHeaders.hasHeader("Access-Control-Allow-Headers"));
        assertFalse(responseHeaders.hasHeader("Access-Control-Max-Age"));
        assertFalse(responseHeaders.hasHeader("Access-Control-Allow-Origin"));
    }
}
