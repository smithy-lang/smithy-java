/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase;
import software.amazon.smithy.protocoltests.traits.eventstream.EventStreamTestCase;

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
     * Filters test cases regardless of mode (i.e. an unsuffixed skip/run entry).
     */
    boolean skipTestCase(HttpMessageTestCase testCase);

    /**
     * Filters a test case for a specific {@link TestMode}, honoring mode-suffixed skip/run entries
     * (e.g. {@code "SomeTest [dynamic]"}).
     */
    boolean skipTestCase(HttpMessageTestCase testCase, TestMode mode);

    /**
     * Filters event stream test cases.
     */
    boolean skipTestCase(EventStreamTestCase testCase);

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
        // "tests"/"skipped" entries that apply to every mode (no [mode] suffix).
        private final Set<String> tests = new HashSet<>();
        private final Set<String> skippedTests = new HashSet<>();
        // Mode-suffixed entries, keyed by mode, e.g. "SomeTest [dynamic]" -> DYNAMIC: {"SomeTest"}.
        private final Map<TestMode, Set<String>> testsByMode = new EnumMap<>(TestMode.class);
        private final Map<TestMode, Set<String>> skippedTestsByMode = new EnumMap<>(TestMode.class);

        public FilterImpl(ProtocolTestFilter filter) {
            skippedOperations.addAll(
                    Arrays.stream(filter.skipOperations()).map(ShapeId::from).collect(Collectors.toSet()));
            for (var id : filter.operations()) {
                var operationId = ShapeId.from(id);
                if (skippedOperations.contains(operationId)) {
                    throw new IllegalArgumentException("Operation: " + id + " is skipped and cannot be run.");
                }
                operations.add(operationId);
            }
            for (var entry : filter.skipTests()) {
                addEntry(entry, skippedTests, skippedTestsByMode);
            }
            for (var entry : filter.tests()) {
                addEntry(entry, tests, testsByMode);
            }
        }

        // Parse an entry into either the unsuffixed set or the per-mode set, depending on a trailing "[mode]".
        private static void addEntry(String entry, Set<String> all, Map<TestMode, Set<String>> byMode) {
            var mode = parseMode(entry);
            if (mode == null) {
                all.add(entry);
            } else {
                byMode.computeIfAbsent(mode, k -> new HashSet<>()).add(stripModeSuffix(entry));
            }
        }

        private static TestMode parseMode(String entry) {
            for (var mode : TestMode.values()) {
                if (entry.endsWith(" [" + mode.label() + "]")) {
                    return mode;
                }
            }
            return null;
        }

        private static String stripModeSuffix(String entry) {
            return entry.substring(0, entry.lastIndexOf(" [")).trim();
        }

        @Override
        public boolean skipOperation(ShapeId operationId) {
            return skippedOperations.contains(operationId)
                    || (!operations.isEmpty() && !operations.contains(operationId));
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase) {
            return skip(testCase.getId(), skippedTests, tests);
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase, TestMode mode) {
            if (skip(testCase.getId(), skippedTests, tests)) {
                return true;
            }
            // Mode-specific skip, or a mode-specific "only run these" allow-list that excludes this id.
            var skippedForMode = skippedTestsByMode.getOrDefault(mode, Set.of());
            var allowedForMode = testsByMode.getOrDefault(mode, Set.of());
            return skippedForMode.contains(testCase.getId())
                    || (!allowedForMode.isEmpty() && !allowedForMode.contains(testCase.getId()));
        }

        @Override
        public boolean skipTestCase(EventStreamTestCase testCase) {
            return skip(testCase.getId(), skippedTests, tests);
        }

        private static boolean skip(String id, Set<String> skipped, Set<String> only) {
            return skipped.contains(id) || (!only.isEmpty() && !only.contains(id));
        }
    }

    final class EmptyFilter implements TestFilter {

        @Override
        public boolean skipOperation(ShapeId operationId) {
            return false;
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase) {
            return false;
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase, TestMode mode) {
            return false;
        }

        @Override
        public boolean skipTestCase(EventStreamTestCase testCase) {
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
        public boolean skipTestCase(HttpMessageTestCase testCase) {
            return first.skipTestCase(testCase) || second.skipTestCase(testCase);
        }

        @Override
        public boolean skipTestCase(HttpMessageTestCase testCase, TestMode mode) {
            return first.skipTestCase(testCase, mode) || second.skipTestCase(testCase, mode);
        }

        @Override
        public boolean skipTestCase(EventStreamTestCase testCase) {
            return first.skipTestCase(testCase) || second.skipTestCase(testCase);
        }
    }
}
