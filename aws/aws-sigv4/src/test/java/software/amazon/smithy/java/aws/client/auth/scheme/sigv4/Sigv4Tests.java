/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test cases are duplicated from:
 * <a href="https://github.com/awslabs/aws-c-auth/tree/main/tests/aws-signing-test-suite/v4">CRT tests</a>
 *
 * <p>TODO: the following test cases are still not supported
 * <ul>
 *     <li>all un-normalized tests - This implementation does not support un-normalized configuration.</li>
 *     <li>get-header-value-multiline</li>
 *     <li>get-header-value-order</li>
 *     <li>get-header-key-duplicate</li>
 *     <li>post-header-key-case</li>
 *     <li>post-sts-header-after</li>
 *     <li>post-x-www-form-urlencoded</li>
 *     <li>get-space-normalized</li>
 *     <li>get-vanilla-query-order-encoded</li>
 * </ul>
 */
public class Sigv4Tests {
    @ParameterizedTest(name = "{0}")
    @MethodSource("source")
    public void testRunner(String filename, Callable<SigV4TestRunner.Result> callable) throws Exception {
        callable.call();
    }

    public static Stream<?> source() {
        return SigV4TestRunner.defaultParameterizedTestSource(Sigv4Tests.class);
    }
}
