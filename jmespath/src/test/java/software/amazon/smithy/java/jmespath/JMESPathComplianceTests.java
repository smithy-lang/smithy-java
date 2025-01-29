/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class JMESPathComplianceTests {
    @ParameterizedTest(name = "{0}")
    @MethodSource("source")
    public void testRunner(String filename, Runnable callable) throws Exception {
        callable.run();
    }

    public static Stream<?> source() {
        return ComplianceTestRunner.defaultParameterizedTestSource(JMESPathComplianceTests.class);
    }
}
