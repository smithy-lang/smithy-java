/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.platform.commons.annotation.Testable;

/**
 * Annotation that allows the filtering of Protocol test operations and test cases.
 *
 * <p>A filter can be applied to a Test Class with the {@link ProtocolTest} annotation to filter all protocol tests
 * within that class or to a specific test method with a specific protocol test provider annotation (such as
 * {@link HttpClientResponseTests}) to only filter the tests supplied to that method.
 * <dl>
 *     <dt>skipTests</dt>
 *     <dd>A list of test cases to skip based on the test case ID</dd>
 *     <dt>skipOperations</dt>
 *     <dd>A list of operations to skip by shape ID. All test cases attached to these operations will be skipped</dd>
 *     <dt>tests</dt>
 *     <dd>An explicit list of tests to run, ignoring all other test. This list cannot overlap with {@code skippedTests}.
 *     <strong>Note:</strong> This filter is useful for debugging specific test cases.
 *     </dd>
 *     <dt>operations</dt>
 *     <dd>An explicit list of operations to run all tests for, ignoring all other operations. This list cannot overlap
 *     with {@code skippedOperations}.
 *     <strong>Note:</strong> This filter is useful for debugging specific test cases.
 *     }</dd>
 * </dl>
 */
@Testable
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Filter {
    /**
     * List of test case IDs to exclude from executed tests.
     */
    String[] skipTests() default {};

    /**
     * List of Operation ID's to skip. All test cases on the operation will be skipped.
     */
    String[] skipOperations() default {};

    /**
     * A list of Test IDs to run. All other tests will be skipped.
     *
     * <p>This filter can be used to
     */
    String[] tests() default {};

    /**
     * A list of Operation IDs to run the tests on. All other operations will be skipped.
     *
     * <p>This filter can be useful in limiting executed tests for debugging.
     */
    String[] operations() default {};
}
