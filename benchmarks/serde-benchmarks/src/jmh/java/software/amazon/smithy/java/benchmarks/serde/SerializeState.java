/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.benchmarks.serde.BenchmarkTestCases.RequestEntry;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Reusable per-trial state for a serialization benchmark.
 *
 * <p>Each benchmark drives {@code ClientProtocol#createRequest(operation,
 * input, context, endpoint)}. This state object holds those four arguments,
 * pre-built once during {@code @Setup}.
 *
 * <p>The codegen-generated {@link ApiOperation} singleton is resolved by
 * reflection from the protocol-specific package (each codegen projection
 * emits into its own sub-package). The typed input is materialized at setup
 * by serializing the test case's {@code params} {@code Node} to JSON and
 * deserializing it via the codegen-generated input builder — that work
 * happens here, not in the benchmark loop.
 */
final class SerializeState {

    private static final SmithyUri ENDPOINT = SmithyUri.of("http://localhost/");

    /**
     * JSON codec used at setup time to convert the test case {@code params}
     * Node into the typed shape. Uses Smithy member names (not jsonName)
     * because the trait params are member-name-keyed; honors
     * {@code @timestampFormat} so test-trait timestamps (typically integer
     * epoch seconds) are decoded into {@code Instant} fields.
     */
    private static final JsonCodec SETUP_CODEC = JsonCodec.builder()
            .useJsonName(false)
            .useTimestampFormat(true)
            .build();

    final ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation;
    final SerializableStruct input;
    final Context context;
    final SmithyUri endpoint;

    private SerializeState(
            ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation,
            SerializableStruct input
    ) {
        this.operation = operation;
        this.input = input;
        this.context = Context.create();
        this.endpoint = ENDPOINT;
    }

    /**
     * Build the serialize state from a request test case.
     *
     * @param testCaseId       benchmark test case id
     * @param generatedPackage Java package emitted by the protocol's codegen
     *                         projection (e.g.,
     *                         {@code "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model"})
     */
    static SerializeState forTestCase(String testCaseId, String generatedPackage) {
        RequestEntry entry = BenchmarkTestCases.request(testCaseId);
        OperationShape opShape = entry.operation;

        ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation =
                ApiOperationLookup.resolve(generatedPackage, opShape.getId().getName());

        // Render the trait params Node directly to JSON, then deserialize via
        // the codegen-generated input builder. Going Node -> JSON -> typed
        // (rather than Node -> Document -> StructDocument -> JSON -> typed)
        // preserves the raw integer / number forms expected by the JSON
        // codec for fields with @timestampFormat = epoch-seconds.
        Node params = entry.testCase.getParams();
        String json = params == null ? "{}" : Node.printJson(params);
        SerializableStruct input = SETUP_CODEC.deserializeShape(
                json.getBytes(StandardCharsets.UTF_8),
                operation.inputBuilder());

        return new SerializeState(operation, input);
    }
}
