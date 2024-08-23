/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.http.auth.scheme.sigv4;

import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test cases are duplicated from:
 * https://github.com/awslabs/aws-c-auth/tree/main/tests/aws-signing-test-suite/v4
 */
public class SigV4Tests {
    @ParameterizedTest(name = "{0}")
    @MethodSource("source")
    public void testRunner(String filename, Callable<SigV4TestRunner.Result> callable) throws Exception {
        callable.call();
    }

    public static Stream<?> source() {
        return SigV4TestRunner.defaultParameterizedTestSource(SigV4Tests.class);
    }
}
