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
import software.amazon.smithy.java.aws.client.restxml.RestXmlClientProtocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * JMH benchmarks for restXml request serialization. Drives
 * {@link RestXmlClientProtocol#createRequest} which performs header / URI
 * label / query string binding plus XML body serialization.
 */
@State(Scope.Benchmark)
public class RestXmlSerializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.restxml.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsRestXmlDataPlane");

    @Param({
            "restXml_CopyObjectRequest_Baseline",
            "restXml_CopyObjectRequest_M",
            "restXml_PutObject_S",
            "restXml_PutObject_M",
            "restXml_PutObject_L",
    })
    public String testCaseId;

    private RestXmlClientProtocol protocol;
    private SerializeState state;

    @Setup
    public void setup() {
        protocol = new RestXmlClientProtocol(SERVICE_ID);
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
