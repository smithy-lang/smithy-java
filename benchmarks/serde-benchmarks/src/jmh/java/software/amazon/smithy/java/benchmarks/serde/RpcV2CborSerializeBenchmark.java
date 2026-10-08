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

@State(Scope.Benchmark)
public class RpcV2CborSerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.rpcv2cbor.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#SmithyRpcV2CborDataPlane");

    @Param("generic")
    public String implementation;

    @Param({
            "rpcv2Cbor_WideTypesRequest_S",
            "rpcv2Cbor_WideTypesRequest_M",
            "rpcv2Cbor_WideTypesRequest_L",
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
        boolean generated = "generated".equals(implementation);
        if (generated && Runtime.version().feature() < 25) {
            throw new IllegalStateException("Runtime codegen unavailable on " + Runtime.version());
        }
        System.setProperty("smithy-java.runtime-codegen", generated ? "enabled" : "disabled");
        protocol = new RpcV2CborProtocol(SERVICE_ID);
        state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);
    }

    @Benchmark
    public void serialize(Blackhole bh) {
        bh.consume(protocol.createRequest(operation(), state.input, state.context, state.endpoint));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ApiOperation<SerializableStruct, SerializableStruct> operation() {
        return (ApiOperation) state.operation;
    }
}
