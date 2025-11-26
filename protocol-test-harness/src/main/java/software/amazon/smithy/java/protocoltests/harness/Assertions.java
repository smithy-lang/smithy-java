/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.java.http.api.HttpMessage;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;

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

    static void assertUriEquals(HttpRequestTestCase testCase, URI uri) {
        assertEquals(testCase.getUri(), uri.getRawPath());
        // Only evaluate query params when params are expected in test case.
        if (!testCase.getQueryParams().isEmpty()) {
            assertQueryParamsEquals(testCase.getQueryParams(), uri.getRawQuery());
        }
    }

    private static void assertQueryParamsEquals(List<String> expectedParams, String actualQuery) {
        var expectedSet = paserQueryParamsList(expectedParams);
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

    private static Set<String> paserQueryParamsList(List<String> params) {
        Set<String> result = new HashSet<>();
        for (String paramPair : params) {
            var pair = paramPair.split("=", 2);
            result.add(pair[0] + "=" + (pair.length == 2 ? pair[1] : ""));
        }
        return result;
    }

    static void assertHostEquals(HttpRequest request, String expected) {
        var hostValue = request.uri().getAuthority();
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
}
