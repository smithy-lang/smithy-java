/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class HttpBindingDeserializerTest {

    @ParameterizedTest
    @MethodSource("contentTypeMatchProvider")
    void contentTypeTest(String actual, String expected, int expectedResult) {
        int result = HttpBindingDeserializer.compareMediaType(actual, expected);
        Assertions.assertEquals(expectedResult, result);
    }

    static List<Arguments> contentTypeMatchProvider() {
        return List.of(
                // Mismatches (return -1)
                Arguments.of("text/plain", "application/json", -1),
                Arguments.of("application/jsonp", "application/json", -1),
                Arguments.of("application/mson", "application/json", -1),

                // Exact matches (return 1)
                Arguments.of("application/json", "application/json", 1),
                Arguments.of("application/JSON", "application/json", 1),
                Arguments.of("application/Json", "application/json", 1),
                Arguments.of("APPLICATION/JSON", "application/json", 1),
                Arguments.of("APPLICATION/json", "application/json", 1),

                // Matches with parameters (return 1)
                Arguments.of("application/json; charset=utf-8", "application/json", 1),
                Arguments.of("application/json;charset=utf-8", "application/json", 1),
                Arguments.of("application/json ; charset=utf-8", "application/json", 1),
                Arguments.of("application/json ;", "application/json", 1),
                Arguments.of("application/json ; ", "application/json", 1),
                Arguments.of("application/json ", "application/json", 1),

                // Null cases
                Arguments.of(null, null, 1), // No validation needed
                Arguments.of(null, "application/json", 0), // Missing actual Content-Type on the wire
                Arguments.of("application/json", null, 1) // No expectation
        );
    }
}
