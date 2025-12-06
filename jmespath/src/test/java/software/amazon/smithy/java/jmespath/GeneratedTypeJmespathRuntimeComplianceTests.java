/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.jmespath.tests.ComplianceTestRunner;

import java.util.stream.Stream;

public class GeneratedTypeJmespathRuntimeComplianceTests {
    @ParameterizedTest(name = "{0}")
    @MethodSource("source")
    public void testRunner(String filename, Runnable callable) throws Exception {
        callable.run();
    }

    public static Stream<?> source() {
        return ComplianceTestRunner.defaultParameterizedTestSource(GeneratedTypeJmespathRuntime.INSTANCE);
    }
}
