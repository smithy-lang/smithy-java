/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.jmh.annotations.Param;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.shapes.ShapeId;

class RuntimeCodegenStrictCoverageTest {

    private static final String REST_JSON_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.restjson.model";
    private static final ShapeId REST_JSON_SERVICE =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsRestJsonDataPlane");
    private static final String AWS_JSON_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId AWS_JSON_SERVICE =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");
    private static final byte[] EMPTY_JSON_BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String REST_JSON_CONTENT_TYPE = "application/json";
    private static final String AWS_JSON_CONTENT_TYPE = "application/x-amz-json-1.0";

    private static String previousCodegen;
    private static String previousProvider;

    @BeforeAll
    static void enableStrictCodegen() {
        previousCodegen = System.setProperty("smithy-java.runtime-codegen", "strict");
        previousProvider = System.setProperty("smithy-java.json-provider", "smithy");
    }

    @AfterAll
    static void restoreProperties() {
        restore("smithy-java.runtime-codegen", previousCodegen);
        restore("smithy-java.json-provider", previousProvider);
    }

    static List<String> restJsonSerializeCases() throws Exception {
        return benchmarkCases(RestJson1SerializeBenchmark.class);
    }

    static List<String> restJsonDeserializeCases() throws Exception {
        return benchmarkCases(RestJson1DeserializeBenchmark.class);
    }

    static List<String> awsJsonSerializeCases() throws Exception {
        return benchmarkCases(AwsJson1_0SerializeBenchmark.class);
    }

    static List<String> awsJsonDeserializeCases() throws Exception {
        return benchmarkCases(AwsJson1_0DeserializeBenchmark.class);
    }

    @ParameterizedTest
    @MethodSource("restJsonSerializeCases")
    void restJsonSerialize(String testCaseId) {
        var protocol = new RestJsonClientProtocol(REST_JSON_SERVICE);
        var state = SerializeState.forTestCase(testCaseId, REST_JSON_PACKAGE, REST_JSON_SERVICE);
        assertNotNull(protocol.createRequest(operation(state.operation), state.input, state.context, state.endpoint));
    }

    @ParameterizedTest
    @MethodSource("restJsonDeserializeCases")
    void restJsonDeserialize(String testCaseId) throws Exception {
        var protocol = new RestJsonClientProtocol(REST_JSON_SERVICE);
        var state = DeserializeState.forTestCase(
                testCaseId,
                REST_JSON_PACKAGE,
                REST_JSON_SERVICE,
                EMPTY_JSON_BODY,
                REST_JSON_CONTENT_TYPE,
                false);
        assertNotNull(protocol.deserializeResponse(
                operation(state.operation),
                state.context,
                state.typeRegistry,
                state.request,
                state.response));
    }

    @ParameterizedTest
    @MethodSource("awsJsonSerializeCases")
    void awsJsonSerialize(String testCaseId) {
        var protocol = new AwsJson1Protocol(AWS_JSON_SERVICE);
        var state = SerializeState.forTestCase(testCaseId, AWS_JSON_PACKAGE, AWS_JSON_SERVICE);
        assertNotNull(protocol.createRequest(operation(state.operation), state.input, state.context, state.endpoint));
    }

    @ParameterizedTest
    @MethodSource("awsJsonDeserializeCases")
    void awsJsonDeserialize(String testCaseId) throws Exception {
        var protocol = new AwsJson1Protocol(AWS_JSON_SERVICE);
        var state = DeserializeState.forTestCase(
                testCaseId,
                AWS_JSON_PACKAGE,
                AWS_JSON_SERVICE,
                EMPTY_JSON_BODY,
                AWS_JSON_CONTENT_TYPE,
                false);
        assertNotNull(protocol.deserializeResponse(
                operation(state.operation),
                state.context,
                state.typeRegistry,
                state.request,
                state.response));
    }

    private static List<String> benchmarkCases(Class<?> benchmark) throws Exception {
        Param param = benchmark.getField("testCaseId").getAnnotation(Param.class);
        return List.of(param.value());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ApiOperation<SerializableStruct, SerializableStruct> operation(Object operation) {
        return (ApiOperation) operation;
    }

    private static void restore(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }
}
