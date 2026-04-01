/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QueryStringBuilderTest {

    static Stream<Arguments> buildCases() {
        return Stream.of(
                // Single param
                Arguments.of(Map.of("a", List.of("1")), "a=1"),
                // Value with space gets encoded
                Arguments.of(Map.of("q", List.of("hello world")), "q=hello%20world"),
                // Key with special chars gets encoded
                Arguments.of(Map.of("a b", List.of("1")), "a%20b=1"),
                // Multiple values for same key
                Arguments.of(Map.of("a", List.of("1", "2")), "a=1&a=2"),
                // Empty value
                Arguments.of(Map.of("a", List.of("")), "a="));
    }

    @ParameterizedTest
    @MethodSource("buildCases")
    void build(Map<String, List<String>> params, String expected) {
        var builder = new QueryStringBuilder();
        builder.add(params);
        assertThat(builder.toString(), equalTo(expected));
    }

    @Test
    void roundTripWithParser() {
        var builder = new QueryStringBuilder();
        builder.add("key", "hello world");
        builder.add("café", "über");
        builder.add("empty", "");

        String queryString = builder.toString();
        Map<String, List<String>> parsed = QueryStringParser.parse(queryString);

        assertThat(parsed.get("key"), equalTo(List.of("hello world")));
        assertThat(parsed.get("café"), equalTo(List.of("über")));
        assertThat(parsed.get("empty"), equalTo(List.of("")));
    }

    @Test
    void addForQueryParamsSkipsDuplicateKeys() {
        var builder = new QueryStringBuilder();
        builder.add("a", "from-query");
        builder.addForQueryParams("a", "from-params");
        builder.addForQueryParams("b", "from-params");

        assertThat(builder.toString(), equalTo("a=from-query&b=from-params"));
    }

    @Test
    void clear() {
        var builder = new QueryStringBuilder();
        builder.add("a", "1");
        builder.clear();
        assertTrue(builder.isEmpty());
        assertThat(builder.toString(), equalTo(""));
    }

    @Test
    void writeToExistingSink() {
        var builder = new QueryStringBuilder();
        builder.add("a", "1");
        var sb = new StringBuilder("https://example.com?");
        builder.write(sb);
        assertThat(sb.toString(), equalTo("https://example.com?a=1"));
    }
}
