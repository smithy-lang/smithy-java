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
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkCodec;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A/B comparison of the Sparrowhawk codec against the CBOR codec at the {@link Codec} level, using the
 * same generated input shapes the RPCv2 CBOR protocol benchmarks use. This intentionally bypasses the
 * protocol/transport layers so the two serializers are compared like for like.
 */
@State(Scope.Benchmark)
public class SparrowhawkCodecBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.rpcv2cbor.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#SmithyRpcV2CborDataPlane");

    @Param({"cbor", "sparrowhawk"})
    public String codecName;

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
    private ApiOperation<? extends SerializableStruct, ? extends SerializableStruct> operation;
    private byte[] payload;

    @Setup
    public void setup() {
        var state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);
        this.operation = state.operation;
        this.input = state.input;
        this.codec = "cbor".equals(codecName)
                ? Rpcv2CborCodec.builder().build()
                : SparrowhawkCodec.get();
        this.payload = ByteBufferUtils.getBytes(codec.serialize(input));
    }

    @Benchmark
    public ByteBuffer serialize() {
        return codec.serialize(input);
    }

    @Benchmark
    public SerializableShape deserialize() {
        return codec.deserializeShape(payload, operation.inputBuilder());
    }
}
