/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
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
 * AWS JSON 1.0 deserialization measured on virtual carrier threads.
 *
 * <p>Companion to {@link AwsJson1_0DeserializeBenchmark} (platform-threaded). The benchmark
 * body is a single {@code deserializeResponse} call; {@code @Fork(jvmArgsAppend =
 * "-Djmh.executor=VIRTUAL")} runs JMH's measurement threads as virtual threads. This
 * isolates how the shared serializer / string-cache pools behave under virtual-thread
 * carriers, keeping virtual-thread creation cost out of the per-op measurement.
 *
 * <p>Run with {@code -prof gc} to compare per-op allocation against the platform-threaded run.
 */
@State(Scope.Benchmark)
@Fork(jvmArgsAppend = "-Djmh.executor=VIRTUAL")
public class AwsJson1_0VirtualThreadDeserializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");
    private static final byte[] EMPTY_JSON_BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_TYPE = "application/x-amz-json-1.0";

    @Param({
            "awsJson1_0_GetItemOutput_S",
            "awsJson1_0_GetItemOutput_M",
            "awsJson1_0_GetItemOutput_L",
    })
    public String testCaseId;

    private AwsJson1Protocol protocol;
    private DeserializeState state;

    @Setup
    public void setup() {
        protocol = new AwsJson1Protocol(SERVICE_ID);
        state = DeserializeState
                .forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID, EMPTY_JSON_BODY, CONTENT_TYPE, false);
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
