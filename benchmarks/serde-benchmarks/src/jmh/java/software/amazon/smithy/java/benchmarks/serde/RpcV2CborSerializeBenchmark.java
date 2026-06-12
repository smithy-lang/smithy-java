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
 * JMH benchmarks for RPC v2 CBOR request serialization. Drives
 * {@link RpcV2CborProtocol#createRequest}.
 */
@State(Scope.Benchmark)
public class RpcV2CborSerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.rpcv2cbor.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#SmithyRpcV2CborDataPlane");

    @Param({
            "rpcv2Cbor_PutItemRequest_Baseline",
            "rpcv2Cbor_PutItemRequest_ShallowMap_S",
            "rpcv2Cbor_PutItemRequest_ShallowMap_M",
            "rpcv2Cbor_PutItemRequest_ShallowMap_L",
            "rpcv2Cbor_PutItemRequest_Nested_M",
            "rpcv2Cbor_PutItemRequest_Nested_L",
            "rpcv2Cbor_PutItemRequest_MixedItem_S",
            "rpcv2Cbor_PutItemRequest_MixedItem_M",
            "rpcv2Cbor_PutItemRequest_MixedItem_L",
            "rpcv2Cbor_PutItemRequest_BinaryData_S",
            "rpcv2Cbor_PutItemRequest_BinaryData_M",
            "rpcv2Cbor_PutItemRequest_BinaryData_L",
    })
    public String testCaseId;

    private RpcV2CborProtocol protocol;
    private SerializeState state;

    @Setup
    public void setup() {
        protocol = new RpcV2CborProtocol(SERVICE_ID);
        state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);
    }

    @Benchmark
    public void serialize(Blackhole bh) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ApiOperation<SerializableStruct, SerializableStruct> op =
                (ApiOperation) state.operation;
        bh.consume(protocol.createRequest(op, state.input, state.context, state.endpoint));
    }
}
