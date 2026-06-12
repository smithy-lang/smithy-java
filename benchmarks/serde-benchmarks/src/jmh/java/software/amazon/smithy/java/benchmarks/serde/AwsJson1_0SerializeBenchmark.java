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
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * JMH benchmarks for AWS JSON 1.0 request serialization.
 *
 * <p>Drives {@link AwsJson1Protocol#createRequest} to produce a full HTTP
 * request from a typed input — the same protocol-level entry point a
 * generated client uses internally.
 */
@State(Scope.Benchmark)
public class AwsJson1_0SerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");

    @Param({
            "awsJson1_0_GetItemInput_Baseline",
            "awsJson1_0_HealthcheckRequest_Example",
            "awsJson1_0_PutItemRequest_Baseline",
            "awsJson1_0_PutItemRequest_ShallowMap_S",
            "awsJson1_0_PutItemRequest_ShallowMap_M",
            "awsJson1_0_PutItemRequest_ShallowMap_L",
            "awsJson1_0_PutItemRequest_Nested_M",
            "awsJson1_0_PutItemRequest_Nested_L",
            "awsJson1_0_PutItemRequest_MixedItem_S",
            "awsJson1_0_PutItemRequest_MixedItem_M",
            "awsJson1_0_PutItemRequest_MixedItem_L",
            "awsJson1_0_PutItemRequest_BinaryData_S",
            "awsJson1_0_PutItemRequest_BinaryData_M",
            "awsJson1_0_PutItemRequest_BinaryData_L",
    })
    public String testCaseId;

    private AwsJson1Protocol protocol;
    private SerializeState state;

    @Setup
    public void setup() {
        protocol = new AwsJson1Protocol(SERVICE_ID);
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
