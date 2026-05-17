/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.util.LinkedHashMap;
import java.util.Map;
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

    static final String TAG = "serde-benchmark";

    private static final Model MODEL = Model.assembler(BenchmarkTestCases.class.getClassLoader())
            .discoverModels(BenchmarkTestCases.class.getClassLoader())
            .assemble()
            .unwrap();

    private static final Map<String, RequestEntry> REQUESTS = indexRequests();
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
        for (OperationShape op : MODEL.getOperationShapes()) {
            var trait = op.getTrait(HttpRequestTestsTrait.class);
            if (trait.isEmpty()) {
                continue;
            }
            for (HttpRequestTestCase tc : trait.get().getTestCases()) {
                if (hasBenchmarkTag(tc)) {
                    result.put(tc.getId(), new RequestEntry(op, tc));
                }
            }
        }
        return result;
    }

    private static Map<String, ResponseEntry> indexResponses() {
        Map<String, ResponseEntry> result = new LinkedHashMap<>();
        for (OperationShape op : MODEL.getOperationShapes()) {
            var trait = op.getTrait(HttpResponseTestsTrait.class);
            if (trait.isEmpty()) {
                continue;
            }
            for (HttpResponseTestCase tc : trait.get().getTestCases()) {
                if (hasBenchmarkTag(tc)) {
                    result.put(tc.getId(), new ResponseEntry(op, tc));
                }
            }
        }
        return result;
    }

    private static boolean hasBenchmarkTag(HttpMessageTestCase tc) {
        return tc.getTags().contains(TAG);
    }

    record RequestEntry(OperationShape operation, HttpRequestTestCase testCase) {}

    record ResponseEntry(OperationShape operation, HttpResponseTestCase testCase) {}
}
