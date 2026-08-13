/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A/B benchmark for production-shaped runtime codegen over AWS JSON GetItem responses.
 */
@State(Scope.Benchmark)
public class AwsJsonRuntimeCodegenDeserializeBenchmark {
    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");
    private static final byte[] EMPTY_JSON = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_TYPE = "application/x-amz-json-1.0";

    @Param({"generic", "generated"})
    public String implementation;

    @Param({"awsJson1_0_GetItemOutput_M", "awsJson1_0_GetItemOutput_L"})
    public String testCaseId;

    private JsonCodec codec;
    private AwsJson1Protocol protocol;
    private DeserializeState state;
    private byte[] input;

    @Setup
    public void setup() {
        String previousCodegen = System.getProperty("smithy-java.runtime-codegen");
        String previousAws = System.getProperty("smithy-java.aws-json-runtime-codegen");
        try {
            if ("generated".equals(implementation)) {
                System.setProperty("smithy-java.runtime-codegen", "json");
                System.setProperty("smithy-java.aws-json-runtime-codegen", "true");
            } else {
                System.clearProperty("smithy-java.runtime-codegen");
                System.clearProperty("smithy-java.aws-json-runtime-codegen");
            }
            codec = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build();
            protocol = new AwsJson1Protocol(SERVICE_ID);
        } finally {
            restore("smithy-java.runtime-codegen", previousCodegen);
            restore("smithy-java.aws-json-runtime-codegen", previousAws);
        }
        state = DeserializeState
                .forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID, EMPTY_JSON, CONTENT_TYPE, false);
        ByteBuffer body = state.response.body().asByteBuffer();
        input = ByteBufferUtils.getBytes(body);
        codecDeserialize();
        protocol.deserializeResponse(operation(), state.context, state.typeRegistry, state.request, state.response);
    }

    @Benchmark
    @SuppressWarnings({"rawtypes", "unchecked"})
    public SerializableStruct codecDeserialize() {
        ShapeBuilder builder = state.operation.outputBuilder();
        return (SerializableStruct) codec.deserializeShape(input, builder);
    }

    @Benchmark
    public void protocolDeserialize(Blackhole blackhole) {
        blackhole.consume(protocol.deserializeResponse(
                operation(),
                state.context,
                state.typeRegistry,
                state.request,
                state.response));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ApiOperation<SerializableStruct, SerializableStruct> operation() {
        return (ApiOperation) state.operation;
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
