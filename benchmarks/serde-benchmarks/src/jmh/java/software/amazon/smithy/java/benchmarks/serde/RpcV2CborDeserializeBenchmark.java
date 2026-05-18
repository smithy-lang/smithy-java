/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.client.rpcv2.RpcV2CborProtocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * JMH benchmarks for RPC v2 CBOR response deserialization. Drives
 * {@link RpcV2CborProtocol#deserializeResponse}.
 *
 * <p>The body strings stored in the smithy model for CBOR responses are
 * base64-encoded (the smithy protocol-tests convention for binary media
 * types). They are decoded once at setup; the benchmark loop measures only
 * the deserialization.
 */
@State(Scope.Benchmark)
public class RpcV2CborDeserializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.rpcv2cbor.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#SmithyRpcV2CborDataPlane");
    /** Single-byte CBOR encoding of {@code {}} (empty map). */
    private static final byte[] EMPTY_CBOR_BODY = new byte[] {(byte) 0xa0};
    private static final String CONTENT_TYPE = "application/cbor";

    @Param({
            "rpcv2Cbor_GetItemOutput_Baseline",
            "rpcv2Cbor_GetItemOutput_S",
            "rpcv2Cbor_GetItemOutput_M",
            "rpcv2Cbor_GetItemOutput_L",
            "rpcv2Cbor_GetItemOutput_OutOfOrder",
            "rpcv2Cbor_GetItemOutputBinary_S",
            "rpcv2Cbor_GetItemOutputBinary_M",
            "rpcv2Cbor_GetItemOutputBinary_L",
    })
    public String testCaseId;

    private RpcV2CborProtocol protocol;
    private DeserializeState state;

    @Setup
    public void setup() {
        protocol = new RpcV2CborProtocol(SERVICE_ID);
        state = DeserializeState.forTestCase(testCaseId, GENERATED_PACKAGE, EMPTY_CBOR_BODY, CONTENT_TYPE, true);
    }

    @Benchmark
    public void deserialize(Blackhole bh) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ApiOperation<SerializableStruct, SerializableStruct> op =
                (ApiOperation) state.operation;
        bh.consume(
                protocol.deserializeResponse(op, state.context, state.typeRegistry, state.request, state.response));
    }
}
