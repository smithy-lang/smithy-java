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
import software.amazon.smithy.model.node.ObjectNode;

@ProtocolTest(
        service = "aws.protocoltests.json#JsonProtocol",
        testType = TestType.CLIENT)
public class AwsJson11ProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
            skipTests = {
                    "SDKAppliedContentEncoding_awsJson1_1",
                    "SDKAppendsGzipAndIgnoresHttpProvidedEncoding_awsJson1_1",
            })
    public void requestTest(DataStream expected, DataStream actual) {
        Node expectedNode = ObjectNode.objectNode();
        if (expected.contentLength() != 0) {
            expectedNode = Node.parse(new String(ByteBufferUtils.getBytes(expected.asByteBuffer()),
                    StandardCharsets.UTF_8));
        }
        Node actualNode = Node.parse(new StringBuildingSubscriber(actual).getResult());
        assertEquals(expectedNode, actualNode);
    }

    @HttpClientResponseTests
    public void responseTest(Runnable test) {
        test.run();
    }
}
