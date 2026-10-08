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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.aws.client.awsquery.AwsQueryClientProtocol;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenStats;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A/B benchmark for runtime codegen over AWS Query request serialization.
 *
 * <p>The Query serializer has no per-instance switch: whether a generated codec is used is decided
 * by a system property read when the dispatch class initializes. JMH gives every parameter tuple its
 * own fork, so setup installs the property before constructing or invoking the protocol and verifies
 * the resulting arm before measurement.
 */
@State(Scope.Benchmark)
public class AwsQueryRuntimeCodegenSerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsquery.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsQueryDataPlane");
    private static final String VERSION = "1999-12-31";

    @Param({"generic", "generated"})
    public String implementation;

    @Param({
            "awsQuery_GetMetricDataRequest_M",
            "awsQuery_PutMetricDataRequest_Baseline",
            "awsQuery_PutMetricDataRequest_M",
            "awsQuery_PutMetricDataRequest_L",
    })
    public String testCaseId;

    private AwsQueryClientProtocol protocol;
    private SerializeState state;
    private String previousCodegen;

    @Setup
    public void setup() {
        boolean generated = "generated".equals(implementation);
        previousCodegen = RuntimeCodegenBenchmarkSupport.setProperty(
                RuntimeCodegenBenchmarkSupport.codegenProperty("awsquery"),
                generated ? "enabled" : "");
        protocol = new AwsQueryClientProtocol(SERVICE_ID, VERSION);
        state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);

        // Force the generated codec to be produced and linked before measurement starts, so the first
        // measured iteration is not paying for class definition.
        RuntimeCodegenStats.reset();
        protocol.createRequest(operation(), state.input, state.context, state.endpoint);
        RuntimeCodegenBenchmarkSupport.verify("awsquery", generated);
    }

    @TearDown
    public void tearDown() {
        RuntimeCodegenBenchmarkSupport.restoreProperty(
                RuntimeCodegenBenchmarkSupport.codegenProperty("awsquery"),
                previousCodegen);
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
