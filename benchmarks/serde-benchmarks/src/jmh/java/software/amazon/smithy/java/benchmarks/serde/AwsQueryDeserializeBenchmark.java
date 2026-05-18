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
import software.amazon.smithy.java.aws.client.awsquery.AwsQueryClientProtocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * JMH benchmarks for AWS Query response deserialization. AWS Query responses
 * are XML; the protocol's {@code deserializeResponse} also strips the
 * protocol-specific result wrapper element.
 */
@State(Scope.Benchmark)
public class AwsQueryDeserializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsquery.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsQueryDataPlane");
    private static final String VERSION = "1999-12-31";
    private static final String CONTENT_TYPE = "text/xml";

    @Param({
            "awsQuery_GetMetricDataResponse_S",
            "awsQuery_GetMetricDataResponse_M",
            "awsQuery_GetMetricDataResponse_L",
            "awsQuery_GetMetricDataResponse_OutOfOrder",
    })
    public String testCaseId;

    private AwsQueryClientProtocol protocol;
    private DeserializeState state;

    @Setup
    public void setup() {
        protocol = new AwsQueryClientProtocol(SERVICE_ID, VERSION);
        state = DeserializeState.forTestCase(testCaseId, GENERATED_PACKAGE, null, CONTENT_TYPE, false);
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
