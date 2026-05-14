/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.MessageDecoder;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.http.api.HttpMessage;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;
import software.amazon.smithy.protocoltests.traits.TestFailureExpectation;
import software.amazon.smithy.protocoltests.traits.eventstream.Event;
import software.amazon.smithy.protocoltests.traits.eventstream.EventHeaderValue;
import software.amazon.smithy.protocoltests.traits.eventstream.EventStreamTestCase;

/**
 * Provides a number of testing utilities for validating protocol test results.
 */
final class Assertions {
    private Assertions() {}

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
            '}');

    static void assertUriEquals(HttpRequestTestCase testCase, SmithyUri uri) {
        assertEquals(testCase.getUri(), uri.getPath());
        // Only evaluate query params when params are expected in test case.
        if (!testCase.getQueryParams().isEmpty()) {
            assertQueryParamsEquals(testCase.getQueryParams(), uri.getQuery());
        }
    }

    private static void assertQueryParamsEquals(List<String> expectedParams, String actualQuery) {
        var expectedSet = parseQueryParamsList(expectedParams);
        var actualSet = parseQueryParamsString(actualQuery);
        assertEquals(expectedSet, actualSet, "Query parameters mismatch");
    }

    private static Set<String> parseQueryParamsString(String query) {
        Set<String> result = new HashSet<>();
        // Raw query string is in format "param1=value1&param2=value2&param3=value3"
        for (String paramPair : query.split("&")) {
            var pair = paramPair.split("=", 2);
            result.add(pair[0] + "=" + (pair.length == 2 ? pair[1] : ""));
        }
        return result;
    }

    private static Set<String> parseQueryParamsList(List<String> params) {
        Set<String> result = new HashSet<>();
        for (String paramPair : params) {
            var pair = paramPair.split("=", 2);
            result.add(pair[0] + "=" + (pair.length == 2 ? pair[1] : ""));
        }
        return result;
    }

    static void assertHostEquals(HttpRequest request, String expected) {
        var uri = request.uri();
        var hostValue = uri.getPort() >= 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        assertEquals(hostValue, expected);
    }

    static void assertHeadersEqual(HttpMessage message, Map<String, String> expected) {
        for (var headerEntry : expected.entrySet()) {
            var headerValues = message.headers().allValues(headerEntry.getKey());
            assertNotNull(headerValues);
            var converted = convertHeaderToString(headerEntry.getKey(), headerValues);
            assertEquals(
                    headerEntry.getValue(),
                    converted,
                    "Mismatch for header \"%s\"".formatted(headerEntry.getKey()));
        }
    }

    private static String convertHeaderToString(String key, List<String> values) {
        if (!key.equalsIgnoreCase("x-stringlist")) {
            return String.join(", ", values);
        }
        return values.stream().map(value -> {
            if (value.chars()
                    .anyMatch(c -> HEADER_DELIMS.contains((char) c) || Character.isWhitespace((char) c))) {
                return '"' + value.replaceAll("[\\s\"]", "\\\\$0") + '"';
            }
            return value;
        }).collect(Collectors.joining(", "));
    }

    static void assertEventHeaderEquals(String key, EventHeaderValue<?> expected, HeaderValue actual) {
        switch (expected.getType()) {
            case BOOLEAN -> assertEquals(expected.asBoolean(), actual.getBoolean(), key);
            case BYTE -> assertEquals(expected.asByte(), actual.getByte(), key);
            case SHORT -> assertEquals(expected.asShort(), actual.getShort(), key);
            case INTEGER -> assertEquals(expected.asInteger(), actual.getInteger(), key);
            case LONG -> assertEquals(expected.asLong(), actual.getLong(), key);
            case STRING -> assertEquals(expected.asString(), actual.getString(), key);
            case BLOB -> assertArrayEquals(expected.asBlob(), actual.getByteArray(), key);
            case TIMESTAMP -> assertEquals(expected.asTimestamp(), actual.getTimestamp(), key);
        }
    }

    static void assertEventStreamRequestEquals(HttpRequest request, Event event) {
        var bodyBytes = request.body().asByteBuffer();
        var decoder = new MessageDecoder();
        decoder.feed(bodyBytes.duplicate());
        var messages = decoder.getDecodedMessages();
        var message = messages.getFirst();
        var actualHeaders = message.getHeaders();
        for (var entry : event.getHeaders().entrySet()) {
            var key = entry.getKey();
            var expected = entry.getValue();
            var actual = actualHeaders.get(key);
            assertThat(actual).as("Missing header: " + key).isNotNull();
            Assertions.assertEventHeaderEquals(key, expected, actual);
        }
        for (var header : event.getForbidHeaders()) {
            assertFalse(actualHeaders.containsKey(header));
        }
        for (var header : event.getRequireHeaders()) {
            assertTrue(actualHeaders.containsKey(header));
        }
        event.getBody().ifPresent(expectedBody -> {
            assertEventStreamBodyEquals(expectedBody,
                    new String(message.getPayload(), StandardCharsets.UTF_8),
                    event.getBodyMediaType().orElse(null));
        });
    }

    static void assertInitialRequestEquals(EventStreamTestCase testCase, HttpRequest request) {
        if (testCase.getInitialRequest().isPresent()) {
            var initialRequest = testCase.getInitialRequest().get();
            assertEquals(initialRequest.expectStringMember("uri").getValue(), request.uri().getPath());
            assertEquals(initialRequest.expectStringMember("method").getValue(), request.method());
            initialRequest.getStringMember("resolvedHost").ifPresent(host -> {
                assertEquals(host.getValue(), request.uri().getHost());
            });
            var actualQueryParams = request.uri().getQuery();
            if (actualQueryParams != null) {
                assertInitialRequestQueryMatches(initialRequest, actualQueryParams);
            }
            assertInitialRequestHeaderMatches(initialRequest, request);
            initialRequest.getStringMember("body").ifPresent(bodyNode -> {
                assertEventStreamBodyEquals(bodyNode.getValue(),
                        new StringBuildingSubscriber(request.body()).getResult(),
                        initialRequest.getStringMember("bodyMediaType").map(StringNode::getValue).orElse(null));
            });
        }
    }

    private static void assertInitialRequestQueryMatches(ObjectNode initialRequest, String actualQuery) {
        initialRequest.getArrayMember("queryParams").ifPresent(params -> {
            assertQueryParamsEquals(params.getElementsAs(StringNode::getValue), actualQuery);
        });
        var queryParamSet = parseQueryParamsString(actualQuery);
        initialRequest.getArrayMember("forbidQueryParams").ifPresent(params -> {
            for (var param : params.getElementsAs(StringNode::getValue)) {
                assertFalse(queryParamSet.contains(param));
            }
        });

        initialRequest.getArrayMember("requireQueryParams").ifPresent(params -> {
            for (var param : params.getElementsAs(StringNode::getValue)) {
                assertTrue(queryParamSet.contains(param));
            }
        });
    }

    private static void assertInitialRequestHeaderMatches(ObjectNode initialRequest, HttpRequest actualRequest) {
        var actualHeaders = actualRequest.headers().map();
        initialRequest.getObjectMember("headers").ifPresent(headersNode -> {
            Map<String, String> headers = new HashMap<>();
            headersNode.getStringMap().forEach((k, v) -> headers.put(k, v.expectStringNode().getValue()));
            assertHeadersEqual(actualRequest, headers);
        });

        initialRequest.getArrayMember("forbidHeaders").ifPresent(headers -> {
            for (var header : headers.getElementsAs(StringNode::getValue)) {
                assertFalse(actualHeaders.containsKey(header));
            }
        });

        initialRequest.getArrayMember("requireHeaders").ifPresent(headers -> {
            for (var header : headers.getElementsAs(StringNode::getValue)) {
                assertTrue(actualHeaders.containsKey(header));
            }
        });
    }

    private static void assertEventStreamBodyEquals(String expectedBody, String actualBody, String bodyType) {
        if ("application/json".equals(bodyType)) {
            Node.assertEquals(Node.parse(expectedBody), Node.parse(actualBody));
        } else {
            assertEquals(expectedBody, actualBody);
        }
    }

    static void assertExpectationEquals(EventStreamTestCase testCase, Throwable e) {
        testCase.getExpectation()
                .getFailure()
                .flatMap(TestFailureExpectation::getErrorId)
                .ifPresent(errorId -> {
                    assertInstanceOf(ModeledException.class, e);
                    assertEquals(errorId.getName(),
                            ((ModeledException) e).schema().id().getName());
                });
    }
}
