/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.java.runtime.http.api.HttpMessage;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;

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
        '}'
    );

    static void assertUriEquals(URI uri, String expected) {
        assertEquals(expected, uri.getRawPath());
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
                "Mismatch for header \"%s\"".formatted(headerEntry.getKey())
            );
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
