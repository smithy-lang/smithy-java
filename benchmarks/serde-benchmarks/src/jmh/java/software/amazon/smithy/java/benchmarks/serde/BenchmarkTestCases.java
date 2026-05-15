/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait;

/**
 * Indexes benchmark test cases by their {@code id} field.
 *
 * <p>A test case is recognized as a benchmark if it appears in an operation's
 * {@code @httpRequestTests} or {@code @httpResponseTests} list and is tagged
 * {@code "serde-benchmark"}. The {@code id} of each case is unique across the
 * model and is used as the {@code @Param("...")} value on the JMH benchmark
 * classes.
 */
final class BenchmarkTestCases {

    /** The tag that marks a test case as a serde benchmark. */
    static final String TAG = "serde-benchmark";

    /** Lazily-built map: test-case id -> request entry. */
    private static final Map<String, RequestEntry> REQUESTS = indexRequests();

    /** Lazily-built map: test-case id -> response entry. */
    private static final Map<String, ResponseEntry> RESPONSES = indexResponses();

    private BenchmarkTestCases() {}

    static RequestEntry request(String testCaseId) {
        RequestEntry entry = REQUESTS.get(testCaseId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "No httpRequestTests case with id '" + testCaseId + "' tagged " + TAG);
        }
        return entry;
    }

    static ResponseEntry response(String testCaseId) {
        ResponseEntry entry = RESPONSES.get(testCaseId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "No httpResponseTests case with id '" + testCaseId + "' tagged " + TAG);
        }
        return entry;
    }

    private static Map<String, RequestEntry> indexRequests() {
        Map<String, RequestEntry> result = new LinkedHashMap<>();
        Model model = BenchmarkContext.MODEL;
        for (OperationShape op : model.getOperationShapes()) {
            Optional<HttpRequestTestsTrait> trait = op.getTrait(HttpRequestTestsTrait.class);
            if (trait.isEmpty()) {
                continue;
            }
            for (HttpRequestTestCase tc : trait.get().getTestCases()) {
                if (!hasBenchmarkTag(tc)) {
                    continue;
                }
                result.put(tc.getId(), new RequestEntry(op, tc));
            }
        }
        return result;
    }

    private static Map<String, ResponseEntry> indexResponses() {
        Map<String, ResponseEntry> result = new LinkedHashMap<>();
        Model model = BenchmarkContext.MODEL;
        for (OperationShape op : model.getOperationShapes()) {
            Optional<HttpResponseTestsTrait> trait = op.getTrait(HttpResponseTestsTrait.class);
            if (trait.isEmpty()) {
                continue;
            }
            for (HttpResponseTestCase tc : trait.get().getTestCases()) {
                if (!hasBenchmarkTag(tc)) {
                    continue;
                }
                result.put(tc.getId(), new ResponseEntry(op, tc));
            }
        }
        return result;
    }

    private static boolean hasBenchmarkTag(HttpMessageTestCase tc) {
        return tc.getTags().contains(TAG);
    }

    /** Pairing of an operation with one of its benchmark request test cases. */
    static final class RequestEntry {
        final OperationShape operation;
        final HttpRequestTestCase testCase;

        RequestEntry(OperationShape operation, HttpRequestTestCase testCase) {
            this.operation = operation;
            this.testCase = testCase;
        }
    }

    /** Pairing of an operation with one of its benchmark response test cases. */
    static final class ResponseEntry {
        final OperationShape operation;
        final HttpResponseTestCase testCase;

        ResponseEntry(OperationShape operation, HttpResponseTestCase testCase) {
            this.operation = operation;
            this.testCase = testCase;
        }
    }
}
