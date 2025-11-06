/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.aws.jsonprotocols;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.protocoltests.harness.*;
import software.amazon.smithy.model.node.Node;

@ProtocolTest(
        service = "aws.protocoltests.json10#JsonRpc10",
        testType = TestType.CLIENT)
public class AwsJson1ProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
            skipTests = {
                    // TODO: implement content-encoding
                    "SDKAppliedContentEncoding_awsJson1_0",
                    "SDKAppendsGzipAndIgnoresHttpProvidedEncoding_awsJson1_0",

                    // Skipping top-level input defaults isn't necessary in Smithy-Java given it uses builders and
                    // the defaults don't impact nullability. This applies to the following tests.
                    "AwsJson10ClientSkipsTopLevelDefaultValuesInInput",
                    "AwsJson10ClientPopulatesDefaultValuesInInput",
                    "AwsJson10ClientUsesExplicitlyProvidedMemberValuesOverDefaults",

                    // Like above, but in smithy-java we populate the defaults but don't change the nullability.
                    "AwsJson10ClientIgnoresNonTopLevelDefaultsOnMembersWithClientOptional",
            },
            skipOperations = "aws.protocoltests.json10#OperationWithRequiredMembersWithDefaults")
    public void requestTest(DataStream expected, DataStream actual) {
        String expectedJson = "{}";
        if (expected.contentLength() != 0) {
            // Use the node parser to strip out white space.
            expectedJson = Node.printJson(
                    Node.parse(new String(ByteBufferUtils.getBytes(expected.asByteBuffer()),
                            StandardCharsets.UTF_8)));
        }
        assertEquals(expectedJson, new StringBuildingSubscriber(actual).getResult());

    }

    @HttpClientResponseTests
    @ProtocolTestFilter(
            skipTests = {
                    "AwsJson10ClientPopulatesDefaultsValuesWhenMissingInResponse",
                    "AwsJson10ClientIgnoresDefaultValuesIfMemberValuesArePresentInResponse",
                    //The below fail because we haven't implemented code based exception handling
                    "AwsJson10FooErrorUsingCode",
                    "AwsJson10FooErrorUsingCodeAndNamespace",
                    "AwsJson10FooErrorUsingCodeUriAndNamespace",
                    "AwsJson10FooErrorWithDunderTypeUriAndNamespace"

            },
            skipOperations = "aws.protocoltests.json10#OperationWithRequiredMembersWithDefaults")
    public void responseTest(Runnable test) {
        test.run();
    }
}
