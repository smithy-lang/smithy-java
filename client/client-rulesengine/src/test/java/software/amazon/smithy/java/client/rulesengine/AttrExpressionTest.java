/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AttrExpressionTest {
    @ParameterizedTest
    @MethodSource("getAttrProvider")
    public void getsAttr(String template, Object value, Object expected) {
        var getAttr = AttrExpression.parse(template);
        var result = getAttr.apply(value);

        assertThat(template, result, equalTo(expected));
        assertThat(template, equalTo(getAttr.toString()));
    }

    public static List<Arguments> getAttrProvider() throws Exception {
        Map<String, Object> mapWithNull = new HashMap<>();
        mapWithNull.put("foo", null);

        return List.of(
                Arguments.of("foo", Map.of("foo", "bar"), "bar"),
                Arguments.of("foo.bar", Map.of("foo", Map.of("bar", "baz")), "baz"),
                Arguments.of("foo.bar.baz", Map.of("foo", Map.of("bar", Map.of("baz", "qux"))), "qux"),
                Arguments.of("foo.bar[0]", Map.of("foo", Map.of("bar", List.of("baz"))), "baz"),
                Arguments.of("foo[0]", Map.of("foo", List.of("bar")), "bar"),
                Arguments.of("foo", Map.of("foo", "bar"), "bar"),
                Arguments.of("isIp", new URI("https://localhost:8080"), false),
                Arguments.of("scheme", new URI("https://localhost:8080"), "https"),
                Arguments.of("foo[2]", Map.of("foo", List.of("bar")), null),
                Arguments.of("foo", null, null),
                Arguments.of("foo[0]", mapWithNull, null),
                Arguments.of("foo[0]", Map.of("foo", Map.of("bar", "baz")), null));
    }
}
