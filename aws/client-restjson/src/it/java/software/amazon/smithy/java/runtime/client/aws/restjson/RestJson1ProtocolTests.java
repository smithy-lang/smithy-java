/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.aws.restjson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.StringBuildingSubscriber;
import software.amazon.smithy.java.protocoltests.harness.TestType;
import software.amazon.smithy.java.runtime.io.ByteBufferUtils;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.model.node.Node;

@ProtocolTest(
    service = "aws.protocoltests.restjson#RestJson",
    testType = TestType.CLIENT
)
@ProtocolTestFilter(
    skipOperations = {
        // We dont ignore defaults on input shapes
        "aws.protocoltests.restjson#OperationWithDefaults",
        // All the Http payload tests are breaking. No idea why
        "aws.protocoltests.restjson#HttpPayloadTraits",
        "aws.protocoltests.restjson#HttpEnumPayload",
        "aws.protocoltests.restjson#HttpPayloadTraitsWithMediaType",
        "aws.protocoltests.restjson#HttpStringPayload",
        "aws.protocoltests.restjson#HttpPayloadWithUnion",
        // We do not fully support streaming in clients yet
        "aws.protocoltests.restjson#StreamingTraits",
        "aws.protocoltests.restjson#StreamingTraitsRequireLength",
        "aws.protocoltests.restjson#StreamingTraitsWithMediaType",
        // Clients do not support content-encoding yet
        "aws.protocoltests.restjson#PutWithContentEncoding"
    }
)
public class RestJson1ProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
        skipTests = {
            // The order of the return values is different for some reason?
            "RestJsonSerializesSparseNullMapValues",
            // We do not yet support checksums in requests
            "RestJsonHttpChecksumRequired",
            // No idea. Fix
            "RestJsonHttpWithHeadersButNoPayload",
            "RestJsonHttpWithEmptyStructurePayload",
            "MediaTypeHeaderInputBase64",
            "RestJsonHttpWithEmptyBlobPayload"
        }
    )
    public void requestTest(DataStream expected, DataStream actual) {
        String expectedJson = "{}";
        if (expected.contentLength() != 0) {
            // Use the node parser to strip out white space.
            expectedJson = Node.printJson(
                Node.parse(new String(ByteBufferUtils.getBytes(expected.waitForByteBuffer()), StandardCharsets.UTF_8))
            );
        }
        assertEquals(expectedJson, new StringBuildingSubscriber(actual).getResult());

    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
        skipTests = {
            // Invalid ints, bools, etc in headers
            "RestJsonInputAndOutputWithNumericHeaders",
            "RestJsonInputAndOutputWithBooleanHeaders",
            "RestJsonInputAndOutputWithTimestampHeaders",
            "RestJsonInputAndOutputWithIntEnumHeaders",
            // "Unexpected Content-Type ''"
            "RestJsonIgnoreQueryParamsInResponseNoPayload"
        }
    )
    public void responseTest(Runnable test) {
        test.run();
    }
}
