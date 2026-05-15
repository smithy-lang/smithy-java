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
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * JMH benchmarks for restJson1 request serialization. Drives
 * {@link RestJsonClientProtocol#createRequest} which performs header / URI
 * label / query string binding plus JSON body serialization.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class RestJson1SerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.restjson.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsRestJsonDataPlane");

    @Param({
            "restJson1_CopyObjectRequest_Baseline",
            "restJson1_CopyObjectRequest_M",
            "restJson1_PutObject_S",
            "restJson1_PutObject_M",
            "restJson1_PutObject_L",
    })
    public String testCaseId;

    private RestJsonClientProtocol protocol;
    private SerializeState state;

    @Setup
    public void setup() {
        protocol = new RestJsonClientProtocol(SERVICE_ID);
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
