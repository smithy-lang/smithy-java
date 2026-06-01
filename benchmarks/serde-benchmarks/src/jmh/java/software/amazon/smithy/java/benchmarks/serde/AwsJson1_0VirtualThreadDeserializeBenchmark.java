/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * AWS JSON 1.0 deserialization driven from many virtual threads concurrently.
 *
 * <p>Companion to {@link AwsJson1_0DeserializeBenchmark} (platform-threaded). Each
 * benchmark op fans {@link #FANOUT} deserializations across virtual threads, exercising
 * the shared serializer/string-cache pools under virtual-thread concurrency. Run with
 * {@code -prof gc} to compare per-op allocation against a build where virtual threads
 * bypass the pools.
 */
@State(Scope.Benchmark)
public class AwsJson1_0VirtualThreadDeserializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");
    private static final byte[] EMPTY_JSON_BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_TYPE = "application/x-amz-json-1.0";

    private static final int FANOUT = 256;

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
        state = DeserializeState.forTestCase(testCaseId, GENERATED_PACKAGE, EMPTY_JSON_BODY, CONTENT_TYPE, false);
    }

    @Benchmark
    public void deserialize(Blackhole bh) throws Exception {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ApiOperation<SerializableStruct, SerializableStruct> op =
                (ApiOperation) state.operation;
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(FANOUT);
            for (int i = 0; i < FANOUT; i++) {
                futures.add(exec.submit(() -> protocol.deserializeResponse(
                        op,
                        state.context,
                        state.typeRegistry,
                        state.request,
                        state.response)));
            }
            for (Future<?> f : futures) {
                bh.consume(f.get());
            }
        }
    }
}
