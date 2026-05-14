/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.aws.restjson;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.EventStreamClientTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.StringBuildingSubscriber;
import software.amazon.smithy.java.protocoltests.harness.TestType;
import software.amazon.smithy.model.node.Node;

@ProtocolTest(
        service = "aws.protocoltests.restjson#RestJson",
        testType = TestType.CLIENT)
@ProtocolTestFilter(
        skipOperations = {
                // We dont ignore defaults on input shapes
                "aws.protocoltests.restjson#OperationWithDefaults",
        },
        skipTests = {
                // Need to add exception type to header
                "ClientErrorInput",
                "DuplexClientErrorInput",
                // Currently we are using JSON codec for plain text payload, need to correct it.
                "StringPayloadOutput",
                "DuplexStringPayloadOutput",
                // eventstream:1.0.1 made ByteValue.encodeValue() and
                // ShortValue.encodeValue() no-ops, producing malformed frames.
                "ByteHeaderInput",
                "DuplexByteHeaderInput",
                "ShortHeaderInput",
                "DuplexShortHeaderInput",
                // Blob test params use inconsistent encoding conventions —
                // headers use base64, payloads use raw strings.
                "BlobPayloadInput",
                "DuplexBlobPayloadInput",
                "BlobPayloadOutput",
                "DuplexBlobPayloadOutput",
                "BlobHeaderInput",
                "DuplexBlobHeaderInput",
                "BlobHeaderOutput",
                "DuplexBlobHeaderOutput",
                "MultipleHeaderInput",
                "DuplexMultipleHeaderInput",
                "MultipleHeaderOutput",
                "DuplexMultipleHeaderOutput",
                // Decoder returns modeled error events instead of throwing
                "ClientErrorOutput",
                "DuplexClientErrorOutput",
                // Client doesn't validate missing @required initial response members
                "MissingRequiredInitialResponseOutput",
                "DuplexMissingRequiredInitialResponseOutput"
        })
public class RestJson1ProtocolTests {
    private static final String EMPTY_BODY = "";

    @HttpClientRequestTests
    public void requestTest(DataStream expected, DataStream actual) {
        assertThat(expected.hasKnownLength())
                .isTrue()
                .isSameAs(actual.hasKnownLength());

        var actualStr = new StringBuildingSubscriber(actual).getResult();
        if (expected.contentLength() != 0) {
            var expectedStr = new String(
                    ByteBufferUtils.getBytes(expected.asByteBuffer()),
                    StandardCharsets.UTF_8);
            if ("application/json".equals(expected.contentType())) {
                var expectedNode = Node.parse(expectedStr);
                var actualNode = Node.parse(actualStr);
                Node.assertEquals(actualNode, expectedNode);
            } else {
                assertEquals(expectedStr, actualStr);
            }
        } else if (expected.contentType() != null) { // Skip request compression tests since they do not have expected body
            assertEquals(EMPTY_BODY, actualStr);
        }
    }

    @HttpClientResponseTests
    public void responseTest(Runnable test) {
        test.run();
    }

    @EventStreamClientTests
    public void eventStreamClientTest(Runnable test) {
        test.run();
    }
}
