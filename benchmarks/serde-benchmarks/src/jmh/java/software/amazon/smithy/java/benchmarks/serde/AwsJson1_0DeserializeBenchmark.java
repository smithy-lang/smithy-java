/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * JMH benchmarks for AWS JSON 1.0 response deserialization. Drives
 * {@link AwsJson1Protocol#deserializeResponse} to consume a full HTTP
 * response and produce a typed output.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AwsJson1_0DeserializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");
    private static final byte[] EMPTY_JSON_BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_TYPE = "application/x-amz-json-1.0";

    @Param({
            "awsJson1_0_GetItemOutput_Baseline",
            "awsJson1_0_GetItemOutput_S",
            "awsJson1_0_GetItemOutput_M",
            "awsJson1_0_GetItemOutput_L",
            "awsJson1_0_GetItemOutputBinary_S",
            "awsJson1_0_GetItemOutputBinary_M",
            "awsJson1_0_GetItemOutputBinary_L",
            "awsJson1_0_HealthcheckResponse_Example",
    })
    public String testCaseId;

    private AwsJson1Protocol protocol;
    private DeserializeState state;

    @Setup
    public void setup() {
        protocol = new AwsJson1Protocol(SERVICE_ID);
        state = DeserializeState.forTestCase(testCaseId, GENERATED_PACKAGE, EMPTY_JSON_BODY, CONTENT_TYPE, false);
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
