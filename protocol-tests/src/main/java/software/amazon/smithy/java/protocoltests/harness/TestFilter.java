/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase;

/**
 * Test filter class that implements filtering of protocol tests. This filter is configured by the {@link ProtocolTestFilter} annotation.
 */
sealed interface TestFilter {
    TestFilter EMPTY = new EmptyFilter();

    /**
     * Filters operations.
     */
    boolean skipOperation(ShapeId operationId);

    /**
     * Filters test cases
     */
    boolean skipTestCase(HttpMessageTestCase testCase, TestType testType);

    default boolean skipTestCase(HttpMessageTestCase testCase) {
        return skipTestCase(testCase, null);
    }

    default TestFilter combine(TestFilter other) {
        return new CombinedTestFilter(this, other);
    }

    static TestFilter fromAnnotation(ProtocolTestFilter filterAnnotation) {
        if (filterAnnotation == null) {
            return EMPTY;
        }
        return new FilterImpl(filterAnnotation);
    }

    final class FilterImpl implements TestFilter {
        private final Set<ShapeId> operations = new HashSet<>();
        private final Set<ShapeId> skippedOperations = new HashSet<>();
        private final Set<String> tests = new HashSet<>();
        private final Set<String> skippedTests = new HashSet<>();

        public FilterImpl(ProtocolTestFilter filter) {
            skippedOperations.addAll(
                Arrays.stream(filter.skipOperations()).map(ShapeId::from).collect(Collectors.toSet())
            );
            for (var id : filter.operations()) {
                var operationId = ShapeId.from(id);
                if (skippedOperations.contains(operationId)) {
                    throw new IllegalArgumentException("Operation: " + id + " is skipped and cannot be run.");
                }
                operations.add(operationId);
            }
            skippedTests.addAll(Arrays.asList(filter.skipTests()));
            for (var test : filter.tests()) {
                if (skippedTests.contains(test)) {
                    throw new IllegalArgumentException("Test: " + test + " is skipped and cannot be run.");
                }
                tests.add(test);
            }
        }

        @Override
        public boolean skipOperation(ShapeId operationId) {
            return skippedOperations.contains(operationId)
                || (!operations.isEmpty() && !operations.contains(operationId));
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase, TestType testType) {
            return skippedTests.contains(testCase.getId())
                || (!tests.isEmpty() && !tests.contains(testCase.getId()))
                || (testType != null
                    && testCase.getAppliesTo().isPresent()
                    && !testCase.getAppliesTo().get().equals(testType.appliesTo));
        }
    }

    final class EmptyFilter implements TestFilter {

        @Override
        public boolean skipOperation(ShapeId operationId) {
            return false;
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase, TestType testType) {
            return false;
        }
    }

    final class CombinedTestFilter implements TestFilter {

        private final TestFilter first;
        private final TestFilter second;

        private CombinedTestFilter(TestFilter first, TestFilter second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean skipOperation(ShapeId operationId) {
            return first.skipOperation(operationId) || second.skipOperation(operationId);
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase, TestType testType) {
            return first.skipTestCase(testCase, testType) || second.skipTestCase(testCase, testType);
        }
    }
}
