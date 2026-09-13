/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.nio.ByteBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkCodec;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Serialize benchmark for the EXPERIMENTAL backward Sparrowhawk writer, driven by shapes generated with
 * {@code reverseMemberSerialization} (descending member dispatch, reverse list iteration). Compare its
 * numbers against the {@code sparrowhawk} and {@code cbor} rows of {@link SparrowhawkCodecBenchmark}
 * from the same session.
 */
@State(Scope.Benchmark)
public class SparrowhawkBackwardBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.rpcv2cbordesc.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#SmithyRpcV2CborDataPlane");

    @Param({
            "rpcv2Cbor_WideTypesRequest_M",
            "rpcv2Cbor_PutItemRequest_ShallowMap_M",
            "rpcv2Cbor_PutItemRequest_Nested_M",
            "rpcv2Cbor_PutItemRequest_MixedItem_M",
            "rpcv2Cbor_PutItemRequest_BinaryData_M",
    })
    public String testCaseId;

    private Codec codec;
    private SerializableStruct input;

    @Setup
    public void setup() {
        var state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);
        this.input = state.input;
        this.codec = SparrowhawkCodec.backward();
        // End-to-end sanity through real generated code: the backward payload must decode back to an
        // equal input (map entry order differs on the wire but decodes identically).
        byte[] payload = ByteBufferUtils.getBytes(codec.serialize(input));
        Object decoded = codec.deserializeShape(payload, state.operation.inputBuilder());
        if (!input.equals(decoded)) {
            throw new IllegalStateException("Backward round-trip mismatch for " + testCaseId);
        }
    }

    @Benchmark
    public ByteBuffer serialize() {
        return codec.serialize(input);
    }
}
