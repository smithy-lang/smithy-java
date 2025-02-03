/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.jmespath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.waiters.models.GetFoosOutput;

public class TestJMESPathMatcher {
    //    static final Document INPUT = Document.of(Map.of(
    //            "True", Document.of(true),
    //            "False", Document.of(false),
    //            "int", Document.of(1)
    //    ));
    //    static final Document OUTPUT = Document.of(Map.of(
    //            "True", Document.of(true),
    //            "False", Document.of(false),
    //            "int", Document.of(1)
    //    ));

    static List<Arguments> matchNoInputSource() {
        return List.of(
                Arguments.of("output.status", "DONE", Comparator.STRING_EQUALS, true));
    }

    @ParameterizedTest
    @MethodSource("matchNoInputSource")
    public void testMatchNoInput(
            String path,
            String expected,
            Comparator comparator,
            boolean isMatch
    ) {
        var matcher = new JMESPathPredicate(path, expected, comparator);
        assertEquals(matcher.test(new GetFoosOutput("DONE")), isMatch);
    }

    static List<Arguments> matchInputSource() {
        return List.of(
                Arguments.of("path", "expected", Comparator.STRING_EQUALS, true));
    }

    @ParameterizedTest
    @MethodSource("matchInputSource")
    public void testMatchInput(String path, String expected, Comparator comparator, boolean isMatch) {
        var matcher = new JMESPathPredicate(path, expected, comparator);
        //assertEquals(matcher.test(INPUT, OUTPUT), isMatch);
    }
}
