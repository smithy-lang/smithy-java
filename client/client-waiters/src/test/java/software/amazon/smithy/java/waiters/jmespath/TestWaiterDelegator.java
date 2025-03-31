/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.jmespath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.jmespath.JmespathExpression;

public class TestWaiterDelegator {

    static List<Arguments> delegatorSource() {
        return List.of(
                Arguments.of("foo", null),
                Arguments.of("input.foo", "value"),
                Arguments.of("bar", "value"),
                Arguments.of("output.bar", "value"));
    }

    @ParameterizedTest
    @MethodSource("delegatorSource")
    void testDelegator(String str, String expected) {
        var input = Document.of(Map.of("foo", Document.of("value")));
        var output = Document.of(Map.of(
                "bar",
                Document.of("value"),
                "baz",
                Document.of("valve")));

        var exp = JmespathExpression.parse(str);
        var value = exp.accept(new InputOutputAwareJMESPathDocumentVisitor(input, output));

        if (expected == null) {
            assertNull(value);
        } else {
            assertEquals(expected, value.asString());
        }
    }
}
