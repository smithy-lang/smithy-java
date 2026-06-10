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
 */
@Testable
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtocolTestFilter {
    /**
     * List of test case IDs to exclude from executed tests.
     *
     * <p>Client protocol tests run in two modes: {@code codegen} (generated, strongly-typed shapes) and
     * {@code dynamic} (document-backed {@code DynamicClient} shapes). By default an entry skips a test in <em>all</em>
     * modes. To skip a test in only one mode, append the mode in brackets, matching the test's display name:
     * <pre>{@code
     *   "SomeTestId"             // skipped in every mode
     *   "SomeTestId [dynamic]"   // skipped only for the dynamic path; codegen still runs
     *   "SomeTestId [codegen]"   // skipped only for the codegen path; dynamic still runs
     * }</pre>
     */
    String[] skipTests() default {};

    /**
     * List of Operation ID's to skip. All test cases on the operation will be skipped.
     */
    String[] skipOperations() default {};

    /**
     * A list of Test IDs to run. All other tests will be skipped.
     *
     * <p>This filter can be useful in limiting executed tests for debugging.
     */
    String[] tests() default {};

    /**
     * A list of Operation IDs to run the tests on. All other operations will be skipped.
     *
     * <p>This filter can be useful in limiting executed tests for debugging.
     */
    String[] operations() default {};
}
