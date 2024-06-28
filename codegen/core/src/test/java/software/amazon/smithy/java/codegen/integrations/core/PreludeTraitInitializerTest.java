/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.utils.AbstractCodegenFileTest;

public class PreludeTraitInitializerTest extends AbstractCodegenFileTest {
    private static final URL TEST_FILE = Objects.requireNonNull(
        PreludeTraitInitializerTest.class.getResource("prelude-trait-initializer-test.smithy")
    );

    @Override
    protected URL testFile() {
        return TEST_FILE;
    }

    static List<String> customInitializersInput() {
        return List.of(
            "new DefaultTrait(Node.from(\"string\"))",
            "LengthTrait.builder().min(10L).build()",
            "RangeTrait.builder().max(new BigDecimal(\"100\")).build()",
            "XmlNamespaceTrait.builder().uri(\"http://foo.com\").build()"
        );
    }

    @ParameterizedTest
    @MethodSource("customInitializersInput")
    void customInitializersCorrectOnInput(String expected) {
        var fileContents = getFileStringForClass("SpecialCasedInput");
        assertTrue(fileContents.contains(expected));
    }

    @Test
    void customInitializerForRetryableCorrect() {
        var fileContents = getFileStringForClass("RetryableError");
        assertTrue(fileContents.contains("RetryableTrait.builder().throttling(false).build()"));
    }
}
