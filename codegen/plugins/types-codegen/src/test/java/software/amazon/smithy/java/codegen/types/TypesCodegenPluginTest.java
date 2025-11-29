/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.smithy.java.codegen.test.PluginTestRunner.addTestCasesFromUrl;

import java.util.Collection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.PluginTestRunner.TestCase;

public class TypesCodegenPluginTest {

    @ParameterizedTest(name = "[{index}] => {0}")
    @MethodSource("testCases")
    public void runTestCase(TestCase test) {
        for (var entry : test.expectedContents().entrySet()) {
            var actual = test.getActualContent(entry.getKey());
            assertThat(actual).isEqualTo(entry.getValue());
        }
    }

    public static Collection<TestCase> testCases() {
        return addTestCasesFromUrl(TypesCodegenPluginTest.class.getResource("test-cases"));
    }
}
