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
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.RuntimeCodegenBenchmarkSupport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Scalar-heavy generated JSON serialization over CloudWatch metric inputs.
 */
@State(Scope.Benchmark)
public class JsonRuntimeCodegenScalarBenchmark {
    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");

    @Param({"generic", "generated"})
    public String implementation;

    @Param({
            "awsQuery_PutMetricDataRequest_S",
            "awsQuery_PutMetricDataRequest_M",
            "awsQuery_PutMetricDataRequest_L"
    })
    public String testCaseId;

    private JsonCodec codec;
    private SerializeState state;

    @Setup
    public void setup() {
        String previous = System.getProperty("smithy-java.runtime-codegen");
        try {
            if ("generated".equals(implementation)) {
                System.setProperty("smithy-java.runtime-codegen", "json");
            } else {
                System.clearProperty("smithy-java.runtime-codegen");
            }
            codec = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build();
        } finally {
            restore("smithy-java.runtime-codegen", previous);
        }
        state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);
        serialize();
        if ("generated".equals(implementation)) {
            RuntimeCodegenBenchmarkSupport.requireGenerated(codec);
        }
    }

    @Benchmark
    public ByteBuffer serialize() {
        return codec.serialize(state.input);
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
