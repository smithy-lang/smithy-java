/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QueryStringParserTest {

    static Stream<Arguments> parseCases() {
        return Stream.of(
                Arguments.of("a=1", Map.of("a", List.of("1"))),
                Arguments.of("a=1&b=2", Map.of("a", List.of("1"), "b", List.of("2"))),
                Arguments.of("a=1&a=2", Map.of("a", List.of("1", "2"))),
                Arguments.of("a", Map.of("a", List.of(""))),
                Arguments.of("a=", Map.of("a", List.of(""))),
                Arguments.of("q=hello%20world", Map.of("q", List.of("hello world"))),
                Arguments.of("hello%20world=1", Map.of("hello world", List.of("1"))),
                Arguments.of("a=1;b=2", Map.of("a", List.of("1"), "b", List.of("2"))),
                Arguments.of("a=b+c", Map.of("a", List.of("b+c"))),
                Arguments.of("a=1&&b=2", Map.of("a", List.of("1"), "b", List.of("2"))),
                Arguments.of("a=b=c", Map.of("a", List.of("b=c"))));
    }

    @ParameterizedTest
    @MethodSource("parseCases")
    void parse(String input, Map<String, List<String>> expected) {
        assertThat(QueryStringParser.parse(input), equalTo(expected));
    }

    @Test
    void parseNullReturnsEmptyMap() {
        assertThat(QueryStringParser.parse(null), equalTo(Map.of()));
    }

    @Test
    void parseEmptyReturnsEmptyMap() {
        assertThat(QueryStringParser.parse(""), equalTo(Map.of()));
    }

    @Test
    void visitorCanStopEarly() {
        var result = new java.util.ArrayList<String>();
        boolean completed = QueryStringParser.parse("a=1&b=2&c=3", (key, value) -> {
            result.add(key);
            return !"b".equals(key); // stop after "b"
        });
        assertThat(completed, equalTo(false));
        assertThat(result, contains("a", "b"));
    }
}
