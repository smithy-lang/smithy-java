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
 * JMH benchmarks for restXml response deserialization. Drives
 * {@link RestXmlClientProtocol#deserializeResponse} which performs header
 * binding plus XML body deserialization.
 */
@State(Scope.Benchmark)
public class RestXmlDeserializeBenchmark {

    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.restxml.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsRestXmlDataPlane");
    /** Pass null to DeserializeState to use the auto-derived <ShapeName/> default. */
    private static final byte[] EMPTY_XML_BODY = null;
    private static final String CONTENT_TYPE = "application/xml";

    @Param({
            "restXml_CopyObjectOutput_Baseline",
            "restXml_CopyObjectOutput_M",
            "restXml_GetObject_S",
            "restXml_GetObject_M",
            "restXml_GetObject_L",
    })
    public String testCaseId;

    private RestXmlClientProtocol protocol;
    private DeserializeState state;

    @Setup
    public void setup() {
        protocol = new RestXmlClientProtocol(SERVICE_ID);
        state = DeserializeState.forTestCase(testCaseId, GENERATED_PACKAGE, EMPTY_XML_BODY, CONTENT_TYPE, false);
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
