/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.aws.restjson;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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
        // TODO: support content-encoding
        "aws.protocoltests.restjson#PutWithContentEncoding"
    }
)
public class RestJson1ProtocolTests {
    private static final String EMPTY_BODY = "";

    @HttpClientRequestTests
    @ProtocolTestFilter(
        skipTests = {
            // TODO: support checksums in requests
            "RestJsonHttpChecksumRequired",
            // Invalid ints, bools, etc in headers
            "RestJsonHttpWithHeadersButNoPayload",
            "RestJsonHttpWithEmptyStructurePayload",
            "MediaTypeHeaderInputBase64",
        }
    )
    public void requestTest(DataStream expected, DataStream actual) {
        assertThat(expected.hasKnownLength())
            .isTrue()
            .isSameAs(actual.hasKnownLength());

        var actualStr = new StringBuildingSubscriber(actual).getResult();
        if (expected.contentLength() != 0) {
            var expectedStr = new String(
                ByteBufferUtils.getBytes(expected.waitForByteBuffer()),
                StandardCharsets.UTF_8
            );
            if ("application/json".equals(expected.contentType())) {
                var expectedNode = Node.parse(expectedStr);
                var actualNode = Node.parse(actualStr);
                assertEquals(expectedNode, actualNode);
            } else {
                assertEquals(expectedStr, actualStr);
            }
        } else {
            assertEquals(EMPTY_BODY, actualStr);
        }
    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
        skipTests = {
            // Invalid ints, bools, etc in headers
            "RestJsonInputAndOutputWithNumericHeaders",
            "RestJsonInputAndOutputWithBooleanHeaders",
            "RestJsonInputAndOutputWithTimestampHeaders",
            "RestJsonInputAndOutputWithIntEnumHeaders",
        }
    )
    public void responseTest(Runnable test) {
        test.run();
    }
}
