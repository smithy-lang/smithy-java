/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

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
import software.amazon.smithy.java.aws.client.awsquery.AwsQueryClientProtocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * JMH benchmarks for AWS Query request serialization (form-urlencoded body
 * inside a full HTTP request envelope). Drives
 * {@link AwsQueryClientProtocol#createRequest}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class AwsQuerySerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsquery.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsQueryDataPlane");
    private static final String VERSION = "1999-12-31";

    @Param({
            "awsQuery_GetMetricDataRequest_S",
            "awsQuery_GetMetricDataRequest_M",
            "awsQuery_GetMetricDataRequest_L",
            "awsQuery_PutMetricDataRequest_Baseline",
            "awsQuery_PutMetricDataRequest_S",
            "awsQuery_PutMetricDataRequest_M",
            "awsQuery_PutMetricDataRequest_L",
    })
    public String testCaseId;

    private AwsQueryClientProtocol protocol;
    private SerializeState state;

    @Setup
    public void setup() {
        protocol = new AwsQueryClientProtocol(SERVICE_ID, VERSION);
        state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE);
    }

    @Benchmark
    public void serialize(Blackhole bh) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ApiOperation<SerializableStruct, SerializableStruct> op =
                (ApiOperation) state.operation;
        bh.consume(protocol.createRequest(op, state.input, state.context, state.endpoint));
    }
}
