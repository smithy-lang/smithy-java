/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.lang.reflect.Method;
import software.amazon.smithy.java.benchmarks.serde.BenchmarkTestCases.RequestEntry;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestDocument;
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
 * by deserializing the test case's {@code params} {@code Node} via
 * {@link ProtocolTestDocument} into the codegen-generated input builder —
 * that work happens here, not in the benchmark loop.
 */
final class SerializeState {

    private static final SmithyUri ENDPOINT = SmithyUri.of("http://localhost/");

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
        OperationShape opShape = entry.operation();

        var operation = resolveOperation(generatedPackage, opShape.getId().getName());

        Node params = entry.testCase().getParams();
        var inputBuilder = operation.inputBuilder();
        new ProtocolTestDocument(params == null ? Node.objectNode() : params, null)
                .deserializeInto(inputBuilder);

        return new SerializeState(operation, inputBuilder.build());
    }

    @SuppressWarnings("unchecked")
    static ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> resolveOperation(
            String generatedPackage,
            String operationName
    ) {
        String fqcn = generatedPackage + "." + operationName;
        try {
            Method instanceMethod = Class.forName(fqcn).getMethod("instance");
            return (ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>) instanceMethod
                    .invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve ApiOperation " + fqcn, e);
        }
    }

    static void main() {
        SerializeState.forTestCase("rpcv2Cbor_PutItemRequest_BinaryData_M",
                "software.amazon.smithy.java.benchmarks.serde.generated.rpcv2cbor.model");
    }
}
