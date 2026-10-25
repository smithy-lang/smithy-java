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
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.model.shapes.ShapeId;

@State(Scope.Benchmark)
public class AwsJson1_0CodecSerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");

    @Param("generic")
    public String implementation;

    @Param({
            "awsJson1_0_GetItemInput_Baseline",
            "awsJson1_0_HealthcheckRequest_Example",
            "awsJson1_0_WideTypesRequest_S",
            "awsJson1_0_WideTypesRequest_M",
            "awsJson1_0_WideTypesRequest_L",
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

    private Codec codec;
    private SerializeState state;

    @Setup
    public void setup() {
        boolean generated = "generated".equals(implementation);
        if (generated && Runtime.version().feature() < 25) {
            throw new IllegalStateException("Runtime codegen unavailable on " + Runtime.version());
        }
        System.setProperty("smithy-java.runtime-codegen", generated ? "enabled" : "disabled");
        System.setProperty("smithy-java.json-provider", "smithy");
        codec = new AwsJson1Protocol(SERVICE_ID).payloadCodec();
        state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);
    }

    @Benchmark
    public void serialize(Blackhole bh) {
        bh.consume(codec.serialize(state.input));
    }
}
